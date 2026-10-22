package com.opencachecleaner.app.data.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.opencachecleaner.app.data.model.AppCacheInfo
import com.opencachecleaner.app.data.model.CleaningState
import com.opencachecleaner.app.service.CacheCleanerAccessibilityService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.resume

class AccessibilityCleanerEngine(
    private val context: Context
) : CleanerEngine {

    private val engineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var job: Job? = null

    private val _state = MutableStateFlow<CleaningState>(CleaningState.Idle)
    override val state: StateFlow<CleaningState> = _state.asStateFlow()

    private var targetList: List<AppCacheInfo> = emptyList()
    private var currentIndex = 0
    private var accumulatedFreedBytes = 0L
    private var startTimeMillis = 0L

    @Volatile
    private var isPaused = false

    @Volatile
    private var isCancelled = false

    override suspend fun startCleaning(targets: List<AppCacheInfo>) {
        if (targets.isEmpty()) {
            _state.value = CleaningState.Completed(0, 0L, 0L)
            return
        }

        if (CacheCleanerAccessibilityService.instance == null) {
            _state.value = CleaningState.Error("Accessibility Service is not enabled or active.")
            return
        }

        targetList = targets
        currentIndex = 0
        accumulatedFreedBytes = 0L
        startTimeMillis = System.currentTimeMillis()
        isPaused = false
        isCancelled = false

        job?.cancel()
        job = engineScope.launch {
            runCleaningLoop()
        }
    }

    private suspend fun runCleaningLoop() {
        CacheCleanerAccessibilityService.isRunning = true

        while (currentIndex < targetList.size && !isCancelled) {
            while (isPaused && !isCancelled) {
                delay(200)
            }

            if (isCancelled) break

            val currentApp = targetList[currentIndex]
            _state.value = CleaningState.Running(
                currentPackage = currentApp.packageName,
                currentAppName = currentApp.appName,
                currentIndex = currentIndex + 1,
                totalApps = targetList.size,
                freedBytes = accumulatedFreedBytes,
                statusMessage = "Opening ${currentApp.appName} settings..."
            )

            // Reset service step
            CacheCleanerAccessibilityService.currentStep = CacheCleanerAccessibilityService.Step.WAIT_APP_INFO

            // Open app settings page
            openAppDetails(currentApp.packageName)

            // Wait for completion callback or timeout watchdog (3.5 seconds)
            val success = withTimeoutOrNull(3500L) {
                suspendCancellableCoroutine { continuation ->
                    CacheCleanerAccessibilityService.onAppFinishedCallback = { cleared ->
                        if (continuation.isActive) {
                            continuation.resume(cleared)
                        }
                    }
                }
            } ?: false

            if (success) {
                accumulatedFreedBytes += currentApp.cacheSizeBytes
            }

            currentIndex++
            delay(150) // Small breather between apps
        }

        CacheCleanerAccessibilityService.stopAutomation()

        if (!isCancelled) {
            val duration = System.currentTimeMillis() - startTimeMillis
            _state.value = CleaningState.Completed(
                totalCleaned = currentIndex,
                totalFreedBytes = accumulatedFreedBytes,
                durationMillis = duration
            )
            bringAppToForeground()
        }
    }

    override fun pause() {
        isPaused = true
        val currentApp = targetList.getOrNull(currentIndex)
        if (currentApp != null) {
            _state.value = CleaningState.Paused(
                currentPackage = currentApp.packageName,
                currentAppName = currentApp.appName,
                currentIndex = currentIndex + 1,
                totalApps = targetList.size,
                freedBytes = accumulatedFreedBytes
            )
        }
    }

    override fun resume() {
        isPaused = false
    }

    override fun stop() {
        isCancelled = true
        isPaused = false
        job?.cancel()
        CacheCleanerAccessibilityService.stopAutomation()
        val duration = System.currentTimeMillis() - startTimeMillis
        _state.value = CleaningState.Completed(
            totalCleaned = currentIndex,
            totalFreedBytes = accumulatedFreedBytes,
            durationMillis = duration
        )
        bringAppToForeground()
    }

    private fun openAppDetails(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        context.startActivity(intent)
    }

    private fun bringAppToForeground() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        if (intent != null) {
            context.startActivity(intent)
        }
    }
}
