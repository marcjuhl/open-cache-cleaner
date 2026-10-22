package com.opencachecleaner.app.data.model

sealed interface CleaningState {
    data object Idle : CleaningState

    data class Running(
        val currentPackage: String,
        val currentAppName: String,
        val currentIndex: Int,
        val totalApps: Int,
        val freedBytes: Long,
        val statusMessage: String = "Cleaning in progress..."
    ) : CleaningState {
        val progress: Float
            get() = if (totalApps > 0) currentIndex.toFloat() / totalApps.toFloat() else 0f
    }

    data class Paused(
        val currentPackage: String,
        val currentAppName: String,
        val currentIndex: Int,
        val totalApps: Int,
        val freedBytes: Long
    ) : CleaningState

    data class Completed(
        val totalCleaned: Int,
        val totalFreedBytes: Long,
        val durationMillis: Long
    ) : CleaningState

    data class Error(
        val errorMessage: String,
        val canRetry: Boolean = true
    ) : CleaningState
}
