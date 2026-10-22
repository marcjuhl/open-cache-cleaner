package com.opencachecleaner.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opencachecleaner.app.data.model.CleaningState
import com.opencachecleaner.app.data.repository.PermissionHelper
import com.opencachecleaner.app.ui.screens.AppListScreen
import com.opencachecleaner.app.ui.screens.CleaningScreen
import com.opencachecleaner.app.ui.screens.OnboardingScreen
import com.opencachecleaner.app.ui.theme.OpenCacheCleanerTheme
import com.opencachecleaner.app.ui.viewmodel.CacheCleanerViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: CacheCleanerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            OpenCacheCleanerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                    var forceShowSettings by remember { mutableStateOf(false) }

                    val shouldShowOnboarding = forceShowSettings || !uiState.areCorePermissionsGranted

                    when {
                        // Priority 1: Active Cleaning Overlay/Screen
                        uiState.cleaningState !is CleaningState.Idle -> {
                            CleaningScreen(
                                state = uiState.cleaningState,
                                onPause = { viewModel.pauseCleaning() },
                                onResume = { viewModel.resumeCleaning() },
                                onStop = { viewModel.stopCleaning() },
                                onDismiss = { viewModel.resetCleaningState() }
                            )
                        }

                        // Priority 2: Permission Onboarding Wizard
                        shouldShowOnboarding -> {
                            OnboardingScreen(
                                isAccessibilityGranted = uiState.isAccessibilityGranted,
                                isUsageAccessGranted = uiState.isUsageAccessGranted,
                                isRootAvailable = uiState.isRootAvailable,
                                isShizukuAvailable = uiState.isShizukuAvailable,
                                selectedMode = uiState.selectedMode,
                                onGrantAccessibility = {
                                    PermissionHelper.openAccessibilitySettings(this@MainActivity)
                                },
                                onGrantUsageAccess = {
                                    PermissionHelper.openUsageAccessSettings(this@MainActivity)
                                },
                                onSelectMode = { mode ->
                                    viewModel.setCleaningMode(mode)
                                },
                                onProceed = {
                                    forceShowSettings = false
                                }
                            )
                        }

                        // Priority 3: Main App List & Cleaner Dashboard
                        else -> {
                            AppListScreen(
                                apps = uiState.filteredApps,
                                searchQuery = uiState.searchQuery,
                                totalSelectedCache = uiState.formattedTotalSelectedCache,
                                selectedCount = uiState.selectedApps.size,
                                selectedMode = uiState.selectedMode,
                                isLoading = uiState.isLoading,
                                includeSystemApps = uiState.includeSystemApps,
                                onSearchQueryChange = { viewModel.setSearchQuery(it) },
                                onToggleAppSelection = { viewModel.toggleAppSelection(it) },
                                onSelectAll = { viewModel.selectAll(it) },
                                onToggleSystemApps = { viewModel.setIncludeSystemApps(it) },
                                onRefresh = { viewModel.loadInstalledApps() },
                                onOpenSettings = { forceShowSettings = true },
                                onStartCleaning = { viewModel.startCleaning() }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check permissions when returning from system Settings pages
        viewModel.checkPermissions()
    }
}
