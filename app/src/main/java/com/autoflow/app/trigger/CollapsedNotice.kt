package com.autoflow.app.trigger

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Tracks apps that are currently collapsing their notifications into a group summary.
 *
 * WhatsApp does this once a large unread backlog builds up: instead of one notification per
 * sender it posts a single "530 messages from 12 chats". That summary names no sender and
 * carries no message body, so a per-sender rule simply cannot match — and previously that
 * looked like the app was broken. Surfacing it lets the UI say so plainly.
 */
object CollapsedNotice {

    /** Packages seen posting a group summary, with the time it was last seen. */
    val collapsed = MutableStateFlow<Map<String, Long>>(emptyMap())

    /** How long a sighting stays relevant; after this it is assumed resolved. */
    private const val TTL_MS = 30 * 60 * 1000L

    fun report(packageName: String) {
        val now = System.currentTimeMillis()
        collapsed.value = collapsed.value.filterValues { now - it < TTL_MS } + (packageName to now)
    }

    /** A per-sender notification arrived, so the app is no longer collapsing. */
    fun clear(packageName: String) {
        if (!collapsed.value.containsKey(packageName)) return
        collapsed.value = collapsed.value - packageName
    }

    fun isCollapsing(packageName: String): Boolean {
        val at = collapsed.value[packageName] ?: return false
        return System.currentTimeMillis() - at < TTL_MS
    }
}
