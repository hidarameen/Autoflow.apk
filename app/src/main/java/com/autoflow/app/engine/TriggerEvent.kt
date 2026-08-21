package com.autoflow.app.engine

/**
 * Normalised form of anything that can start a rule. Every trigger source converts its own
 * payload into this, so the engine never needs to know where an event came from.
 */
data class TriggerEvent(
    val source: Source,
    val packageName: String = "",
    val appLabel: String = packageName,
    val title: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    /** Extra numeric payload: battery percent, interval minutes, and similar. */
    val value: Int = 0,
) {
    enum class Source {
        NOTIFICATION,
        NOTIFICATION_REMOVED,
        SCREEN_TEXT,
        APP_OPENED,
        APP_CLOSED,
        SCHEDULE,
        INTERVAL,
        SMS,
        SCREEN_ON,
        SCREEN_OFF,
        DEVICE_UNLOCKED,
        POWER_CONNECTED,
        POWER_DISCONNECTED,
        BATTERY,
        WIFI_CONNECTED,
        WIFI_DISCONNECTED,
        HEADSET,
        CLIPBOARD,
        APP_INSTALLED,
        APP_UNINSTALLED,
        BOOT,
        SHAKE,
        QUICK_TILE,
        MANUAL,
    }

    companion object {
        fun manual() = TriggerEvent(Source.MANUAL, packageName = "com.autoflow.app")

        fun of(source: Source) = TriggerEvent(source)
    }
}
