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
    /**
     * Source-specific identifier. Telegram chat ids are 64-bit and negative for groups
     * (-1001234567890), so they cannot live in [value] without overflowing.
     */
    val tag: String = "",
    /**
     * Local content:// URI of media that arrived with the message, already downloaded and
     * shareable. Empty when the message was text only.
     */
    val mediaUri: String = "",
    /** MIME type of [mediaUri], e.g. image/jpeg. */
    val mediaType: String = "",
) {
    enum class Source {
        NOTIFICATION,
        CHAT_MESSAGE,
        TELEGRAM_BOT,
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

/**
 * Cheap pre-filter used on the accessibility hot path: answers "could this trigger ever
 * care about this kind of event?" without running the full matcher.
 */
fun com.autoflow.app.data.TriggerSpec.listensTo(source: TriggerEvent.Source): Boolean {
    val t = this
    return when (source) {
        TriggerEvent.Source.NOTIFICATION ->
            t is com.autoflow.app.data.TriggerSpec.Notification ||
                t is com.autoflow.app.data.TriggerSpec.WhatsAppMessage ||
                t is com.autoflow.app.data.TriggerSpec.TelegramMessage ||
                t is com.autoflow.app.data.TriggerSpec.InstagramNotification ||
                t is com.autoflow.app.data.TriggerSpec.YouTubeNotification ||
                t is com.autoflow.app.data.TriggerSpec.XNotification

        TriggerEvent.Source.CHAT_MESSAGE -> t is com.autoflow.app.data.TriggerSpec.ChatMessage
        TriggerEvent.Source.TELEGRAM_BOT -> t is com.autoflow.app.data.TriggerSpec.TelegramBot
        TriggerEvent.Source.SCREEN_TEXT -> t is com.autoflow.app.data.TriggerSpec.ScreenText
        TriggerEvent.Source.APP_OPENED -> t is com.autoflow.app.data.TriggerSpec.AppOpened
        TriggerEvent.Source.APP_CLOSED -> t is com.autoflow.app.data.TriggerSpec.AppClosed
        TriggerEvent.Source.NOTIFICATION_REMOVED ->
            t is com.autoflow.app.data.TriggerSpec.NotificationRemoved
        else -> true
    }
}
