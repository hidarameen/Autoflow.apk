package com.autoflow.app.util

import com.autoflow.app.data.ActionSpec
import com.autoflow.app.data.ConditionSpec
import com.autoflow.app.data.EventField
import com.autoflow.app.data.GlobalActionType
import com.autoflow.app.data.MatchMode
import com.autoflow.app.data.MatchSpec
import com.autoflow.app.data.MediaCommand
import com.autoflow.app.data.Rule
import com.autoflow.app.data.ScrollDirection
import com.autoflow.app.data.TextFieldTarget
import com.autoflow.app.data.TriggerSpec
import com.autoflow.app.data.VolumeStream

data class RuleTemplate(
    val title: String,
    val summary: String,
    val category: String,
    val requiresPackage: String = "",
    val build: () -> Rule,
)

/**
 * Ready-made rules grouped by the app they drive. They are ordinary rules -- the editor can
 * change every field -- and exist to show what the vocabulary can express and to give each
 * supported app a working starting point.
 *
 * UI-driven templates depend on the target app's current labels. When one stops working,
 * the fix is almost always a changed button label, not a bug in the engine.
 */
object Templates {

    val all: List<RuleTemplate> by lazy {
        bridges + telegram + whatsapp + x + instagram + facebook + youtube + browser + device
    }

    val categories: List<String> by lazy { all.map { it.category }.distinct() }

    // ---- Cross-app bridges ----------------------------------------------

    private val bridges = listOf(
        RuleTemplate(
            title = "Telegram to X (share sheet)",
            summary = "Forwards a Telegram message into the X composer, then posts it. " +
                "Fewer steps to break than driving the composer field by field.",
            category = "Bridges",
            requiresPackage = KnownPackages.X,
            build = {
                Rule(
                    name = "Telegram to X",
                    trigger = TriggerSpec.Notification(
                        packageNames = listOf(KnownPackages.TELEGRAM),
                    ),
                    actions = listOf(
                        ActionSpec.ShareText(KnownPackages.X, "{{text|trim:270}}"),
                        ActionSpec.Delay(3_000),
                        ActionSpec.ClickText("Post", exact = true, timeoutMs = 10_000),
                    ),
                    cooldownMs = 15_000,
                )
            },
        ),

        RuleTemplate(
            title = "Telegram to X (full UI automation)",
            summary = "Opens X, taps compose, types the message and posts. Use when the " +
                "share sheet is unavailable.",
            category = "Bridges",
            requiresPackage = KnownPackages.X,
            build = {
                Rule(
                    name = "Telegram to X (UI)",
                    trigger = TriggerSpec.Notification(
                        packageNames = listOf(KnownPackages.TELEGRAM),
                    ),
                    actions = listOf(
                        ActionSpec.LaunchApp(KnownPackages.X),
                        ActionSpec.WaitForApp(KnownPackages.X, 10_000),
                        ActionSpec.Delay(2_000),
                        ActionSpec.ClickDescription("Post", timeoutMs = 10_000),
                        ActionSpec.Delay(1_500),
                        ActionSpec.SetText(
                            text = "{{text|trim:270}}",
                            target = TextFieldTarget.FIRST_EDITABLE,
                            timeoutMs = 10_000,
                        ),
                        ActionSpec.Delay(1_000),
                        ActionSpec.ClickText("Post", exact = true, timeoutMs = 10_000),
                    ),
                    cooldownMs = 20_000,
                )
            },
        ),

        RuleTemplate(
            title = "Telegram to WhatsApp",
            summary = "Relays Telegram messages into WhatsApp through the share sheet.",
            category = "Bridges",
            requiresPackage = KnownPackages.WHATSAPP,
            build = {
                Rule(
                    name = "Telegram to WhatsApp",
                    trigger = TriggerSpec.Notification(
                        packageNames = listOf(KnownPackages.TELEGRAM),
                    ),
                    actions = listOf(
                        ActionSpec.ShareText(KnownPackages.WHATSAPP, "{{title}}: {{text}}"),
                        ActionSpec.Delay(2_500),
                        ActionSpec.ClickDescription("Send", timeoutMs = 10_000),
                    ),
                    cooldownMs = 15_000,
                )
            },
        ),

        RuleTemplate(
            title = "Any notification to a webhook",
            summary = "Forwards every notification to an HTTP endpoint. Works with the " +
                "screen off and never breaks on a UI change.",
            category = "Bridges",
            build = {
                Rule(
                    name = "Notifications to webhook",
                    trigger = TriggerSpec.Notification(),
                    actions = listOf(
                        ActionSpec.HttpRequest(
                            url = "https://example.com/hook",
                            method = "POST",
                            headers = mapOf("Authorization" to "Bearer REPLACE_ME"),
                            body = """{"app":"{{app|json}}","from":"{{title|json}}","text":"{{text|json}}"}""",
                        ),
                    ),
                    cooldownMs = 1_000,
                )
            },
        ),

        RuleTemplate(
            title = "Forward to Telegram via Bot API",
            summary = "Posts into a Telegram chat over HTTPS. Replace BOT_TOKEN and CHAT_ID.",
            category = "Bridges",
            build = {
                Rule(
                    name = "Forward to Telegram",
                    trigger = TriggerSpec.Notification(),
                    actions = listOf(
                        ActionSpec.HttpRequest(
                            url = "https://api.telegram.org/botBOT_TOKEN/sendMessage",
                            method = "POST",
                            body = """{"chat_id":"CHAT_ID","text":"{{app|json}}: {{text|json}}"}""",
                        ),
                    ),
                    cooldownMs = 1_000,
                )
            },
        ),

        RuleTemplate(
            title = "Post to X via API v2",
            summary = "Uses the official X API instead of the app. Needs a paid tier and a " +
                "bearer token, but works with the screen off.",
            category = "Bridges",
            build = {
                Rule(
                    name = "Post to X (API)",
                    trigger = TriggerSpec.Notification(
                        packageNames = listOf(KnownPackages.TELEGRAM),
                    ),
                    actions = listOf(
                        ActionSpec.HttpRequest(
                            url = "https://api.twitter.com/2/tweets",
                            method = "POST",
                            headers = mapOf("Authorization" to "Bearer REPLACE_ME"),
                            body = """{"text":"{{text|trim:270|json}}"}""",
                        ),
                    ),
                    cooldownMs = 15_000,
                )
            },
        ),

        RuleTemplate(
            title = "Copy one-time codes automatically",
            summary = "Pulls a 4-8 digit code out of any SMS or notification and copies it " +
                "to the clipboard, then announces it.",
            category = "Bridges",
            build = {
                Rule(
                    name = "Copy OTP codes",
                    trigger = TriggerSpec.Notification(
                        text = MatchSpec(MatchMode.REGEX, "\\b\\d{4,8}\\b"),
                    ),
                    actions = listOf(
                        ActionSpec.ExtractRegex(
                            source = "{{text}}",
                            pattern = "\\b(\\d{4,8})\\b",
                            group = 1,
                            variable = "code",
                        ),
                        ActionSpec.CopyToClipboard("{{code}}"),
                        ActionSpec.Notify("Code copied", "{{code}} from {{app}}"),
                    ),
                    cooldownMs = 3_000,
                )
            },
        ),
    )

    // ---- Telegram --------------------------------------------------------

    private val telegram = listOf(
        RuleTemplate(
            title = "Auto-reply to a Telegram contact",
            summary = "Opens the chat from the notification and sends a canned reply.",
            category = "Telegram",
            requiresPackage = KnownPackages.TELEGRAM,
            build = {
                Rule(
                    name = "Telegram auto-reply",
                    trigger = TriggerSpec.Notification(
                        packageNames = listOf(KnownPackages.TELEGRAM),
                        title = MatchSpec(MatchMode.CONTAINS, "NAME HERE"),
                    ),
                    actions = listOf(
                        ActionSpec.LaunchApp(KnownPackages.TELEGRAM),
                        ActionSpec.WaitForApp(KnownPackages.TELEGRAM, 8_000),
                        ActionSpec.ClickText("{{title}}", timeoutMs = 8_000),
                        ActionSpec.Delay(1_500),
                        ActionSpec.SetText(
                            text = "Got your message, I will reply shortly.",
                            target = TextFieldTarget.BY_HINT,
                            selector = "Message",
                        ),
                        ActionSpec.ClickDescription("Send", timeoutMs = 8_000),
                        ActionSpec.Global(GlobalActionType.HOME),
                    ),
                    cooldownMs = 60_000,
                    dailyLimit = 20,
                )
            },
        ),

        RuleTemplate(
            title = "Save Telegram links to the clipboard",
            summary = "Grabs the first URL out of a Telegram message and copies it.",
            category = "Telegram",
            build = {
                Rule(
                    name = "Save Telegram links",
                    trigger = TriggerSpec.Notification(
                        packageNames = listOf(KnownPackages.TELEGRAM),
                        text = MatchSpec(MatchMode.CONTAINS, "http"),
                    ),
                    actions = listOf(
                        ActionSpec.ExtractRegex(
                            source = "{{text}}",
                            pattern = "(https?://\\S+)",
                            group = 1,
                            variable = "link",
                        ),
                        ActionSpec.CopyToClipboard("{{link}}"),
                        ActionSpec.Toast("Link copied"),
                    ),
                )
            },
        ),

        RuleTemplate(
            title = "Read Telegram messages aloud while driving",
            summary = "Speaks incoming messages, but only when headphones are connected.",
            category = "Telegram",
            build = {
                Rule(
                    name = "Speak Telegram messages",
                    trigger = TriggerSpec.Notification(
                        packageNames = listOf(KnownPackages.TELEGRAM),
                    ),
                    conditions = listOf(ConditionSpec.ScreenState(on = false)),
                    actions = listOf(
                        ActionSpec.Speak("Message from {{title}}: {{text|trim:200}}"),
                    ),
                    cooldownMs = 8_000,
                )
            },
        ),
    )

    // ---- WhatsApp --------------------------------------------------------

    private val whatsapp = listOf(
        RuleTemplate(
            title = "WhatsApp auto-reply after hours",
            summary = "Replies only outside working hours, and at most 15 times a day.",
            category = "WhatsApp",
            requiresPackage = KnownPackages.WHATSAPP,
            build = {
                Rule(
                    name = "WhatsApp after-hours reply",
                    trigger = TriggerSpec.Notification(
                        packageNames = listOf(KnownPackages.WHATSAPP),
                    ),
                    conditions = listOf(
                        ConditionSpec.TimeWindow(startMinutes = 18 * 60, endMinutes = 8 * 60),
                    ),
                    actions = listOf(
                        ActionSpec.LaunchApp(KnownPackages.WHATSAPP),
                        ActionSpec.WaitForApp(KnownPackages.WHATSAPP, 8_000),
                        ActionSpec.ClickText("{{title}}", timeoutMs = 8_000),
                        ActionSpec.Delay(1_500),
                        ActionSpec.SetText(
                            text = "Thanks for your message. I am away and will reply tomorrow.",
                            target = TextFieldTarget.BY_HINT,
                            selector = "message",
                        ),
                        ActionSpec.ClickDescription("Send", timeoutMs = 8_000),
                        ActionSpec.Global(GlobalActionType.HOME),
                    ),
                    cooldownMs = 120_000,
                    dailyLimit = 15,
                )
            },
        ),

        RuleTemplate(
            title = "Send a WhatsApp message on a schedule",
            summary = "Opens a chat by phone number every day at a set time using a wa.me link.",
            category = "WhatsApp",
            requiresPackage = KnownPackages.WHATSAPP,
            build = {
                Rule(
                    name = "Daily WhatsApp message",
                    trigger = TriggerSpec.Schedule(hour = 9, minute = 0),
                    actions = listOf(
                        ActionSpec.OpenUrl(
                            url = "https://wa.me/1234567890?text={{date|url}}%20check-in",
                            packageName = KnownPackages.WHATSAPP,
                        ),
                        ActionSpec.Delay(3_000),
                        ActionSpec.ClickDescription("Send", timeoutMs = 10_000),
                    ),
                )
            },
        ),

        RuleTemplate(
            title = "Forward WhatsApp messages from one person",
            summary = "Relays messages from a named contact to a webhook of your choice.",
            category = "WhatsApp",
            build = {
                Rule(
                    name = "Forward WhatsApp from contact",
                    trigger = TriggerSpec.Notification(
                        packageNames = listOf(KnownPackages.WHATSAPP),
                        title = MatchSpec(MatchMode.CONTAINS, "NAME HERE"),
                    ),
                    actions = listOf(
                        ActionSpec.HttpRequest(
                            url = "https://example.com/hook",
                            body = """{"from":"{{title|json}}","text":"{{text|json}}"}""",
                        ),
                    ),
                )
            },
        ),
    )

    // ---- X ---------------------------------------------------------------

    private val x = listOf(
        RuleTemplate(
            title = "Post to X on a schedule",
            summary = "Publishes a daily post at a fixed time through the X app.",
            category = "X",
            requiresPackage = KnownPackages.X,
            build = {
                Rule(
                    name = "Daily X post",
                    trigger = TriggerSpec.Schedule(hour = 10, minute = 0),
                    actions = listOf(
                        ActionSpec.ShareText(KnownPackages.X, "Good morning. {{date}}"),
                        ActionSpec.Delay(3_000),
                        ActionSpec.ClickText("Post", exact = true, timeoutMs = 10_000),
                    ),
                )
            },
        ),

        RuleTemplate(
            title = "Like the first posts in the X timeline",
            summary = "Opens X and likes the first few posts, scrolling between each.",
            category = "X",
            requiresPackage = KnownPackages.X,
            build = {
                Rule(
                    name = "X timeline likes",
                    trigger = TriggerSpec.Manual,
                    actions = listOf(
                        ActionSpec.LaunchApp(KnownPackages.X),
                        ActionSpec.WaitForApp(KnownPackages.X, 10_000),
                        ActionSpec.Delay(2_500),
                        ActionSpec.Repeat(5),
                        ActionSpec.ClickDescription("Like", timeoutMs = 5_000),
                        ActionSpec.Delay(1_200),
                        ActionSpec.Scroll(ScrollDirection.FORWARD),
                        ActionSpec.Delay(1_200),
                        ActionSpec.EndRepeat,
                        ActionSpec.Global(GlobalActionType.HOME),
                    ),
                )
            },
        ),

        RuleTemplate(
            title = "Alert me when someone mentions me on X",
            summary = "Turns an X notification into a spoken alert and a vibration.",
            category = "X",
            build = {
                Rule(
                    name = "X mention alert",
                    trigger = TriggerSpec.Notification(
                        packageNames = listOf(KnownPackages.X),
                        text = MatchSpec(MatchMode.CONTAINS, "mentioned"),
                    ),
                    actions = listOf(
                        ActionSpec.Vibrate(500),
                        ActionSpec.Speak("You were mentioned on X"),
                    ),
                    cooldownMs = 30_000,
                )
            },
        ),
    )

    // ---- Instagram -------------------------------------------------------

    private val instagram = listOf(
        RuleTemplate(
            title = "Share text to Instagram",
            summary = "Hands a message to Instagram's share flow. Instagram expects media, " +
                "so pair this with a screenshot step for a full post.",
            category = "Instagram",
            requiresPackage = KnownPackages.INSTAGRAM,
            build = {
                Rule(
                    name = "Share to Instagram",
                    trigger = TriggerSpec.Manual,
                    actions = listOf(
                        ActionSpec.ShareText(KnownPackages.INSTAGRAM, "{{text}}"),
                        ActionSpec.Delay(3_000),
                    ),
                )
            },
        ),

        RuleTemplate(
            title = "Open a specific Instagram profile",
            summary = "Jumps straight to a profile using an Instagram deep link.",
            category = "Instagram",
            requiresPackage = KnownPackages.INSTAGRAM,
            build = {
                Rule(
                    name = "Open Instagram profile",
                    trigger = TriggerSpec.Manual,
                    actions = listOf(
                        ActionSpec.OpenUrl(
                            url = "https://www.instagram.com/USERNAME/",
                            packageName = KnownPackages.INSTAGRAM,
                        ),
                    ),
                )
            },
        ),

        RuleTemplate(
            title = "Limit Instagram to 20 minutes a day",
            summary = "When Instagram opens outside your allowed window, it closes again " +
                "and tells you why.",
            category = "Instagram",
            build = {
                Rule(
                    name = "Instagram time limit",
                    trigger = TriggerSpec.AppOpened(KnownPackages.INSTAGRAM),
                    conditions = listOf(
                        ConditionSpec.TimeWindow(startMinutes = 0, endMinutes = 18 * 60),
                    ),
                    actions = listOf(
                        ActionSpec.Toast("Instagram is off limits until 18:00"),
                        ActionSpec.Delay(400),
                        ActionSpec.Global(GlobalActionType.HOME),
                    ),
                    cooldownMs = 3_000,
                )
            },
        ),
    )

    // ---- Facebook --------------------------------------------------------

    private val facebook = listOf(
        RuleTemplate(
            title = "Post text to Facebook",
            summary = "Opens the Facebook composer pre-filled through the share sheet.",
            category = "Facebook",
            requiresPackage = KnownPackages.FACEBOOK,
            build = {
                Rule(
                    name = "Post to Facebook",
                    trigger = TriggerSpec.Manual,
                    actions = listOf(
                        ActionSpec.ShareText(KnownPackages.FACEBOOK, "{{text}}"),
                        ActionSpec.Delay(3_000),
                        ActionSpec.ClickText("Post", exact = true, timeoutMs = 10_000),
                    ),
                )
            },
        ),

        RuleTemplate(
            title = "Relay Telegram posts to Facebook",
            summary = "Sends every Telegram channel message into the Facebook composer.",
            category = "Facebook",
            requiresPackage = KnownPackages.FACEBOOK,
            build = {
                Rule(
                    name = "Telegram to Facebook",
                    trigger = TriggerSpec.Notification(
                        packageNames = listOf(KnownPackages.TELEGRAM),
                    ),
                    actions = listOf(
                        ActionSpec.ShareText(KnownPackages.FACEBOOK, "{{text}}"),
                        ActionSpec.Delay(3_500),
                        ActionSpec.ClickText("Post", exact = true, timeoutMs = 10_000),
                    ),
                    cooldownMs = 20_000,
                )
            },
        ),

        RuleTemplate(
            title = "Mute Messenger notifications during work",
            summary = "Dismisses Messenger alerts inside working hours by pressing back.",
            category = "Facebook",
            build = {
                Rule(
                    name = "Quiet Messenger at work",
                    trigger = TriggerSpec.Notification(
                        packageNames = listOf(KnownPackages.MESSENGER),
                    ),
                    conditions = listOf(
                        ConditionSpec.TimeWindow(startMinutes = 9 * 60, endMinutes = 17 * 60),
                        ConditionSpec.DayOfWeek(listOf(2, 3, 4, 5, 6)),
                    ),
                    actions = listOf(ActionSpec.SetVolume(VolumeStream.NOTIFICATION, 0)),
                    cooldownMs = 60_000,
                )
            },
        ),
    )

    // ---- YouTube ---------------------------------------------------------

    private val youtube = listOf(
        RuleTemplate(
            title = "Skip YouTube ads automatically",
            summary = "Watches for the skip button while YouTube is open and taps it.",
            category = "YouTube",
            requiresPackage = KnownPackages.YOUTUBE,
            build = {
                Rule(
                    name = "Skip YouTube ads",
                    trigger = TriggerSpec.ScreenText(
                        packageName = KnownPackages.YOUTUBE,
                        text = MatchSpec(MatchMode.CONTAINS, "Skip"),
                    ),
                    actions = listOf(
                        ActionSpec.ClickText("Skip", timeoutMs = 4_000),
                    ),
                    cooldownMs = 4_000,
                )
            },
        ),

        RuleTemplate(
            title = "Search YouTube for a topic",
            summary = "Opens YouTube straight into search results using a deep link.",
            category = "YouTube",
            requiresPackage = KnownPackages.YOUTUBE,
            build = {
                Rule(
                    name = "YouTube search",
                    trigger = TriggerSpec.Manual,
                    actions = listOf(
                        ActionSpec.OpenUrl(
                            url = "https://www.youtube.com/results?search_query={{text|url}}",
                            packageName = KnownPackages.YOUTUBE,
                        ),
                    ),
                )
            },
        ),

        RuleTemplate(
            title = "Pause media when headphones are unplugged",
            summary = "Sends a media pause the moment the jack or Bluetooth link drops.",
            category = "YouTube",
            build = {
                Rule(
                    name = "Pause on unplug",
                    trigger = TriggerSpec.Headset(plugged = false),
                    actions = listOf(ActionSpec.Media(MediaCommand.PLAY_PAUSE)),
                    cooldownMs = 2_000,
                )
            },
        ),
    )

    // ---- Browser ---------------------------------------------------------

    private val browser = listOf(
        RuleTemplate(
            title = "Open every link from a channel in the browser",
            summary = "Extracts a URL from an incoming message and opens it in Chrome.",
            category = "Browser",
            build = {
                Rule(
                    name = "Auto-open links",
                    trigger = TriggerSpec.Notification(
                        text = MatchSpec(MatchMode.CONTAINS, "http"),
                    ),
                    actions = listOf(
                        ActionSpec.ExtractRegex(
                            source = "{{text}}",
                            pattern = "(https?://\\S+)",
                            group = 1,
                            variable = "link",
                        ),
                        ActionSpec.OpenUrl("{{link}}", KnownPackages.CHROME),
                    ),
                    cooldownMs = 10_000,
                )
            },
        ),

        RuleTemplate(
            title = "Search the web for the clipboard",
            summary = "Bound to the Quick Settings tile: searches whatever you just copied.",
            category = "Browser",
            build = {
                Rule(
                    name = "Search clipboard",
                    trigger = TriggerSpec.QuickTile,
                    actions = listOf(
                        ActionSpec.ReadClipboard("clip"),
                        ActionSpec.OpenUrl(
                            url = "https://www.google.com/search?q={{clip|url}}",
                            packageName = KnownPackages.CHROME,
                        ),
                    ),
                )
            },
        ),

        RuleTemplate(
            title = "Read a page and forward the text",
            summary = "Reads everything on the current browser screen and posts it to a webhook.",
            category = "Browser",
            build = {
                Rule(
                    name = "Scrape current page",
                    trigger = TriggerSpec.Manual,
                    actions = listOf(
                        ActionSpec.ReadScreenText("screen"),
                        ActionSpec.HttpRequest(
                            url = "https://example.com/hook",
                            body = """{"screen":"{{screen|json}}"}""",
                        ),
                    ),
                )
            },
        ),
    )

    // ---- Device ----------------------------------------------------------

    private val device = listOf(
        RuleTemplate(
            title = "Silence the phone at night",
            summary = "Drops ring and notification volume to zero on a nightly schedule.",
            category = "Device",
            build = {
                Rule(
                    name = "Night mode",
                    trigger = TriggerSpec.Schedule(hour = 23, minute = 0),
                    actions = listOf(
                        ActionSpec.SetVolume(VolumeStream.RING, 0),
                        ActionSpec.SetVolume(VolumeStream.NOTIFICATION, 0),
                        ActionSpec.Notify("Night mode", "Phone silenced until morning"),
                    ),
                )
            },
        ),

        RuleTemplate(
            title = "Warn me when the battery is low",
            summary = "Speaks and vibrates below 15%, but only when not already charging.",
            category = "Device",
            build = {
                Rule(
                    name = "Low battery warning",
                    trigger = TriggerSpec.BatteryLevel(percent = 15, below = true),
                    conditions = listOf(ConditionSpec.Charging(charging = false)),
                    actions = listOf(
                        ActionSpec.Vibrate(600),
                        ActionSpec.Speak("Battery low, please charge the phone"),
                    ),
                    cooldownMs = 600_000,
                )
            },
        ),

        RuleTemplate(
            title = "Shake to run a rule",
            summary = "Shows how a physical gesture can start any automation.",
            category = "Device",
            build = {
                Rule(
                    name = "Shake shortcut",
                    trigger = TriggerSpec.Shake,
                    actions = listOf(
                        ActionSpec.Vibrate(120),
                        ActionSpec.Toast("Shake detected"),
                    ),
                    cooldownMs = 3_000,
                )
            },
        ),

        RuleTemplate(
            title = "Branch on the message content",
            summary = "Demonstrates If / Else: urgent messages get a spoken alert, " +
                "everything else only a toast.",
            category = "Device",
            build = {
                Rule(
                    name = "Urgent or not",
                    trigger = TriggerSpec.Notification(),
                    actions = listOf(
                        ActionSpec.If(
                            ConditionSpec.Text(
                                field = EventField.TEXT,
                                match = MatchSpec(MatchMode.CONTAINS, "urgent"),
                            )
                        ),
                        ActionSpec.Vibrate(800),
                        ActionSpec.Speak("Urgent message from {{title}}"),
                        ActionSpec.Else,
                        ActionSpec.Toast("{{app}}: {{text|trim:40}}"),
                        ActionSpec.EndIf,
                    ),
                    cooldownMs = 5_000,
                )
            },
        ),

        RuleTemplate(
            title = "Hourly heartbeat to a server",
            summary = "Interval trigger plus an HTTP call. Runs with the screen off.",
            category = "Device",
            build = {
                Rule(
                    name = "Hourly heartbeat",
                    trigger = TriggerSpec.Interval(minutes = 60),
                    actions = listOf(
                        ActionSpec.HttpRequest(
                            url = "https://example.com/heartbeat",
                            method = "POST",
                            body = """{"at":"{{datetime}}"}""",
                        ),
                    ),
                )
            },
        ),
    )
}
