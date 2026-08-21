package com.autoflow.app.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import com.autoflow.app.trigger.AutoFlowAccessibilityService
import com.autoflow.app.trigger.AutoFlowNotificationListener

/**
 * The three switches AutoFlow cannot turn on for itself. Each check reads the live system
 * setting rather than a cached flag, because the user can revoke any of them at any time.
 */
object Permissions {

    fun isAccessibilityEnabled(context: Context): Boolean {
        val expected = ComponentName(context, AutoFlowAccessibilityService::class.java)
            .flattenToString()

        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()

        val splitter = TextUtils.SimpleStringSplitter(':').apply { setString(enabled) }
        while (splitter.hasNext()) {
            val entry = splitter.next()
            if (entry.equals(expected, ignoreCase = true)) return true
            // Some OEMs store the short form "pkg/.ClassName".
            if (entry.equals(shortForm(context), ignoreCase = true)) return true
        }
        return false
    }

    private fun shortForm(context: Context): String =
        ComponentName(context, AutoFlowAccessibilityService::class.java).flattenToShortString()

    fun isNotificationAccessEnabled(context: Context): Boolean {
        val listeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ).orEmpty()
        val expected = ComponentName(context, AutoFlowNotificationListener::class.java)
        return listeners.split(':').any {
            ComponentName.unflattenFromString(it) == expected
        }
    }

    fun isBatteryOptimisationIgnored(context: Context): Boolean {
        val power = context.getSystemService(PowerManager::class.java) ?: return false
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun openAccessibilitySettings(context: Context) =
        open(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    fun openNotificationAccessSettings(context: Context) =
        open(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))

    fun openBatterySettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        )
        open(context, intent)
    }

    private fun open(context: Context, intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
