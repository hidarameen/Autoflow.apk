package com.autoflow.app.action

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Node-tree helpers. Everything here polls rather than assumes: after a tap the target
 * app needs time to render, and there is no reliable screen-settled signal to wait on.
 */
object UiAutomator {

    private const val POLL_INTERVAL_MS = 250L
    private const val MAX_ANCESTOR_DEPTH = 12

    /** Retries until [predicate] matches a node or [timeoutMs] elapses. */
    suspend fun awaitNode(
        service: AccessibilityService,
        timeoutMs: Long,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val root = service.rootInActiveWindow
            if (root != null) {
                findNode(root, predicate)?.let { return it }
            }
            delay(POLL_INTERVAL_MS)
        }
        return null
    }

    private fun findNode(
        root: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        if (predicate(root)) return root
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            findNode(child, predicate)?.let { return it }
        }
        return null
    }

    fun matchesText(node: AccessibilityNodeInfo, wanted: String, exact: Boolean): Boolean {
        val candidates = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
        return candidates.any { candidate ->
            if (exact) candidate.equals(wanted, ignoreCase = true)
            else candidate.contains(wanted, ignoreCase = true)
        }
    }

    fun matchesViewId(node: AccessibilityNodeInfo, wanted: String): Boolean {
        val id = node.viewIdResourceName ?: return false
        return id == wanted || id.substringAfterLast("/") == wanted
    }

    /**
     * Labels usually sit on a non-clickable child of the clickable row, so walk up
     * until something can actually receive the click.
     */
    fun clickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < MAX_ANCESTOR_DEPTH) {
            if (current.isClickable && current.isEnabled) return current
            current = current.parent
            depth++
        }
        return null
    }

    fun click(node: AccessibilityNodeInfo): Boolean {
        val target = clickableAncestor(node) ?: return false
        return target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    fun longClick(node: AccessibilityNodeInfo): Boolean {
        val target = clickableAncestor(node) ?: return false
        return target.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
    }

    /**
     * Flattens every visible label into one string. Used by ReadScreenText so a rule can
     * regex over whatever the current screen shows.
     */
    fun collectText(root: AccessibilityNodeInfo?): String {
        if (root == null) return ""
        val parts = mutableListOf<String>()
        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > 40) return
            node.text?.toString()?.takeIf { it.isNotBlank() }?.let(parts::add)
            node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let(parts::add)
            for (i in 0 until node.childCount) {
                walk(node.getChild(i) ?: continue, depth + 1)
            }
        }
        walk(root, 0)
        return parts.distinct().joinToString("\n")
    }

    fun setText(node: AccessibilityNodeInfo, text: String): Boolean {
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    fun isEditable(node: AccessibilityNodeInfo): Boolean =
        node.isEditable || node.className?.toString()?.contains("EditText", ignoreCase = true) == true

    /** Suspends until the gesture completes so the next action does not race it. */
    suspend fun dispatch(service: AccessibilityService, path: Path, durationMs: Long): Boolean {
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(1))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return suspendCancellableCoroutine { continuation ->
            val dispatched = service.dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(description: GestureDescription?) {
                        if (continuation.isActive) continuation.resume(true)
                    }

                    override fun onCancelled(description: GestureDescription?) {
                        if (continuation.isActive) continuation.resume(false)
                    }
                },
                null,
            )
            if (!dispatched && continuation.isActive) continuation.resume(false)
        }
    }

    fun tapPath(x: Int, y: Int): Path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }

    fun swipePath(x1: Int, y1: Int, x2: Int, y2: Int): Path = Path().apply {
        moveTo(x1.toFloat(), y1.toFloat())
        lineTo(x2.toFloat(), y2.toFloat())
    }
}
