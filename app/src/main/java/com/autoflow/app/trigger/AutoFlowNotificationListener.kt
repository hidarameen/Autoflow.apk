package com.autoflow.app.trigger

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.autoflow.app.engine.RuleEngine
import com.autoflow.app.engine.TriggerEvent
import com.autoflow.app.util.AppInfo
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Primary trigger source. Notifications carry the sender and the message body for
 * virtually every messaging app, which is why a Telegram-to-anything rule does not need to
 * read Telegram's UI at all.
 */
class AutoFlowNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected.value = true
    }

    override fun onListenerDisconnected() {
        connected.value = false
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        if (sbn.packageName == applicationContext.packageName) return

        // Ongoing notifications are progress bars and media controls, not messages.
        val isOngoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0
        if (isOngoing) return

        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: return

        RuleEngine.submit(
            TriggerEvent(
                source = TriggerEvent.Source.NOTIFICATION,
                packageName = sbn.packageName,
                appLabel = AppInfo.label(applicationContext, sbn.packageName),
                title = title,
                text = text,
                timestamp = sbn.postTime,
            )
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val packageName = sbn?.packageName ?: return
        if (packageName == applicationContext.packageName) return

        val extras = sbn.notification?.extras
        RuleEngine.submit(
            TriggerEvent(
                source = TriggerEvent.Source.NOTIFICATION_REMOVED,
                packageName = packageName,
                appLabel = AppInfo.label(applicationContext, packageName),
                title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
                text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
            )
        )
    }

    companion object {
        /** Observed by the UI so the setup card can show live status. */
        val connected = MutableStateFlow(false)
    }
}
