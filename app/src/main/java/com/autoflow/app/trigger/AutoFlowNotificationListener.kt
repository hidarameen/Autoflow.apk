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

        // Keep the reply action so a rule can answer through the notification itself.
        NotificationReplyStore.remember(sbn)

        val extras = notification.extras

        // WhatsApp, Messenger and Telegram post MessagingStyle notifications, where the
        // body lives in EXTRA_MESSAGES rather than EXTRA_TEXT. Reading only EXTRA_TEXT
        // silently dropped every real chat message.
        val latest = latestStyledMessage(extras)

        val title = latest?.first?.takeIf { it.isNotBlank() }
            ?: extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()

        val text = latest?.second
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: return

        // A group summary ("530 messages from 12 chats") names no sender and carries no
        // message body, so no rule can usefully match it. Record it so the UI can explain
        // why a per-sender rule is not firing instead of failing silently.
        val isSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
        if (isSummary) {
            CollapsedNotice.report(sbn.packageName)
            return
        }
        CollapsedNotice.clear(sbn.packageName)

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
        NotificationReplyStore.forget(sbn.key)
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

    /**
     * Pulls the newest (sender, body) pair out of a MessagingStyle notification.
     *
     * The array holds one Bundle per message with "sender" and "text" keys; the last entry
     * is the message that just arrived.
     */
    private fun latestStyledMessage(extras: android.os.Bundle): Pair<String, String>? {
        val raw = extras.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return null
        val last = raw.filterIsInstance<android.os.Bundle>().lastOrNull() ?: return null

        val body = last.getCharSequence("text")?.toString().orEmpty()
        if (body.isBlank()) return null

        val sender = last.getCharSequence("sender")?.toString()
            ?: (last.getParcelable("sender_person") as? android.app.Person)?.name?.toString()
            ?: ""
        return sender to body
    }

    companion object {
        /** Observed by the UI so the setup card can show live status. */
        val connected = MutableStateFlow(false)
    }
}
