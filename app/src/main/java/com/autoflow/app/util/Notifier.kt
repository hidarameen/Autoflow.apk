package com.autoflow.app.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.R as AndroidR

/** The app's own notifications: the foreground-service badge and the Notify action. */
object Notifier {

    const val SERVICE_CHANNEL_ID = "autoflow_service"
    const val ALERT_CHANNEL_ID = "autoflow_alerts"
    const val SERVICE_NOTIFICATION_ID = 1001

    private var nextAlertId = 2000

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                SERVICE_CHANNEL_ID,
                "Automation engine",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Shown while AutoFlow is watching for triggers." }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                ALERT_CHANNEL_ID,
                "Rule alerts",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Messages posted by your rules." }
        )
    }

    fun serviceNotification(context: Context, activeRules: Int): Notification =
        NotificationCompat.Builder(context, SERVICE_CHANNEL_ID)
            .setContentTitle("AutoFlow is running")
            .setContentText(
                if (activeRules == 1) "1 rule armed" else "$activeRules rules armed"
            )
            .setSmallIcon(AndroidR.drawable.ic_menu_manage)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    fun post(context: Context, title: String, message: String) {
        if (!canPost(context)) return
        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(AndroidR.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(nextAlertId++, notification)
    }

    /** POST_NOTIFICATIONS only became a runtime permission in API 33; below that it is implicit. */
    private fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}
