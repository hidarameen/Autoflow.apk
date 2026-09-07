package com.autoflow.app.action

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Reads messages straight off a chat screen through the accessibility tree.
 *
 * This is the path that does NOT depend on notifications: as long as the conversation is
 * on screen, every bubble is a node with text, whether or not the user has notifications
 * enabled for that app. A scheduled rule can therefore open a chat, let this harvest it,
 * and act on messages that never produced a notification at all.
 *
 * Message bubbles are identified by view-id where the app exposes a stable one, and fall
 * back to a shape heuristic: a leaf text node that is not part of the toolbar or the
 * composer.
 */
object ChatReader {

    data class ChatMessage(
        val sender: String,
        val text: String,
        /** Stable enough to de-duplicate across repeated scans of the same screen. */
        val fingerprint: String,
    )

    /** View-id fragments that carry the message body in the major messengers. */
    private val MESSAGE_IDS = listOf(
        "message_text",       // WhatsApp
        "messageTextView",    // Telegram
        "text_content",
        "message_body",
        "row_content",
        "direct_text_message_text_view", // Instagram
    )

    /**
     * Ids that only ever appear inside a message the user sent.
     *
     * A delivery indicator (the one/two ticks) exists solely on an outgoing bubble, so a row
     * carrying one is our own message and must not trigger a reply — otherwise the rule
     * answers the user's own typing, which is exactly what happened before this filter.
     */
    private val OUTGOING_IDS = listOf("status", "message_status", "delivery")

    /** Words WhatsApp/Telegram put on an outgoing row's description, in either language. */
    private val OUTGOING_WORDS = listOf(
        "sent", "delivered", "read", "seen",
        "تم الإرسال",
        "تم التسليم",
        "مقروء",
    )

    /** Ids and words that mark chrome we must never mistake for a message. */
    private val CHROME_IDS = listOf(
        "entry", "edit_text", "compose", "input", "search_src_text",
        "toolbar", "action_bar", "conversation_contact_name",
    )

    private const val MAX_DEPTH = 40
    private const val MAX_MESSAGES = 40

    /**
     * Collects the visible messages in the conversation, oldest first.
     *
     * [selfHints] are strings that mark an outgoing bubble; anything matching is skipped so
     * a rule does not react to the user's own messages.
     */
    fun readMessages(root: AccessibilityNodeInfo?): List<ChatMessage> {
        if (root == null) return emptyList()

        val found = mutableListOf<ChatMessage>()

        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > MAX_DEPTH || found.size >= MAX_MESSAGES) return

            val viewId = node.viewIdResourceName?.substringAfterLast('/').orEmpty()
            val text = node.text?.toString()?.trim().orEmpty()

            val isChrome = CHROME_IDS.any { viewId.contains(it, ignoreCase = true) } ||
                node.isEditable

            if (!isChrome && text.isNotBlank() && looksLikeMessage(node, viewId, text) &&
                !isOutgoing(node)
            ) {
                val sender = describeSender(node)
                found += ChatMessage(
                    sender = sender,
                    text = text,
                    fingerprint = "$sender|$text",
                )
            }

            for (i in 0 until node.childCount) {
                walk(node.getChild(i) ?: continue, depth + 1)
            }
        }

        walk(root, 0)
        return found
    }

    private fun looksLikeMessage(
        node: AccessibilityNodeInfo,
        viewId: String,
        text: String,
    ): Boolean {
        if (MESSAGE_IDS.any { viewId.contains(it, ignoreCase = true) }) return true

        // Fallback: a leaf TextView with real content. Timestamps and one-word chrome
        // ("Today", "12:04") are too short to be worth acting on.
        val isLeafText = node.childCount == 0 &&
            node.className?.toString()?.contains("TextView", ignoreCase = true) == true
        return isLeafText && text.length >= 3 && !isTimestamp(text)
    }

    /**
     * True when the bubble containing [node] is one the user sent.
     *
     * Walks up a few levels because the delivery indicator is a sibling of the text, not a
     * child of it.
     */
    private fun isOutgoing(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 4) {
            if (subtreeHasDeliveryMark(current, 0)) return true

            val description = current.contentDescription?.toString()?.lowercase().orEmpty()
            if (description.isNotBlank() && OUTGOING_WORDS.any { description.contains(it) }) {
                return true
            }
            current = current.parent
            depth++
        }
        return false
    }

    private fun subtreeHasDeliveryMark(node: AccessibilityNodeInfo, depth: Int): Boolean {
        if (depth > 3) return false
        val id = node.viewIdResourceName?.substringAfterLast('/').orEmpty().lowercase()
        if (id.isNotEmpty() && OUTGOING_IDS.any { id == it || id.endsWith("_$it") }) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (subtreeHasDeliveryMark(child, depth + 1)) return true
        }
        return false
    }

    private fun isTimestamp(text: String): Boolean =
        Regex("^\\d{1,2}:\\d{2}(\\s?[AaPp][Mm])?$").matches(text) ||
            text.equals("today", true) ||
            text.equals("yesterday", true)

    /**
     * Walks up looking for a content description that names the sender. WhatsApp and
     * Telegram both describe the bubble container with the sender name.
     */
    private fun describeSender(node: AccessibilityNodeInfo): String {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 5) {
            val description = current.contentDescription?.toString().orEmpty()
            if (description.isNotBlank()) {
                // Descriptions read like "Ali, 12:04" or "Message from Ali".
                val cleaned = description
                    .substringBefore(',')
                    .removePrefix("Message from ")
                    .trim()
                if (cleaned.isNotBlank() && cleaned.length < 40) return cleaned
            }
            current = current.parent
            depth++
        }
        return ""
    }
}
