package com.autoflow.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Which global navigation button to press. */
@Serializable
enum class GlobalActionType { BACK, HOME, RECENTS, NOTIFICATIONS, QUICK_SETTINGS, LOCK_SCREEN, POWER_DIALOG }

/** How a [ActionSpec.SetText] target is located on screen. */
@Serializable
enum class TextFieldTarget { FOCUSED, FIRST_EDITABLE, BY_HINT, BY_VIEW_ID }

@Serializable
enum class ScrollDirection { FORWARD, BACKWARD }

@Serializable
enum class MediaCommand { PLAY_PAUSE, NEXT, PREVIOUS, STOP }

@Serializable
enum class VolumeStream { MUSIC, RING, NOTIFICATION, ALARM }

@Serializable
enum class SettingsPanel { INTERNET, WIFI, NFC, VOLUME }

/**
 * One step of a rule. Steps run in order; control-flow steps ([If], [Else], [EndIf],
 * [Repeat], [EndRepeat]) are flat markers interpreted by the executor rather than nested
 * lists, which keeps the editor a simple reorderable list.
 */
@Serializable
sealed interface ActionSpec {

    // ---- Launching -------------------------------------------------------

    @Serializable
    @SerialName("launch_app")
    data class LaunchApp(val packageName: String = "") : ActionSpec

    @Serializable
    @SerialName("launch_activity")
    data class LaunchActivity(
        val packageName: String = "",
        val activityClass: String = "",
    ) : ActionSpec

    @Serializable
    @SerialName("open_url")
    data class OpenUrl(val url: String = "", val packageName: String = "") : ActionSpec

    // ---- Screen interaction ----------------------------------------------

    @Serializable
    @SerialName("click_text")
    data class ClickText(
        val text: String = "",
        val exact: Boolean = false,
        val timeoutMs: Long = 8_000,
        /** Friendly name shown in the step list; blank falls back to a generated label. */
        val label: String = "",
    ) : ActionSpec

    @Serializable
    @SerialName("long_click_text")
    data class LongClickText(
        val text: String = "",
        val exact: Boolean = false,
        val timeoutMs: Long = 8_000,
        val label: String = "",
    ) : ActionSpec

    @Serializable
    @SerialName("click_desc")
    data class ClickDescription(
        val description: String = "",
        val timeoutMs: Long = 8_000,
        val label: String = "",
    ) : ActionSpec

    @Serializable
    @SerialName("click_id")
    data class ClickViewId(
        val viewId: String = "",
        val timeoutMs: Long = 8_000,
        val label: String = "",
    ) : ActionSpec

    @Serializable
    @SerialName("set_text")
    data class SetText(
        val text: String = "",
        val target: TextFieldTarget = TextFieldTarget.FIRST_EDITABLE,
        val selector: String = "",
        val timeoutMs: Long = 8_000,
        val label: String = "",
    ) : ActionSpec

    @Serializable
    @SerialName("wait_for_text")
    data class WaitForText(val text: String = "", val timeoutMs: Long = 10_000) : ActionSpec

    @Serializable
    @SerialName("wait_for_app")
    data class WaitForApp(val packageName: String = "", val timeoutMs: Long = 10_000) : ActionSpec

    @Serializable
    @SerialName("scroll")
    data class Scroll(
        val direction: ScrollDirection = ScrollDirection.FORWARD,
        val timeoutMs: Long = 5_000,
    ) : ActionSpec

    @Serializable
    @SerialName("tap")
    data class Tap(val x: Int = 0, val y: Int = 0) : ActionSpec

    @Serializable
    @SerialName("long_press")
    data class LongPress(val x: Int = 0, val y: Int = 0, val durationMs: Long = 700) : ActionSpec

    @Serializable
    @SerialName("swipe")
    data class Swipe(
        val x1: Int = 540,
        val y1: Int = 1600,
        val x2: Int = 540,
        val y2: Int = 600,
        val durationMs: Long = 300,
    ) : ActionSpec

    @Serializable
    @SerialName("global")
    data class Global(val action: GlobalActionType = GlobalActionType.BACK) : ActionSpec

    /** Captures the screen into a variable-free file; requires Android 11+. */
    @Serializable
    @SerialName("screenshot")
    data object Screenshot : ActionSpec

    // ---- Text and data ---------------------------------------------------

    @Serializable
    @SerialName("copy_clipboard")
    data class CopyToClipboard(val text: String = "{{text}}") : ActionSpec

    @Serializable
    @SerialName("read_clipboard")
    data class ReadClipboard(val variable: String = "clip") : ActionSpec

    @Serializable
    @SerialName("set_variable")
    data class SetVariable(val name: String = "", val value: String = "") : ActionSpec

    /** Pulls part of the message out with a regex so later steps can use just that piece. */
    @Serializable
    @SerialName("extract_regex")
    data class ExtractRegex(
        val source: String = "{{text}}",
        val pattern: String = "",
        val group: Int = 1,
        val variable: String = "match",
    ) : ActionSpec

    /**
     * Brings a conversation on screen so the chat reader can harvest it. This is what makes
     * a scheduled "check my messages" rule possible without notifications.
     */
    /**
     * Answers through the notification's own reply box. Works with the screen off and the
     * device locked, and never opens the target app.
     */
    @Serializable
    @SerialName("reply_notification")
    data class ReplyToNotification(
        val message: String = "{{text}}",
        /** Blank uses the notification that started this rule. */
        val packageName: String = "",
        val sender: String = "",
    ) : ActionSpec

    @Serializable
    @SerialName("open_chat")
    data class OpenChat(
        val packageName: String = "",
        val contact: String = "",
        val searchDescription: String = "Search",
    ) : ActionSpec

    /** Reads the messages currently on screen into a variable. */
    @Serializable
    @SerialName("read_chat")
    data class ReadChat(
        val variable: String = "messages",
        val limit: Int = 10,
    ) : ActionSpec

    @Serializable
    @SerialName("read_screen")
    data class ReadScreenText(val variable: String = "screen") : ActionSpec

    // ---- Sending out -----------------------------------------------------

    @Serializable
    @SerialName("share_text")
    data class ShareText(val packageName: String = "", val text: String = "{{text}}") : ActionSpec

    /**
     * Shares text plus one or more media files. A single item uses ACTION_SEND; several use
     * ACTION_SEND_MULTIPLE, which is what produces an album/carousel post on X.
     */
    @Serializable
    @SerialName("share_media")
    data class ShareMedia(
        val packageName: String = "",
        val text: String = "",
        /** content:// or file:// URIs, comma separated in the editor. */
        val mediaUris: List<String> = emptyList(),
        val mimeType: String = "image/*",
        /** Optional explicit component, e.g. X's composer, to skip the chooser. */
        val activityClass: String = "",
        /**
         * Extra string extras for the intent. WhatsApp uses "jid"
         * (<number>@s.whatsapp.net) to target one chat directly instead of showing its
         * contact picker.
         */
        val extras: Map<String, String> = emptyMap(),
    ) : ActionSpec

    @Serializable
    @SerialName("http")
    data class HttpRequest(
        val url: String = "",
        val method: String = "POST",
        val headers: Map<String, String> = emptyMap(),
        val body: String = "",
        val contentType: String = "application/json",
        /** Stores the response body in this variable so later steps can branch on it. */
        val saveResponseTo: String = "",
    ) : ActionSpec

    @Serializable
    @SerialName("send_sms")
    data class SendSms(val to: String = "", val body: String = "{{text}}") : ActionSpec

    @Serializable
    @SerialName("dial")
    data class Dial(val number: String = "") : ActionSpec

    // ---- Feedback --------------------------------------------------------

    @Serializable
    @SerialName("notify")
    data class Notify(val title: String = "AutoFlow", val message: String = "{{text}}") : ActionSpec

    @Serializable
    @SerialName("toast")
    data class Toast(val text: String = "") : ActionSpec

    @Serializable
    @SerialName("speak")
    data class Speak(val text: String = "{{text}}") : ActionSpec

    @Serializable
    @SerialName("vibrate")
    data class Vibrate(val millis: Long = 300) : ActionSpec

    // ---- Device ----------------------------------------------------------

    @Serializable
    @SerialName("set_volume")
    data class SetVolume(
        val stream: VolumeStream = VolumeStream.MUSIC,
        val percent: Int = 50,
    ) : ActionSpec

    @Serializable
    @SerialName("media")
    data class Media(val command: MediaCommand = MediaCommand.PLAY_PAUSE) : ActionSpec

    @Serializable
    @SerialName("wake_screen")
    data object WakeScreen : ActionSpec

    @Serializable
    @SerialName("settings_panel")
    data class OpenSettingsPanel(val panel: SettingsPanel = SettingsPanel.INTERNET) : ActionSpec

    // ---- Flow control ----------------------------------------------------

    @Serializable
    @SerialName("delay")
    data class Delay(val millis: Long = 1_000) : ActionSpec

    @Serializable
    @SerialName("if")
    data class If(val condition: ConditionSpec = ConditionSpec.Text()) : ActionSpec

    @Serializable
    @SerialName("else")
    data object Else : ActionSpec

    @Serializable
    @SerialName("end_if")
    data object EndIf : ActionSpec

    @Serializable
    @SerialName("repeat")
    data class Repeat(val times: Int = 2) : ActionSpec

    @Serializable
    @SerialName("end_repeat")
    data object EndRepeat : ActionSpec

    /**
     * Switches error handling for the steps that follow.
     *
     * Some confirmations only exist sometimes — WhatsApp shows a preview for media but
     * sends plain text straight away — so a missing button there should not abort the rest
     * of a multi-destination rule.
     */
    @Serializable
    @SerialName("ignore_errors")
    data class IgnoreErrors(val ignore: Boolean = true) : ActionSpec

    @Serializable
    @SerialName("stop_rule")
    data object StopRule : ActionSpec

    @Serializable
    @SerialName("run_rule")
    data class RunRule(val ruleName: String = "") : ActionSpec

    @Serializable
    @SerialName("log")
    data class Log(val message: String = "") : ActionSpec

    // ---- App-Specific Actions -------------------------------------------

    @Serializable
    @SerialName("whatsapp_send")
    data class WhatsAppSend(
        val phone: String = "",
        val message: String = "",
    ) : ActionSpec

    @Serializable
    @SerialName("whatsapp_reply")
    data class WhatsAppReply(
        val message: String = "",
    ) : ActionSpec

    @Serializable
    @SerialName("whatsapp_forward")
    data class WhatsAppForward(
        val phone: String = "",
    ) : ActionSpec

    @Serializable
    @SerialName("telegram_send")
    data class TelegramSend(
        val botToken: String = "",
        val chatId: String = "",
        val message: String = "",
    ) : ActionSpec

    @Serializable
    @SerialName("telegram_reply")
    data class TelegramReply(
        val message: String = "",
    ) : ActionSpec

    @Serializable
    @SerialName("instagram_direct")
    data class InstagramDirect(
        val username: String = "",
    ) : ActionSpec

    @Serializable
    @SerialName("youtube_open")
    data class YouTubeOpen(
        val queryOrUrl: String = "",
    ) : ActionSpec

    @Serializable
    @SerialName("x_post")
    data class XPost(
        val tweet: String = "",
    ) : ActionSpec
}

val ActionSpec.label: String
    get() = when (this) {
        is ActionSpec.LaunchApp -> "Open $packageName"
        is ActionSpec.LaunchActivity -> "Open $packageName/$activityClass"
        is ActionSpec.OpenUrl -> "Open $url"
        is ActionSpec.ClickText -> label.ifBlank { "Tap text \"$text\"" }
        is ActionSpec.LongClickText -> label.ifBlank { "Long-press \"$text\"" }
        is ActionSpec.ClickDescription -> label.ifBlank { "Tap \"$description\"" }
        is ActionSpec.ClickViewId -> label.ifBlank { "Tap id $viewId" }
        is ActionSpec.SetText -> label.ifBlank { "Type \"$text\"" }
        is ActionSpec.WaitForText -> "Wait for \"$text\""
        is ActionSpec.WaitForApp -> "Wait for $packageName"
        is ActionSpec.Scroll -> "Scroll ${direction.name.lowercase()}"
        is ActionSpec.Tap -> "Tap at ($x, $y)"
        is ActionSpec.LongPress -> "Long-press at ($x, $y)"
        is ActionSpec.Swipe -> "Swipe ($x1,$y1) to ($x2,$y2)"
        is ActionSpec.Global -> "Press ${action.name.lowercase().replace('_', ' ')}"
        ActionSpec.Screenshot -> "Take a screenshot"
        is ActionSpec.CopyToClipboard -> "Copy to clipboard"
        is ActionSpec.ReadClipboard -> "Read clipboard into {{$variable}}"
        is ActionSpec.SetVariable -> "Set {{$name}}"
        is ActionSpec.ExtractRegex -> "Extract /$pattern/ into {{$variable}}"
        is ActionSpec.ReadScreenText -> "Read screen into {{$variable}}"
        is ActionSpec.ReplyToNotification -> "Reply via notification"
        is ActionSpec.OpenChat -> "Open chat with ${contact.ifBlank { "…" }}"
        is ActionSpec.ReadChat -> "Read chat into {{$variable}}"
        is ActionSpec.ShareMedia ->
            "Share ${mediaUris.size} media to ${packageName.ifBlank { "chooser" }}"
        is ActionSpec.ShareText -> "Share to ${packageName.ifBlank { "chooser" }}"
        is ActionSpec.HttpRequest -> "$method $url"
        is ActionSpec.SendSms -> "SMS to $to"
        is ActionSpec.Dial -> "Dial $number"
        is ActionSpec.Notify -> "Notify \"$title\""
        is ActionSpec.Toast -> "Toast \"$text\""
        is ActionSpec.Speak -> "Speak text"
        is ActionSpec.Vibrate -> "Vibrate ${millis}ms"
        is ActionSpec.SetVolume -> "Set ${stream.name.lowercase()} volume to $percent%"
        is ActionSpec.Media -> "Media ${command.name.lowercase().replace('_', ' ')}"
        ActionSpec.WakeScreen -> "Wake the screen"
        is ActionSpec.OpenSettingsPanel -> "Open ${panel.name.lowercase()} settings"
        is ActionSpec.Delay -> "Wait ${millis}ms"
        is ActionSpec.If -> "If ${condition.label}"
        ActionSpec.Else -> "Else"
        ActionSpec.EndIf -> "End if"
        is ActionSpec.Repeat -> "Repeat $times times"
        ActionSpec.EndRepeat -> "End repeat"
        is ActionSpec.IgnoreErrors ->
            if (ignore) "Ignore failures from here" else "Stop on failure again"
        ActionSpec.StopRule -> "Stop this rule"
        is ActionSpec.RunRule -> "Run rule \"$ruleName\""
        is ActionSpec.Log -> "Log \"$message\""
        is ActionSpec.WhatsAppSend -> "WhatsApp: Send to $phone"
        is ActionSpec.WhatsAppReply -> "WhatsApp: Reply \"$message\""
        is ActionSpec.WhatsAppForward -> "WhatsApp: Forward to $phone"
        is ActionSpec.TelegramSend -> "Telegram: Send to $chatId"
        is ActionSpec.TelegramReply -> "Telegram: Reply \"$message\""
        is ActionSpec.InstagramDirect -> "Instagram: DM $username"
        is ActionSpec.YouTubeOpen -> "YouTube: Open $queryOrUrl"
        is ActionSpec.XPost -> "X: Post \"$tweet\""
    }

/** Steps that open or close a block, used by the editor to indent the list. */
val ActionSpec.indentDelta: Int
    get() = when (this) {
        is ActionSpec.If, is ActionSpec.Repeat -> 1
        ActionSpec.EndIf, ActionSpec.EndRepeat -> -1
        else -> 0
    }
