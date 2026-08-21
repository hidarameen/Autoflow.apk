package com.autoflow.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Shared Json instance. A readable discriminator keeps stored rules debuggable. */
val AppJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "kind"
    prettyPrint = false
}

/**
 * What starts a rule. Each variant is converted into a
 * [com.autoflow.app.engine.TriggerEvent] by whichever system listener owns it, so the
 * engine matches every trigger the same way.
 */
@Serializable
sealed interface TriggerSpec {

    // ---- Apps and screen -------------------------------------------------

    /** A notification was posted. The workhorse: it carries sender and body for every messenger. */
    @Serializable
    @SerialName("notification")
    data class Notification(
        val packageNames: List<String> = emptyList(),
        val title: MatchSpec = MatchSpec.ANY,
        val text: MatchSpec = MatchSpec.ANY,
        val ignoreOngoing: Boolean = true,
        /** Drops a message identical to the previous one from the same app. */
        val ignoreDuplicates: Boolean = true,
    ) : TriggerSpec

    @Serializable
    @SerialName("notification_removed")
    data class NotificationRemoved(val packageNames: List<String> = emptyList()) : TriggerSpec

    /** Text appeared on screen while [packageName] was in the foreground. */
    @Serializable
    @SerialName("screen_text")
    data class ScreenText(
        val packageName: String = "",
        val text: MatchSpec = MatchSpec.ANY,
    ) : TriggerSpec

    @Serializable
    @SerialName("app_opened")
    data class AppOpened(val packageName: String = "") : TriggerSpec

    @Serializable
    @SerialName("app_closed")
    data class AppClosed(val packageName: String = "") : TriggerSpec

    // ---- Time ------------------------------------------------------------

    /** Daily at a wall-clock time, on the given Calendar day numbers (Sunday = 1). */
    @Serializable
    @SerialName("schedule")
    data class Schedule(
        val hour: Int = 9,
        val minute: Int = 0,
        val days: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7),
    ) : TriggerSpec

    /** Every N minutes while the engine is running. */
    @Serializable
    @SerialName("interval")
    data class Interval(val minutes: Int = 30) : TriggerSpec

    // ---- Messaging -------------------------------------------------------

    @Serializable
    @SerialName("sms_received")
    data class SmsReceived(
        val from: MatchSpec = MatchSpec.ANY,
        val body: MatchSpec = MatchSpec.ANY,
    ) : TriggerSpec

    // ---- Device state ----------------------------------------------------

    @Serializable
    @SerialName("screen_on")
    data object ScreenOn : TriggerSpec

    @Serializable
    @SerialName("screen_off")
    data object ScreenOff : TriggerSpec

    @Serializable
    @SerialName("device_unlocked")
    data object DeviceUnlocked : TriggerSpec

    @Serializable
    @SerialName("power_connected")
    data object PowerConnected : TriggerSpec

    @Serializable
    @SerialName("power_disconnected")
    data object PowerDisconnected : TriggerSpec

    @Serializable
    @SerialName("battery_level")
    data class BatteryLevel(val percent: Int = 20, val below: Boolean = true) : TriggerSpec

    @Serializable
    @SerialName("wifi_connected")
    data object WifiConnected : TriggerSpec

    @Serializable
    @SerialName("wifi_disconnected")
    data object WifiDisconnected : TriggerSpec

    @Serializable
    @SerialName("headset")
    data class Headset(val plugged: Boolean = true) : TriggerSpec

    @Serializable
    @SerialName("clipboard_changed")
    data object ClipboardChanged : TriggerSpec

    @Serializable
    @SerialName("app_installed")
    data object AppInstalled : TriggerSpec

    @Serializable
    @SerialName("app_uninstalled")
    data object AppUninstalled : TriggerSpec

    @Serializable
    @SerialName("boot_completed")
    data object BootCompleted : TriggerSpec

    /** Shake the device. Threshold is tuned for a deliberate shake, not a pocket jostle. */
    @Serializable
    @SerialName("shake")
    data object Shake : TriggerSpec

    /** Tapping AutoFlow's Quick Settings tile. */
    @Serializable
    @SerialName("quick_tile")
    data object QuickTile : TriggerSpec

    @Serializable
    @SerialName("manual")
    data object Manual : TriggerSpec

    // ---- App-Specific Triggers ------------------------------------------

    @Serializable
    @SerialName("whatsapp_message")
    data class WhatsAppMessage(
        val sender: MatchSpec = MatchSpec.ANY,
        val text: MatchSpec = MatchSpec.ANY,
        val ignoreDuplicates: Boolean = true,
    ) : TriggerSpec

    @Serializable
    @SerialName("telegram_message")
    data class TelegramMessage(
        val sender: MatchSpec = MatchSpec.ANY,
        val text: MatchSpec = MatchSpec.ANY,
        val ignoreDuplicates: Boolean = true,
    ) : TriggerSpec

    @Serializable
    @SerialName("instagram_notification")
    data class InstagramNotification(
        val sender: MatchSpec = MatchSpec.ANY,
        val text: MatchSpec = MatchSpec.ANY,
    ) : TriggerSpec

    @Serializable
    @SerialName("youtube_notification")
    data class YouTubeNotification(
        val channel: MatchSpec = MatchSpec.ANY,
        val title: MatchSpec = MatchSpec.ANY,
    ) : TriggerSpec

    @Serializable
    @SerialName("x_notification")
    data class XNotification(
        val sender: MatchSpec = MatchSpec.ANY,
        val text: MatchSpec = MatchSpec.ANY,
    ) : TriggerSpec
}

val TriggerSpec.label: String
    get() = when (this) {
        is TriggerSpec.Notification -> {
            val from = if (packageNames.isEmpty()) "any app" else packageNames.joinToString()
            buildString {
                append("Notification from $from")
                if (title.isFilter) append(", sender ${title.label}")
                if (text.isFilter) append(", body ${text.label}")
            }
        }
        is TriggerSpec.NotificationRemoved ->
            "Notification dismissed in ${packageNames.ifEmpty { listOf("any app") }.joinToString()}"
        is TriggerSpec.ScreenText ->
            "Screen text ${text.label}${if (packageName.isNotBlank()) " in $packageName" else ""}"
        is TriggerSpec.AppOpened -> "$packageName opens"
        is TriggerSpec.AppClosed -> "$packageName closes"
        is TriggerSpec.Schedule -> "Daily at %02d:%02d".format(hour, minute)
        is TriggerSpec.Interval -> "Every $minutes min"
        is TriggerSpec.SmsReceived -> "SMS received"
        TriggerSpec.ScreenOn -> "Screen turns on"
        TriggerSpec.ScreenOff -> "Screen turns off"
        TriggerSpec.DeviceUnlocked -> "Device unlocked"
        TriggerSpec.PowerConnected -> "Charger connected"
        TriggerSpec.PowerDisconnected -> "Charger disconnected"
        is TriggerSpec.BatteryLevel -> "Battery ${if (below) "below" else "above"} $percent%"
        TriggerSpec.WifiConnected -> "Wi-Fi connects"
        TriggerSpec.WifiDisconnected -> "Wi-Fi disconnects"
        is TriggerSpec.Headset -> if (plugged) "Headphones plugged in" else "Headphones unplugged"
        TriggerSpec.ClipboardChanged -> "Clipboard changes"
        TriggerSpec.AppInstalled -> "An app is installed"
        TriggerSpec.AppUninstalled -> "An app is uninstalled"
        TriggerSpec.BootCompleted -> "Device finishes booting"
        TriggerSpec.Shake -> "Device is shaken"
        TriggerSpec.QuickTile -> "Quick Settings tile tapped"
        TriggerSpec.Manual -> "Manual run only"
        is TriggerSpec.WhatsAppMessage -> "WhatsApp message from ${if (sender.isFilter) sender.label else "anyone"}"
        is TriggerSpec.TelegramMessage -> "Telegram message from ${if (sender.isFilter) sender.label else "anyone"}"
        is TriggerSpec.InstagramNotification -> "Instagram notification from ${if (sender.isFilter) sender.label else "anyone"}"
        is TriggerSpec.YouTubeNotification -> "YouTube notification from ${if (channel.isFilter) channel.label else "any channel"}"
        is TriggerSpec.XNotification -> "X notification from ${if (sender.isFilter) sender.label else "anyone"}"
    }
