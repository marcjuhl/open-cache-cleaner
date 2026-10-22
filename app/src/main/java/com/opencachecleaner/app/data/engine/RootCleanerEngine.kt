package com.opencachecleaner.app.data.engine

import com.opencachecleaner.app.data.model.AppCacheInfo
import com.opencachecleaner.app.data.model.CleaningState
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RootCleanerEngine : CleanerEngine {

    private val engineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
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

        if (!Shell.getShell().isRoot) {
            _state.value = CleaningState.Error("Root shell permission was denied or not available.")
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
            runRootCleaningLoop()
        }
    }

    private suspend fun runRootCleaningLoop() {
        for (i in targetList.indices) {
            while (isPaused && !isCancelled) {
                delay(200)
            }
            if (isCancelled) break

            currentIndex = i
            val app = targetList[i]

            _state.value = CleaningState.Running(
                currentPackage = app.packageName,
                currentAppName = app.appName,
                currentIndex = i + 1,
                totalApps = targetList.size,
                freedBytes = accumulatedFreedBytes,
                statusMessage = "Purging cache directory for ${app.appName}..."
            )

            // Execute root commands directly to purge cache
            val pkg = app.packageName
            val commands = arrayOf(
                "rm -rf /data/data/$pkg/cache/*",
                "rm -rf /data/data/$pkg/code_cache/*",
                "rm -rf /data/user/0/$pkg/cache/*",
                "rm -rf /data/user/0/$pkg/code_cache/*",
                "rm -rf /sdcard/Android/data/$pkg/cache/*"
            )

            val result = Shell.cmd(*commands).exec()
            if (result.isSuccess) {
                accumulatedFreedBytes += app.cacheSizeBytes
            }

            // Yield slightly to prevent blocking CPU
            delay(50)
        }

        // Also trigger pm trim-caches as a cleanup pass
        Shell.cmd("pm trim-caches 4096G").exec()

        val duration = System.currentTimeMillis() - startTimeMillis
        _state.value = CleaningState.Completed(
            totalCleaned = currentIndex + 1,
            totalFreedBytes = accumulatedFreedBytes,
            durationMillis = duration
        )
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
        val duration = System.currentTimeMillis() - startTimeMillis
        _state.value = CleaningState.Completed(
            totalCleaned = currentIndex,
            totalFreedBytes = accumulatedFreedBytes,
            durationMillis = duration
        )
    }
}
