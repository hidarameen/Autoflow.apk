package com.autoflow.app.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class InstalledApp(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
)

/** Package-name to human-label lookups, memoised because notifications arrive in bursts. */
object AppInfo {

    private val labelCache = mutableMapOf<String, String>()
    private val iconCache = mutableMapOf<String, ImageBitmap?>()

    fun label(context: Context, packageName: String): String =
        labelCache.getOrPut(packageName) {
            runCatching {
                val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
            }.getOrDefault(packageName)
        }

    fun appIcon(context: Context, packageName: String): ImageBitmap? {
        if (packageName.isBlank()) return null
        return iconCache.getOrPut(packageName) {
            runCatching {
                val pm = context.packageManager
                val drawable = pm.getApplicationIcon(packageName)
                drawable.toBitmap(width = 96, height = 96).asImageBitmap()
            }.getOrNull()
        }
    }

    /** Apps the user could plausibly automate: anything with a launcher entry. */
    fun launchableApps(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .map { info ->
                InstalledApp(
                    packageName = info.packageName,
                    label = runCatching { pm.getApplicationLabel(info).toString() }
                        .getOrDefault(info.packageName),
                    isSystem = info.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    fun isInstalled(context: Context, packageName: String): Boolean =
        context.packageManager.getLaunchIntentForPackage(packageName) != null
}

/**
 * Package names the templates target. Kept in one place because these change between app
 * versions and regions, and a wrong one makes a whole template silently fail.
 */
object KnownPackages {
    const val TELEGRAM = "org.telegram.messenger"
    const val TELEGRAM_WEB = "org.telegram.messenger.web"
    const val WHATSAPP = "com.whatsapp"
    const val WHATSAPP_BUSINESS = "com.whatsapp.w4b"
    const val X = "com.twitter.android"
    const val INSTAGRAM = "com.instagram.android"
    const val FACEBOOK = "com.facebook.katana"
    const val MESSENGER = "com.facebook.orca"
    const val YOUTUBE = "com.google.android.youtube"
    const val CHROME = "com.android.chrome"
    const val GMAIL = "com.google.android.gm"
    const val DISCORD = "com.discord"
    const val TIKTOK = "com.zhiliaoapp.musically"
    const val SIGNAL = "org.thoughtcrime.securesms"
}
