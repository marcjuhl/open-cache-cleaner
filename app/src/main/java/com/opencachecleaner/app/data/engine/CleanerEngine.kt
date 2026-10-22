package com.opencachecleaner.app.data.engine

import com.opencachecleaner.app.data.model.AppCacheInfo
import com.opencachecleaner.app.data.model.CleaningState
import kotlinx.coroutines.flow.StateFlow

interface CleanerEngine {
    val state: StateFlow<CleaningState>
    suspend fun startCleaning(targets: List<AppCacheInfo>)
    fun pause()
    fun resume()
    fun stop()
}
