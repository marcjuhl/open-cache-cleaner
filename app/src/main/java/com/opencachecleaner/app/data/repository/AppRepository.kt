package com.opencachecleaner.app.data.repository

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager
import com.opencachecleaner.app.data.model.AppCacheInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppRepository(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
    private val storageStatsManager: StorageStatsManager? =
        context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager

    /**
     * Scans installed applications and retrieves their cache sizes.
     * @param includeSystemApps Whether to include pre-installed system apps.
     */
    suspend fun getInstalledAppsWithCache(includeSystemApps: Boolean = false): List<AppCacheInfo> =
        withContext(Dispatchers.IO) {
            val flags = PackageManager.GET_META_DATA
            val installedApps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(flags.toLong()))
            } else {
                packageManager.getInstalledApplications(flags)
            }

            val userHandle = Process.myUserHandle()
            val hasUsageStats = PermissionHelper.isUsageAccessGranted(context)

            installedApps
                .filter { appInfo ->
                    // Exclude our own application from cache clearing
                    appInfo.packageName != context.packageName
                }
                .filter { appInfo ->
                    val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    includeSystemApps || !isSystem
                }
                .map { appInfo ->
                    var cacheBytes = 0L
                    if (hasUsageStats && storageStatsManager != null) {
                        try {
                            val storageUuid = appInfo.storageUuid ?: StorageManager.UUID_DEFAULT
                            val stats = storageStatsManager.queryStatsForPackage(
                                storageUuid,
                                appInfo.packageName,
                                userHandle
                            )
                            cacheBytes = stats.cacheBytes
                        } catch (_: Exception) {
                            // Fallback if permission not granted or package inaccessible
                            cacheBytes = 0L
                        }
                    }

                    val appName = try {
                        packageManager.getApplicationLabel(appInfo).toString()
                    } catch (_: Exception) {
                        appInfo.packageName
                    }

                    val icon = try {
                        packageManager.getApplicationIcon(appInfo)
                    } catch (_: Exception) {
                        null
                    }

                    val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                    AppCacheInfo(
                        packageName = appInfo.packageName,
                        appName = appName,
                        icon = icon,
                        cacheSizeBytes = cacheBytes,
                        isSystemApp = isSystem,
                        isSelected = cacheBytes > 0 // Pre-select apps that have cache to clean
                    )
                }
                .sortedByDescending { it.cacheSizeBytes }
        }
}
