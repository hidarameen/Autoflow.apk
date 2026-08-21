package com.autoflow.app.trigger

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
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

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> emitScreenText(eventPackage, event)
        }
    }

    private fun isSystemSurface(packageName: String): Boolean =
        packageName == "com.android.systemui" || packageName.endsWith(".launcher")

    private fun emitScreenText(eventPackage: String, event: AccessibilityEvent) {
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
    }
}
