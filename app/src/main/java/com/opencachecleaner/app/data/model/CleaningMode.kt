package com.opencachecleaner.app.data.model

/**
 * Supported execution backends for cleaning app cache.
 */
enum class CleaningMode(val displayName: String, val description: String) {
    ACCESSIBILITY(
        displayName = "Accessibility Service (Non-Root)",
        description = "Automates opening App Info and pressing 'Clear Cache' via standard UI. Safe and requires no root."
    ),
    SHIZUKU(
        displayName = "Shizuku Service (ADB Privileged)",
        description = "Invokes system package manager commands in the background via Shizuku without opening UI."
    ),
    ROOT(
        displayName = "Root Shell (SuperUser)",
        description = "Executes direct shell commands (pm trim-caches & cache purging) in the background with full root privileges."
    )
}
