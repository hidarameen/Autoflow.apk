package com.autoflow.app.trigger

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.RemoteInput

/**
 * Keeps the reply action from recent messaging notifications so a rule can answer through
 * the notification itself.
 *
 * This is how smartwatches and Android Auto reply: the posting app attaches a
 * [RemoteInput] action, and firing it delivers text straight into the conversation. No UI
 * automation, no app switch, and it works while the screen is off and the device locked —
 * which is far more reliable than driving the chat screen by hand.
 */
object NotificationReplyStore {

    private const val TAG = "AutoFlowReply"
    private const val CAPACITY = 60

    data class Entry(
        val key: String,
        val packageName: String,
        val title: String,
        val text: String,
        val postedAt: Long,
        val action: Notification.Action,
    )

    /** Insertion-ordered so the oldest entry is the cheapest to evict. */
    private val entries = LinkedHashMap<String, Entry>()

    @Synchronized
    fun remember(sbn: StatusBarNotification) {
        val notification = sbn.notification ?: return
        val action = findReplyAction(notification) ?: return

        val extras = notification.extras
        val entry = Entry(
            key = sbn.key,
            packageName = sbn.packageName,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
            postedAt = sbn.postTime,
            action = action,
        )

        // A re-posted conversation keeps its key, so replacing keeps one entry per chat.
        entries.remove(sbn.key)
        entries[sbn.key] = entry

        while (entries.size > CAPACITY) {
            val oldest = entries.keys.firstOrNull() ?: break
            entries.remove(oldest)
        }
    }

    @Synchronized
    fun forget(key: String) {
        entries.remove(key)
    }

    /**
     * Finds the newest repliable notification for a conversation.
     *
     * [title] is matched loosely because the trigger may have been started by a slightly
     * different string than the one the notification carries — a saved contact name versus
     * the raw number, for instance.
     */
    @Synchronized
    fun find(packageName: String, title: String): Entry? {
        val candidates = entries.values.filter { it.packageName == packageName }
        if (candidates.isEmpty()) return null

        if (title.isNotBlank()) {
            candidates.lastOrNull { it.title.equals(title, ignoreCase = true) }?.let { return it }
            candidates.lastOrNull { it.title.contains(title, ignoreCase = true) }?.let { return it }
            // Unsaved senders show as a formatted number, so compare digits only.
            val wanted = PhoneMatch.digits(title)
            if (wanted.length >= 6) {
                candidates.lastOrNull { PhoneMatch.sameNumber(it.title, title) }?.let { return it }
            }
        }
        return candidates.lastOrNull()
    }

    /** Sends [message] through the stored reply action. Returns false if the app rejected it. */
    fun reply(context: Context, entry: Entry, message: String): Boolean {
        val remoteInputs = entry.action.remoteInputs
        if (remoteInputs.isNullOrEmpty()) return false

        val intent = Intent()
        val results = Bundle()
        for (input in remoteInputs) {
            results.putCharSequence(input.resultKey, message)
        }

        // The framework RemoteInput and the AndroidX one write the same extras, but only the
        // AndroidX helper accepts the framework array shape used by Notification.Action.
        RemoteInput.addResultsToIntent(
            remoteInputs.map { framework ->
                RemoteInput.Builder(framework.resultKey)
                    .setLabel(framework.label)
                    .setAllowFreeFormInput(framework.allowFreeFormInput)
                    .build()
            }.toTypedArray(),
            intent,
            results,
        )

        return try {
            entry.action.actionIntent.send(context, 0, intent)
            true
        } catch (cancelled: PendingIntent.CanceledException) {
            Log.w(TAG, "reply intent was cancelled by ${entry.packageName}")
            false
        }
    }

    /** The first action carrying free-form text input is the conversation's reply box. */
    private fun findReplyAction(notification: Notification): Notification.Action? =
        notification.actions?.firstOrNull { action ->
            action.remoteInputs?.any { it.allowFreeFormInput } == true
        }
}

/** Digit-only comparison so saved names, raw numbers and formatted numbers all line up. */
object PhoneMatch {

    fun digits(value: String): String = value.filter { it.isDigit() }

    /**
     * True when two strings denote the same phone number.
     *
     * Compared from the right so that a local number matches the same number written with a
     * country code (`0779788716` vs `+967779788716`).
     */
    fun sameNumber(a: String, b: String): Boolean {
        val left = digits(a).trimStart('0')
        val right = digits(b).trimStart('0')
        if (left.length < 6 || right.length < 6) return false
        val span = minOf(left.length, right.length, 9)
        return left.takeLast(span) == right.takeLast(span)
    }
}
