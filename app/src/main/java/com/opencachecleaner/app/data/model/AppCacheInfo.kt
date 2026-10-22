package com.opencachecleaner.app.data.model

import android.graphics.drawable.Drawable

/**
 * Represents an installed application with its detected cache footprint.
 */
data class AppCacheInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val cacheSizeBytes: Long,
    val isSystemApp: Boolean = false,
    val isSelected: Boolean = true
) {
    val formattedCacheSize: String
        get() = formatBytes(cacheSizeBytes)

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
            val clampedGroup = digitGroups.coerceIn(0, units.size - 1)
            val value = bytes / Math.pow(1024.0, clampedGroup.toDouble())
            return String.format("%.2f %s", value, units[clampedGroup])
        }
    }
}
