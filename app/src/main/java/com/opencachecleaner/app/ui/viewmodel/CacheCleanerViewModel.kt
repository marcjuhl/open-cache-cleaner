package com.opencachecleaner.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.opencachecleaner.app.data.engine.AccessibilityCleanerEngine
import com.opencachecleaner.app.data.engine.CleanerEngine
import com.opencachecleaner.app.data.engine.RootCleanerEngine
import com.opencachecleaner.app.data.engine.ShizukuCleanerEngine
import com.opencachecleaner.app.data.model.AppCacheInfo
import com.opencachecleaner.app.data.model.CleaningMode
import com.opencachecleaner.app.data.model.CleaningState
import com.opencachecleaner.app.data.repository.AppRepository
import com.opencachecleaner.app.data.repository.PermissionHelper
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class CleanerUiState(
    val isLoading: Boolean = false,
    val apps: List<AppCacheInfo> = emptyList(),
    val searchQuery: String = "",
    val includeSystemApps: Boolean = false,
    val selectedMode: CleaningMode = CleaningMode.ACCESSIBILITY,
    val isAccessibilityGranted: Boolean = false,
    val isUsageAccessGranted: Boolean = false,
    val isRootAvailable: Boolean = false,
    val isShizukuAvailable: Boolean = false,
    val cleaningState: CleaningState = CleaningState.Idle
) {
    val filteredApps: List<AppCacheInfo>
        get() = apps.filter {
            it.appName.contains(searchQuery, ignoreCase = true) ||
            it.packageName.contains(searchQuery, ignoreCase = true)
        }

    val selectedApps: List<AppCacheInfo>
        get() = apps.filter { it.isSelected }

    val totalSelectedCacheBytes: Long
        get() = selectedApps.sumOf { it.cacheSizeBytes }

    val formattedTotalSelectedCache: String
        get() = AppCacheInfo.formatBytes(totalSelectedCacheBytes)

    val areCorePermissionsGranted: Boolean
        get() = when (selectedMode) {
            CleaningMode.ACCESSIBILITY -> isAccessibilityGranted && isUsageAccessGranted
            CleaningMode.ROOT -> isRootAvailable && isUsageAccessGranted
            CleaningMode.SHIZUKU -> isShizukuAvailable && isUsageAccessGranted
        }
}

class CacheCleanerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application)
    private val accessibilityEngine = AccessibilityCleanerEngine(application)
    private val rootEngine = RootCleanerEngine()
    private val shizukuEngine = ShizukuCleanerEngine()

    private val _uiState = MutableStateFlow(CleanerUiState())
    val uiState: StateFlow<CleanerUiState> = _uiState.asStateFlow()

    private val currentEngine: CleanerEngine
        get() = when (_uiState.value.selectedMode) {
            CleaningMode.ACCESSIBILITY -> accessibilityEngine
            CleaningMode.ROOT -> rootEngine
            CleaningMode.SHIZUKU -> shizukuEngine
        }

    init {
        checkPermissions()
        loadInstalledApps()

        // Observe active engine state updates
        viewModelScope.launch {
            merge(accessibilityEngine.state, rootEngine.state, shizukuEngine.state)
                .collect { engineState ->
                    _uiState.update { it.copy(cleaningState = engineState) }
                    // Reload apps once cleaning finishes
                    if (engineState is CleaningState.Completed) {
                        loadInstalledApps()
                    }
                }
        }
    }

    fun checkPermissions() {
        val context = getApplication<Application>()
        _uiState.update {
            it.copy(
                isAccessibilityGranted = PermissionHelper.isAccessibilityServiceEnabled(context),
                isUsageAccessGranted = PermissionHelper.isUsageAccessGranted(context),
                isRootAvailable = PermissionHelper.isRootAvailable(),
                isShizukuAvailable = PermissionHelper.isShizukuAvailable(context)
            )
        }
    }

    fun loadInstalledApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val apps = repository.getInstalledAppsWithCache(_uiState.value.includeSystemApps)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    apps = apps
                )
            }
        }
    }

    fun toggleAppSelection(packageName: String) {
        _uiState.update { state ->
            val updated = state.apps.map {
                if (it.packageName == packageName) it.copy(isSelected = !it.isSelected) else it
            }
            state.copy(apps = updated)
        }
    }

    fun selectAll(select: Boolean) {
        _uiState.update { state ->
            val updated = state.apps.map { it.copy(isSelected = select) }
            state.copy(apps = updated)
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setIncludeSystemApps(include: Boolean) {
        _uiState.update { it.copy(includeSystemApps = include) }
        loadInstalledApps()
    }

    fun setCleaningMode(mode: CleaningMode) {
        _uiState.update { it.copy(selectedMode = mode) }
    }

    fun startCleaning() {
        val targets = _uiState.value.selectedApps
        if (targets.isEmpty()) return

        viewModelScope.launch {
            currentEngine.startCleaning(targets)
        }
    }

    fun pauseCleaning() {
        currentEngine.pause()
    }

    fun resumeCleaning() {
        currentEngine.resume()
    }

    fun stopCleaning() {
        currentEngine.stop()
    }

    fun resetCleaningState() {
        _uiState.update { it.copy(cleaningState = CleaningState.Idle) }
    }
}
