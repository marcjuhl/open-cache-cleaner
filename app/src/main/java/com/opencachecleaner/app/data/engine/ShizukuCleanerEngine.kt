package com.opencachecleaner.app.data.engine

import android.content.pm.PackageManager
import com.opencachecleaner.app.data.model.AppCacheInfo
import com.opencachecleaner.app.data.model.CleaningState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

class ShizukuCleanerEngine : CleanerEngine {

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

        if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            _state.value = CleaningState.Error("Shizuku is not running or permission is not granted.")
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
            runShizukuCleaning()
        }
    }

    private suspend fun runShizukuCleaning() {
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
                statusMessage = "Requesting cache trim for ${app.appName} via Shizuku..."
            )

            // Execute package trim via Shizuku process
            executeShizukuCommand(arrayOf("pm", "trim-caches", "4096G"))

            accumulatedFreedBytes += app.cacheSizeBytes
            delay(100)
        }

        val duration = System.currentTimeMillis() - startTimeMillis
        _state.value = CleaningState.Completed(
            totalCleaned = currentIndex + 1,
            totalFreedBytes = accumulatedFreedBytes,
            durationMillis = duration
        )
    }

    private fun executeShizukuCommand(command: Array<String>): Boolean {
        return try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }
            val process = method.invoke(null, command, null, null) as java.lang.Process
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            reader.readLines()
            process.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }

    override fun pause() {
        isPaused = true
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
