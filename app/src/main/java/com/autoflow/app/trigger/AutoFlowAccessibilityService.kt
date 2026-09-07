package com.autoflow.app.trigger

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.autoflow.app.action.ChatReader
import com.autoflow.app.engine.RuleEngine
import com.autoflow.app.engine.TriggerEvent
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The eyes and hands of the app: reads the active window's node tree and performs
 * clicks, text entry and gestures inside other apps.
 *
 * Held as a process-wide instance because ActionExecutor needs it long after the
 * callback that delivered an event has returned.
 */
class AutoFlowAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        connected.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val eventPackage = event.packageName?.toString() ?: return
        // Ignore our own UI so a rule can never react to itself.
        if (eventPackage == applicationContext.packageName) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val previous = foregroundPackage
                if (previous != eventPackage) {
                    // Launcher and system UI transitions are not an app "closing".
                    if (previous != null && !isSystemSurface(previous)) {
                        RuleEngine.submit(
                            TriggerEvent(TriggerEvent.Source.APP_CLOSED, packageName = previous)
                        )
                    }
                    foregroundPackage = eventPackage
                    RuleEngine.submit(
                        TriggerEvent(TriggerEvent.Source.APP_OPENED, packageName = eventPackage)
                    )
                }
                emitScreenText(eventPackage, event)
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                emitScreenText(eventPackage, event)
                scanChat(eventPackage)
            }
        }
    }

    private fun isSystemSurface(packageName: String): Boolean =
        packageName == "com.android.systemui" || packageName.endsWith(".launcher")

    /**
     * Harvests messages straight off the conversation on screen.
     *
     * This is the notification-free path: while a chat is open its bubbles are ordinary
     * accessibility nodes, so a rule can react to a message even when notifications for
     * that app are switched off entirely.
     */
    private fun scanChat(eventPackage: String) {
        if (eventPackage !in CHAT_PACKAGES) return
        // Walking the whole node tree is the single most expensive thing this service does,
        // so it only runs when a rule is actually waiting on chat messages.
        if (!RuleEngine.wantsChatScan()) return

        // Scanning the whole tree on every content change would burn battery; the apps
        // fire these events continuously while a list scrolls.
        val now = System.currentTimeMillis()
        if (now - lastScanAt < SCAN_INTERVAL_MS) return
        lastScanAt = now

        val messages = ChatReader.readMessages(rootInActiveWindow)
        if (messages.isEmpty()) return

        for (message in messages) {
            // Every scan re-reads the whole visible history, so only genuinely new
            // fingerprints may fire a rule.
            if (!seenMessages.add(message.fingerprint)) continue

            RuleEngine.submit(
                TriggerEvent(
                    source = TriggerEvent.Source.CHAT_MESSAGE,
                    packageName = eventPackage,
                    appLabel = eventPackage,
                    title = message.sender,
                    text = message.text,
                )
            )
        }

        // Bounded so a long session cannot grow the set without limit.
        if (seenMessages.size > SEEN_LIMIT) {
            val excess = seenMessages.size - SEEN_LIMIT / 2
            repeat(excess) { seenMessages.iterator().let { if (it.hasNext()) { it.next(); it.remove() } } }
        }
    }

    private fun emitScreenText(eventPackage: String, event: AccessibilityEvent) {
        if (!RuleEngine.wantsScreenText()) return
        val text = event.text.joinToString(" ") { it.toString() }.trim()
        if (text.isEmpty()) return
        RuleEngine.submit(
            TriggerEvent(
                source = TriggerEvent.Source.SCREEN_TEXT,
                packageName = eventPackage,
                text = text,
            )
        )
    }

    private var lastScanAt = 0L
    private val seenMessages = linkedSetOf<String>()

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        connected.value = false
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: AutoFlowAccessibilityService? = null
            private set

        /** Last app seen in the foreground. Read by conditions and WaitForApp. */
        @Volatile
        var foregroundPackage: String? = null
            private set

        /** Observed by the UI so the setup card can show live status. */
        val connected = MutableStateFlow(false)

        /** Apps whose conversation screens the chat reader understands. */
        private val CHAT_PACKAGES = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "org.telegram.messenger",
            "org.telegram.messenger.web",
            "com.facebook.orca",
            "com.instagram.android",
            "com.twitter.android",
        )

        private const val SCAN_INTERVAL_MS = 900L
        private const val SEEN_LIMIT = 400
    }
}
