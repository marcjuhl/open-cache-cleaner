package com.opencachecleaner.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.opencachecleaner.app.data.model.AppCacheInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class CacheCleanerAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var automationJob: Job? = null

    enum class Step {
        WAIT_APP_INFO,
        WAIT_STORAGE_PAGE,
        WAIT_CLEARED,
        DONE
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceBound.value = true
        Log.d(TAG, "CacheCleanerAccessibilityService connected.")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        _isServiceBound.value = false
        automationJob?.cancel()
        Log.d(TAG, "CacheCleanerAccessibilityService unbound.")
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isRunning || event == null) return
        processCurrentWindow()
    }

    override fun onInterrupt() {
        Log.w(TAG, "AccessibilityService interrupted.")
        stopAutomation()
    }

    /**
     * Inspects active window hierarchy and executes the click sequence safely.
     */
    @Synchronized
    fun processCurrentWindow() {
        val rootNode = rootInActiveWindow ?: return

        when (currentStep) {
            Step.WAIT_APP_INFO -> {
                // Check if we are directly on the storage page first
                val clearCacheNode = findClearCacheNode(rootNode)
                if (clearCacheNode != null) {
                    currentStep = Step.WAIT_STORAGE_PAGE
                    executeClearCache(clearCacheNode)
                    return
                }

                // Otherwise, search for "Storage" or "Storage & cache" row
                val storageEntryNode = findStorageEntryNode(rootNode)
                if (storageEntryNode != null) {
                    Log.d(TAG, "Found storage entry. Clicking...")
                    clickNode(storageEntryNode)
                    currentStep = Step.WAIT_STORAGE_PAGE
                }
            }

            Step.WAIT_STORAGE_PAGE -> {
                val clearCacheNode = findClearCacheNode(rootNode)
                if (clearCacheNode != null) {
                    executeClearCache(clearCacheNode)
                }
            }

            Step.WAIT_CLEARED, Step.DONE -> {
                // Waiting for next app transition
            }
        }
    }

    private fun executeClearCache(clearCacheNode: AccessibilityNodeInfo) {
        if (!clearCacheNode.isEnabled) {
            Log.d(TAG, "Clear cache button is disabled (cache already 0). Moving next.")
            onAppCleanedSuccess()
            return
        }

        Log.d(TAG, "Clicking 'Clear Cache' button...")
        clickNode(clearCacheNode)
        currentStep = Step.WAIT_CLEARED

        // Allow UI brief debounce to complete cache zeroing before moving to next app
        serviceScope.launch {
            delay(350)
            onAppCleanedSuccess()
        }
    }

    private fun findStorageEntryNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val storageKeywords = listOf(
            "storage & cache",
            "storage and cache",
            "storage",
            "speicher & cache", // German
            "speicher und cache",
            "speicher",
            "stockage et cache", // French
            "stockage",
            "almacenamiento" // Spanish
        )

        for (keyword in storageKeywords) {
            val nodes = root.findAccessibilityNodeInfosByText(keyword)
            for (node in nodes) {
                val text = node.text?.toString()?.lowercase(Locale.ROOT) ?: ""
                val desc = node.contentDescription?.toString()?.lowercase(Locale.ROOT) ?: ""
                
                // Ensure it's not a button or irrelevant label
                if (text.contains("clear") || desc.contains("clear")) continue

                // Find clickable target (either node itself or clickable parent)
                val clickableNode = findClickableParent(node)
                if (clickableNode != null) {
                    return clickableNode
                }
            }
        }

        // Try standard Android Settings Resource IDs
        val knownIds = listOf(
            "com.android.settings:id/storage_settings",
            "com.android.settings:id/storage_size",
            "android:id/title"
        )
        for (resId in knownIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(resId)
            for (node in nodes) {
                val text = node.text?.toString()?.lowercase(Locale.ROOT) ?: ""
                if (storageKeywords.any { text.contains(it) }) {
                    val clickableNode = findClickableParent(node)
                    if (clickableNode != null) return clickableNode
                }
            }
        }

        return null
    }

    private fun findClearCacheNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val clearCacheKeywords = listOf(
            "clear cache",
            "cache leeren", // German
            "vider le cache", // French
            "borrar caché", // Spanish
            "limpiar caché"
        )

        // Strict resource ID match first
        val knownClearCacheIds = listOf(
            "com.android.settings:id/clear_cache_button",
            "com.android.settings:id/button2", // Frequently button2 on AOSP
            "com.android.settings:id/button_clear_cache",
            "com.miui.securitycenter:id/clear_cache"
        )

        for (id in knownClearCacheIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            for (node in nodes) {
                val text = node.text?.toString()?.lowercase(Locale.ROOT) ?: ""
                // Critical safety: NEVER click clear data / clear storage
                if (isDataWipeNode(text)) continue
                if (node.isClickable || findClickableParent(node) != null) {
                    return if (node.isClickable) node else findClickableParent(node)
                }
            }
        }

        // Text-based fallback search
        for (keyword in clearCacheKeywords) {
            val nodes = root.findAccessibilityNodeInfosByText(keyword)
            for (node in nodes) {
                val text = node.text?.toString()?.lowercase(Locale.ROOT) ?: ""
                val desc = node.contentDescription?.toString()?.lowercase(Locale.ROOT) ?: ""

                // Safety check: ensure it is specifically "cache", not "data" or "storage"
                if (isDataWipeNode(text) || isDataWipeNode(desc)) continue

                if (text.contains("cache") || desc.contains("cache")) {
                    return if (node.isClickable) node else findClickableParent(node)
                }
            }
        }

        return null
    }

    /**
     * Safety barrier: prevents accidental clicks on destructive 'Clear storage' / 'Clear data' buttons.
     */
    private fun isDataWipeNode(text: String): Boolean {
        val dangerous = listOf(
            "clear storage", "clear data", "daten löschen", "speicherinhalt löschen",
            "effacer les données", "borrar datos"
        )
        return dangerous.any { text.contains(it) }
    }

    private fun findClickableParent(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var current = node
        while (current != null) {
            if (current.isClickable) return current
            current = current.parent
        }
        return null
    }

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun onAppCleanedSuccess() {
        currentStep = Step.DONE
        onAppFinishedCallback?.invoke(true)
    }

    companion object {
        private const val TAG = "CacheAccessibility"

        var instance: CacheCleanerAccessibilityService? = null
            private set

        private val _isServiceBound = MutableStateFlow(false)
        val isServiceBound: StateFlow<Boolean> = _isServiceBound.asStateFlow()

        var isRunning: Boolean = false
        var currentStep: Step = Step.WAIT_APP_INFO
        var onAppFinishedCallback: ((success: Boolean) -> Unit)? = null

        fun stopAutomation() {
            isRunning = false
            currentStep = Step.DONE
            onAppFinishedCallback = null
        }
    }
}
