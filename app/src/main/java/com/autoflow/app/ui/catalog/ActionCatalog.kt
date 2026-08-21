package com.autoflow.app.ui.catalog

import com.autoflow.app.data.ActionSpec
import com.autoflow.app.data.GlobalActionType
import com.autoflow.app.data.MediaCommand
import com.autoflow.app.data.ScrollDirection
import com.autoflow.app.data.SettingsPanel
import com.autoflow.app.data.TextFieldTarget
import com.autoflow.app.data.VolumeStream

/**
 * Describes every action as data: its label, its editable fields, and how to read and write
 * a spec from a flat string map.
 *
 * The editor renders straight from this, so adding an action means adding one entry here
 * rather than another hand-written dialog.
 */
data class ActionDef(
    val title: String,
    val category: String,
    val help: String = "",
    val fields: List<FieldDef> = emptyList(),
    val create: () -> ActionSpec,
    val read: (ActionSpec) -> Map<String, String> = { emptyMap() },
    val write: (ActionSpec, Map<String, String>) -> ActionSpec,
)

object ActionCatalog {

    private val timeout = FieldDef("timeoutMs", "Timeout (ms)", FieldType.NUMBER)

    val all: List<ActionDef> = listOf(

        // ---- WhatsApp --------------------------------------------------------

        ActionDef(
            title = "WhatsApp: Send message to number",
            category = "WhatsApp",
            help = "Opens chat with phone number (with country code, e.g. +9665...) and sends the message.",
            fields = listOf(
                FieldDef("phone", "Phone number (+country code)", FieldType.TEXT),
                FieldDef("message", "Message text (supports {{variables}})", FieldType.TEXT),
            ),
            create = { ActionSpec.WhatsAppSend() },
            read = {
                (it as ActionSpec.WhatsAppSend).let { a ->
                    mapOf("phone" to a.phone, "message" to a.message)
                }
            },
            write = { _, v ->
                ActionSpec.WhatsAppSend(
                    phone = Fields.text(v, "phone"),
                    message = Fields.text(v, "message"),
                )
            },
        ),

        ActionDef(
            title = "WhatsApp: Quick reply to chat",
            category = "WhatsApp",
            help = "Opens the incoming chat from {{title}} and types/sends the reply automatically.",
            fields = listOf(
                FieldDef("message", "Reply text (supports {{variables}})", FieldType.TEXT),
            ),
            create = { ActionSpec.WhatsAppReply() },
            read = {
                (it as ActionSpec.WhatsAppReply).let { a ->
                    mapOf("message" to a.message)
                }
            },
            write = { _, v ->
                ActionSpec.WhatsAppReply(
                    message = Fields.text(v, "message"),
                )
            },
        ),

        ActionDef(
            title = "WhatsApp: Forward received message to number",
            category = "WhatsApp",
            help = "Relays the incoming notification message {{text}} to a chosen phone number.",
            fields = listOf(
                FieldDef("phone", "Target phone number (+country code)", FieldType.TEXT),
            ),
            create = { ActionSpec.WhatsAppForward() },
            read = {
                (it as ActionSpec.WhatsAppForward).let { a ->
                    mapOf("phone" to a.phone)
                }
            },
            write = { _, v ->
                ActionSpec.WhatsAppForward(
                    phone = Fields.text(v, "phone"),
                )
            },
        ),

        // ---- Telegram --------------------------------------------------------

        ActionDef(
            title = "Telegram: Quick reply to chat",
            category = "Telegram",
            help = "Opens Telegram and replies to the sender from the trigger event.",
            fields = listOf(
                FieldDef("message", "Reply text (supports {{variables}})", FieldType.TEXT),
            ),
            create = { ActionSpec.TelegramReply() },
            read = {
                (it as ActionSpec.TelegramReply).let { a ->
                    mapOf("message" to a.message)
                }
            },
            write = { _, v ->
                ActionSpec.TelegramReply(
                    message = Fields.text(v, "message"),
                )
            },
        ),

        ActionDef(
            title = "Telegram: Send via Bot API",
            category = "Telegram",
            help = "Sends a message via Telegram Bot API silently in the background over HTTPS.",
            fields = listOf(
                FieldDef("botToken", "Bot Token", FieldType.TEXT),
                FieldDef("chatId", "Chat ID / Channel ID", FieldType.TEXT),
                FieldDef("message", "Message text", FieldType.TEXT),
            ),
            create = { ActionSpec.TelegramSend() },
            read = {
                (it as ActionSpec.TelegramSend).let { a ->
                    mapOf("botToken" to a.botToken, "chatId" to a.chatId, "message" to a.message)
                }
            },
            write = { _, v ->
                ActionSpec.TelegramSend(
                    botToken = Fields.text(v, "botToken"),
                    chatId = Fields.text(v, "chatId"),
                    message = Fields.text(v, "message"),
                )
            },
        ),

        // ---- Social Media ----------------------------------------------------

        ActionDef(
            title = "Instagram: Open DM with user",
            category = "Social Media",
            help = "Opens direct messages in Instagram with the given username.",
            fields = listOf(
                FieldDef("username", "Instagram username", FieldType.TEXT),
            ),
            create = { ActionSpec.InstagramDirect() },
            read = {
                (it as ActionSpec.InstagramDirect).let { a ->
                    mapOf("username" to a.username)
                }
            },
            write = { _, v ->
                ActionSpec.InstagramDirect(
                    username = Fields.text(v, "username"),
                )
            },
        ),

        ActionDef(
            title = "YouTube: Open video or search",
            category = "Social Media",
            help = "Opens a YouTube URL or performs a search query in the YouTube app.",
            fields = listOf(
                FieldDef("queryOrUrl", "Video URL or search query", FieldType.TEXT),
            ),
            create = { ActionSpec.YouTubeOpen() },
            read = {
                (it as ActionSpec.YouTubeOpen).let { a ->
                    mapOf("queryOrUrl" to a.queryOrUrl)
                }
            },
            write = { _, v ->
                ActionSpec.YouTubeOpen(
                    queryOrUrl = Fields.text(v, "queryOrUrl"),
                )
            },
        ),

        ActionDef(
            title = "X (Twitter): Post a tweet",
            category = "Social Media",
            help = "Shares and posts a tweet to the X app.",
            fields = listOf(
                FieldDef("tweet", "Tweet text (supports {{variables}})", FieldType.TEXT),
            ),
            create = { ActionSpec.XPost() },
            read = {
                (it as ActionSpec.XPost).let { a ->
                    mapOf("tweet" to a.tweet)
                }
            },
            write = { _, v ->
                ActionSpec.XPost(
                    tweet = Fields.text(v, "tweet"),
                )
            },
        ),

        // ---- Launching ---------------------------------------------------

        ActionDef(
            title = "Open an app",
            category = "Launch",
            fields = listOf(FieldDef("packageName", "App", FieldType.APP)),
            create = { ActionSpec.LaunchApp() },
            read = { (it as ActionSpec.LaunchApp).let { a -> mapOf("packageName" to a.packageName) } },
            write = { _, v -> ActionSpec.LaunchApp(Fields.text(v, "packageName")) },
        ),

        ActionDef(
            title = "Open a specific screen",
            category = "Launch",
            help = "Fully-qualified activity name, e.g. com.example.app.MainActivity.",
            fields = listOf(
                FieldDef("packageName", "App", FieldType.APP),
                FieldDef("activityClass", "Activity class", FieldType.TEXT),
            ),
            create = { ActionSpec.LaunchActivity() },
            read = {
                (it as ActionSpec.LaunchActivity).let { a ->
                    mapOf("packageName" to a.packageName, "activityClass" to a.activityClass)
                }
            },
            write = { _, v ->
                ActionSpec.LaunchActivity(Fields.text(v, "packageName"), Fields.text(v, "activityClass"))
            },
        ),

        ActionDef(
            title = "Open a URL",
            category = "Launch",
            help = "Leave the app blank to use the default browser. Deep links work here too.",
            fields = listOf(
                FieldDef("url", "URL", FieldType.TEXT),
                FieldDef("packageName", "Open in", FieldType.APP),
            ),
            create = { ActionSpec.OpenUrl() },
            read = {
                (it as ActionSpec.OpenUrl).let { a ->
                    mapOf("url" to a.url, "packageName" to a.packageName)
                }
            },
            write = { _, v -> ActionSpec.OpenUrl(Fields.text(v, "url"), Fields.text(v, "packageName")) },
        ),

        // ---- Screen interaction ------------------------------------------

        ActionDef(
            title = "Tap text on screen",
            category = "Screen",
            help = "The most portable way to tap. Matches labels and content descriptions.",
            fields = listOf(
                FieldDef("text", "Text", FieldType.TEXT),
                FieldDef("exact", "Exact match", FieldType.BOOL),
                timeout,
            ),
            create = { ActionSpec.ClickText() },
            read = {
                (it as ActionSpec.ClickText).let { a ->
                    mapOf(
                        "text" to a.text,
                        "exact" to a.exact.toString(),
                        "timeoutMs" to a.timeoutMs.toString(),
                    )
                }
            },
            write = { _, v ->
                ActionSpec.ClickText(
                    text = Fields.text(v, "text"),
                    exact = Fields.bool(v, "exact", false),
                    timeoutMs = Fields.long(v, "timeoutMs", 8_000),
                )
            },
        ),

        ActionDef(
            title = "Long-press text",
            category = "Screen",
            fields = listOf(
                FieldDef("text", "Text", FieldType.TEXT),
                FieldDef("exact", "Exact match", FieldType.BOOL),
                timeout,
            ),
            create = { ActionSpec.LongClickText() },
            read = {
                (it as ActionSpec.LongClickText).let { a ->
                    mapOf(
                        "text" to a.text,
                        "exact" to a.exact.toString(),
                        "timeoutMs" to a.timeoutMs.toString(),
                    )
                }
            },
            write = { _, v ->
                ActionSpec.LongClickText(
                    text = Fields.text(v, "text"),
                    exact = Fields.bool(v, "exact", false),
                    timeoutMs = Fields.long(v, "timeoutMs", 8_000),
                )
            },
        ),

        ActionDef(
            title = "Tap by content description",
            category = "Screen",
            help = "Icons usually have no visible text but do have a description, e.g. Send.",
            fields = listOf(FieldDef("description", "Description", FieldType.TEXT), timeout),
            create = { ActionSpec.ClickDescription() },
            read = {
                (it as ActionSpec.ClickDescription).let { a ->
                    mapOf("description" to a.description, "timeoutMs" to a.timeoutMs.toString())
                }
            },
            write = { _, v ->
                ActionSpec.ClickDescription(
                    description = Fields.text(v, "description"),
                    timeoutMs = Fields.long(v, "timeoutMs", 8_000),
                )
            },
        ),

        ActionDef(
            title = "Tap by view id",
            category = "Screen",
            help = "Find ids with Layout Inspector. The short form after the slash also works.",
            fields = listOf(FieldDef("viewId", "View id", FieldType.TEXT), timeout),
            create = { ActionSpec.ClickViewId() },
            read = {
                (it as ActionSpec.ClickViewId).let { a ->
                    mapOf("viewId" to a.viewId, "timeoutMs" to a.timeoutMs.toString())
                }
            },
            write = { _, v ->
                ActionSpec.ClickViewId(Fields.text(v, "viewId"), Fields.long(v, "timeoutMs", 8_000))
            },
        ),

        ActionDef(
            title = "Type text into a field",
            category = "Screen",
            help = "FIRST_EDITABLE suits most composers. Use BY_HINT when a screen has several fields.",
            fields = listOf(
                FieldDef("text", "Text to type", FieldType.MULTILINE),
                FieldDef(
                    "target", "Find the field by", FieldType.ENUM,
                    options = TextFieldTarget.entries.map { it.name },
                ),
                FieldDef("selector", "Hint or view id", FieldType.TEXT),
                timeout,
            ),
            create = { ActionSpec.SetText() },
            read = {
                (it as ActionSpec.SetText).let { a ->
                    mapOf(
                        "text" to a.text,
                        "target" to a.target.name,
                        "selector" to a.selector,
                        "timeoutMs" to a.timeoutMs.toString(),
                    )
                }
            },
            write = { _, v ->
                ActionSpec.SetText(
                    text = Fields.text(v, "text"),
                    target = Fields.enum(v, "target", TextFieldTarget.FIRST_EDITABLE),
                    selector = Fields.text(v, "selector"),
                    timeoutMs = Fields.long(v, "timeoutMs", 8_000),
                )
            },
        ),

        ActionDef(
            title = "Wait for text to appear",
            category = "Screen",
            fields = listOf(FieldDef("text", "Text", FieldType.TEXT), timeout),
            create = { ActionSpec.WaitForText() },
            read = {
                (it as ActionSpec.WaitForText).let { a ->
                    mapOf("text" to a.text, "timeoutMs" to a.timeoutMs.toString())
                }
            },
            write = { _, v ->
                ActionSpec.WaitForText(Fields.text(v, "text"), Fields.long(v, "timeoutMs", 10_000))
            },
        ),

        ActionDef(
            title = "Wait for an app to open",
            category = "Screen",
            help = "More reliable than a fixed delay after launching an app.",
            fields = listOf(FieldDef("packageName", "App", FieldType.APP), timeout),
            create = { ActionSpec.WaitForApp() },
            read = {
                (it as ActionSpec.WaitForApp).let { a ->
                    mapOf("packageName" to a.packageName, "timeoutMs" to a.timeoutMs.toString())
                }
            },
            write = { _, v ->
                ActionSpec.WaitForApp(
                    Fields.text(v, "packageName"),
                    Fields.long(v, "timeoutMs", 10_000),
                )
            },
        ),

        ActionDef(
            title = "Scroll",
            category = "Screen",
            fields = listOf(
                FieldDef(
                    "direction", "Direction", FieldType.ENUM,
                    options = ScrollDirection.entries.map { it.name },
                ),
                timeout,
            ),
            create = { ActionSpec.Scroll() },
            read = {
                (it as ActionSpec.Scroll).let { a ->
                    mapOf("direction" to a.direction.name, "timeoutMs" to a.timeoutMs.toString())
                }
            },
            write = { _, v ->
                ActionSpec.Scroll(
                    Fields.enum(v, "direction", ScrollDirection.FORWARD),
                    Fields.long(v, "timeoutMs", 5_000),
                )
            },
        ),

        ActionDef(
            title = "Tap coordinates",
            category = "Screen",
            help = "Pixel coordinates break on other screen sizes. Prefer tapping by text.",
            fields = listOf(
                FieldDef("x", "X", FieldType.NUMBER),
                FieldDef("y", "Y", FieldType.NUMBER),
            ),
            create = { ActionSpec.Tap() },
            read = {
                (it as ActionSpec.Tap).let { a -> mapOf("x" to a.x.toString(), "y" to a.y.toString()) }
            },
            write = { _, v -> ActionSpec.Tap(Fields.int(v, "x", 0), Fields.int(v, "y", 0)) },
        ),

        ActionDef(
            title = "Long-press coordinates",
            category = "Screen",
            fields = listOf(
                FieldDef("x", "X", FieldType.NUMBER),
                FieldDef("y", "Y", FieldType.NUMBER),
                FieldDef("durationMs", "Hold for (ms)", FieldType.NUMBER),
            ),
            create = { ActionSpec.LongPress() },
            read = {
                (it as ActionSpec.LongPress).let { a ->
                    mapOf(
                        "x" to a.x.toString(),
                        "y" to a.y.toString(),
                        "durationMs" to a.durationMs.toString(),
                    )
                }
            },
            write = { _, v ->
                ActionSpec.LongPress(
                    Fields.int(v, "x", 0),
                    Fields.int(v, "y", 0),
                    Fields.long(v, "durationMs", 700),
                )
            },
        ),

        ActionDef(
            title = "Swipe",
            category = "Screen",
            fields = listOf(
                FieldDef("x1", "Start X", FieldType.NUMBER),
                FieldDef("y1", "Start Y", FieldType.NUMBER),
                FieldDef("x2", "End X", FieldType.NUMBER),
                FieldDef("y2", "End Y", FieldType.NUMBER),
                FieldDef("durationMs", "Duration (ms)", FieldType.NUMBER),
            ),
            create = { ActionSpec.Swipe() },
            read = {
                (it as ActionSpec.Swipe).let { a ->
                    mapOf(
                        "x1" to a.x1.toString(),
                        "y1" to a.y1.toString(),
                        "x2" to a.x2.toString(),
                        "y2" to a.y2.toString(),
                        "durationMs" to a.durationMs.toString(),
                    )
                }
            },
            write = { _, v ->
                ActionSpec.Swipe(
                    Fields.int(v, "x1", 540),
                    Fields.int(v, "y1", 1600),
                    Fields.int(v, "x2", 540),
                    Fields.int(v, "y2", 600),
                    Fields.long(v, "durationMs", 300),
                )
            },
        ),

        ActionDef(
            title = "Press a system button",
            category = "Screen",
            fields = listOf(
                FieldDef(
                    "action", "Button", FieldType.ENUM,
                    options = GlobalActionType.entries.map { it.name },
                ),
            ),
            create = { ActionSpec.Global() },
            read = { (it as ActionSpec.Global).let { a -> mapOf("action" to a.action.name) } },
            write = { _, v -> ActionSpec.Global(Fields.enum(v, "action", GlobalActionType.BACK)) },
        ),

        ActionDef(
            title = "Take a screenshot",
            category = "Screen",
            help = "Requires Android 11 or newer.",
            create = { ActionSpec.Screenshot },
            write = { _, _ -> ActionSpec.Screenshot },
        ),

        // ---- Text and data -----------------------------------------------

        ActionDef(
            title = "Copy to clipboard",
            category = "Data",
            fields = listOf(FieldDef("text", "Text", FieldType.MULTILINE)),
            create = { ActionSpec.CopyToClipboard() },
            read = { (it as ActionSpec.CopyToClipboard).let { a -> mapOf("text" to a.text) } },
            write = { _, v -> ActionSpec.CopyToClipboard(Fields.text(v, "text")) },
        ),

        ActionDef(
            title = "Read the clipboard",
            category = "Data",
            help = "Stores the clip in a variable you can use later as {{name}}.",
            fields = listOf(FieldDef("variable", "Save into variable", FieldType.TEXT)),
            create = { ActionSpec.ReadClipboard() },
            read = { (it as ActionSpec.ReadClipboard).let { a -> mapOf("variable" to a.variable) } },
            write = { _, v -> ActionSpec.ReadClipboard(Fields.text(v, "variable", "clip")) },
        ),

        ActionDef(
            title = "Set a variable",
            category = "Data",
            fields = listOf(
                FieldDef("name", "Variable name", FieldType.TEXT),
                FieldDef("value", "Value", FieldType.MULTILINE),
            ),
            create = { ActionSpec.SetVariable() },
            read = {
                (it as ActionSpec.SetVariable).let { a -> mapOf("name" to a.name, "value" to a.value) }
            },
            write = { _, v -> ActionSpec.SetVariable(Fields.text(v, "name"), Fields.text(v, "value")) },
        ),

        ActionDef(
            title = "Extract with a regex",
            category = "Data",
            help = "Pulls one piece out of a message, e.g. (\\d{4,8}) for a verification code.",
            fields = listOf(
                FieldDef("source", "Source text", FieldType.TEXT),
                FieldDef("pattern", "Regex pattern", FieldType.TEXT),
                FieldDef("group", "Capture group", FieldType.NUMBER),
                FieldDef("variable", "Save into variable", FieldType.TEXT),
            ),
            create = { ActionSpec.ExtractRegex() },
            read = {
                (it as ActionSpec.ExtractRegex).let { a ->
                    mapOf(
                        "source" to a.source,
                        "pattern" to a.pattern,
                        "group" to a.group.toString(),
                        "variable" to a.variable,
                    )
                }
            },
            write = { _, v ->
                ActionSpec.ExtractRegex(
                    source = Fields.text(v, "source", "{{text}}"),
                    pattern = Fields.text(v, "pattern"),
                    group = Fields.int(v, "group", 1),
                    variable = Fields.text(v, "variable", "match"),
                )
            },
        ),

        ActionDef(
            title = "Read everything on screen",
            category = "Data",
            help = "Flattens all visible labels into a variable so a later step can search it.",
            fields = listOf(FieldDef("variable", "Save into variable", FieldType.TEXT)),
            create = { ActionSpec.ReadScreenText() },
            read = { (it as ActionSpec.ReadScreenText).let { a -> mapOf("variable" to a.variable) } },
            write = { _, v -> ActionSpec.ReadScreenText(Fields.text(v, "variable", "screen")) },
        ),

        // ---- Sending out -------------------------------------------------

        ActionDef(
            title = "Share text to an app",
            category = "Send",
            help = "Uses the Android share sheet. Usually the sturdiest way into another app.",
            fields = listOf(
                FieldDef("packageName", "Share to", FieldType.APP),
                FieldDef("text", "Text", FieldType.MULTILINE),
            ),
            create = { ActionSpec.ShareText() },
            read = {
                (it as ActionSpec.ShareText).let { a ->
                    mapOf("packageName" to a.packageName, "text" to a.text)
                }
            },
            write = { _, v ->
                ActionSpec.ShareText(Fields.text(v, "packageName"), Fields.text(v, "text"))
            },
        ),

        ActionDef(
            title = "Call an HTTP API",
            category = "Send",
            help = "Runs with the screen off and never breaks on a UI change. " +
                "Use {{text|json}} inside a JSON body.",
            fields = listOf(
                FieldDef("url", "URL", FieldType.TEXT),
                FieldDef(
                    "method", "Method", FieldType.ENUM,
                    options = listOf("POST", "GET", "PUT", "PATCH", "DELETE"),
                ),
                FieldDef("headers", "Headers, one per line", FieldType.MULTILINE),
                FieldDef("body", "Body", FieldType.MULTILINE),
                FieldDef("contentType", "Content type", FieldType.TEXT),
                FieldDef("saveResponseTo", "Save response into variable", FieldType.TEXT),
            ),
            create = { ActionSpec.HttpRequest() },
            read = {
                (it as ActionSpec.HttpRequest).let { a ->
                    mapOf(
                        "url" to a.url,
                        "method" to a.method,
                        "headers" to Fields.headersToString(a.headers),
                        "body" to a.body,
                        "contentType" to a.contentType,
                        "saveResponseTo" to a.saveResponseTo,
                    )
                }
            },
            write = { _, v ->
                ActionSpec.HttpRequest(
                    url = Fields.text(v, "url"),
                    method = Fields.text(v, "method", "POST"),
                    headers = Fields.headers(Fields.text(v, "headers")),
                    body = Fields.text(v, "body"),
                    contentType = Fields.text(v, "contentType", "application/json"),
                    saveResponseTo = Fields.text(v, "saveResponseTo"),
                )
            },
        ),

        ActionDef(
            title = "Send an SMS",
            category = "Send",
            help = "Needs the SEND_SMS permission, which you grant on first use.",
            fields = listOf(
                FieldDef("to", "Recipient number", FieldType.TEXT),
                FieldDef("body", "Message", FieldType.MULTILINE),
            ),
            create = { ActionSpec.SendSms() },
            read = {
                (it as ActionSpec.SendSms).let { a -> mapOf("to" to a.to, "body" to a.body) }
            },
            write = { _, v -> ActionSpec.SendSms(Fields.text(v, "to"), Fields.text(v, "body")) },
        ),

        ActionDef(
            title = "Open the dialer",
            category = "Send",
            help = "Fills in the number but does not place the call.",
            fields = listOf(FieldDef("number", "Number", FieldType.TEXT)),
            create = { ActionSpec.Dial() },
            read = { (it as ActionSpec.Dial).let { a -> mapOf("number" to a.number) } },
            write = { _, v -> ActionSpec.Dial(Fields.text(v, "number")) },
        ),

        // ---- Feedback ----------------------------------------------------

        ActionDef(
            title = "Show a notification",
            category = "Feedback",
            fields = listOf(
                FieldDef("title", "Title", FieldType.TEXT),
                FieldDef("message", "Message", FieldType.MULTILINE),
            ),
            create = { ActionSpec.Notify() },
            read = {
                (it as ActionSpec.Notify).let { a -> mapOf("title" to a.title, "message" to a.message) }
            },
            write = { _, v -> ActionSpec.Notify(Fields.text(v, "title"), Fields.text(v, "message")) },
        ),

        ActionDef(
            title = "Show a toast",
            category = "Feedback",
            fields = listOf(FieldDef("text", "Text", FieldType.TEXT)),
            create = { ActionSpec.Toast() },
            read = { (it as ActionSpec.Toast).let { a -> mapOf("text" to a.text) } },
            write = { _, v -> ActionSpec.Toast(Fields.text(v, "text")) },
        ),

        ActionDef(
            title = "Speak text aloud",
            category = "Feedback",
            fields = listOf(FieldDef("text", "Text", FieldType.MULTILINE)),
            create = { ActionSpec.Speak() },
            read = { (it as ActionSpec.Speak).let { a -> mapOf("text" to a.text) } },
            write = { _, v -> ActionSpec.Speak(Fields.text(v, "text")) },
        ),

        ActionDef(
            title = "Vibrate",
            category = "Feedback",
            fields = listOf(FieldDef("millis", "Milliseconds", FieldType.NUMBER)),
            create = { ActionSpec.Vibrate() },
            read = { (it as ActionSpec.Vibrate).let { a -> mapOf("millis" to a.millis.toString()) } },
            write = { _, v -> ActionSpec.Vibrate(Fields.long(v, "millis", 300)) },
        ),

        // ---- Device ------------------------------------------------------

        ActionDef(
            title = "Set the volume",
            category = "Device",
            fields = listOf(
                FieldDef(
                    "stream", "Stream", FieldType.ENUM,
                    options = VolumeStream.entries.map { it.name },
                ),
                FieldDef("percent", "Level (0-100)", FieldType.NUMBER),
            ),
            create = { ActionSpec.SetVolume() },
            read = {
                (it as ActionSpec.SetVolume).let { a ->
                    mapOf("stream" to a.stream.name, "percent" to a.percent.toString())
                }
            },
            write = { _, v ->
                ActionSpec.SetVolume(
                    Fields.enum(v, "stream", VolumeStream.MUSIC),
                    Fields.int(v, "percent", 50),
                )
            },
        ),

        ActionDef(
            title = "Control media playback",
            category = "Device",
            fields = listOf(
                FieldDef(
                    "command", "Command", FieldType.ENUM,
                    options = MediaCommand.entries.map { it.name },
                ),
            ),
            create = { ActionSpec.Media() },
            read = { (it as ActionSpec.Media).let { a -> mapOf("command" to a.command.name) } },
            write = { _, v -> ActionSpec.Media(Fields.enum(v, "command", MediaCommand.PLAY_PAUSE)) },
        ),

        ActionDef(
            title = "Wake the screen",
            category = "Device",
            create = { ActionSpec.WakeScreen },
            write = { _, _ -> ActionSpec.WakeScreen },
        ),

        ActionDef(
            title = "Open a settings panel",
            category = "Device",
            help = "Android no longer lets apps toggle Wi-Fi directly; this opens the panel instead.",
            fields = listOf(
                FieldDef(
                    "panel", "Panel", FieldType.ENUM,
                    options = SettingsPanel.entries.map { it.name },
                ),
            ),
            create = { ActionSpec.OpenSettingsPanel() },
            read = { (it as ActionSpec.OpenSettingsPanel).let { a -> mapOf("panel" to a.panel.name) } },
            write = { _, v ->
                ActionSpec.OpenSettingsPanel(Fields.enum(v, "panel", SettingsPanel.INTERNET))
            },
        ),

        // ---- Flow --------------------------------------------------------

        ActionDef(
            title = "Wait",
            category = "Flow",
            fields = listOf(FieldDef("millis", "Milliseconds", FieldType.NUMBER)),
            create = { ActionSpec.Delay() },
            read = { (it as ActionSpec.Delay).let { a -> mapOf("millis" to a.millis.toString()) } },
            write = { _, v -> ActionSpec.Delay(Fields.long(v, "millis", 1_000)) },
        ),

        ActionDef(
            title = "If",
            category = "Flow",
            help = "Steps between If and Else run only when the condition holds. " +
                "Close the block with End if.",
            create = { ActionSpec.If() },
            write = { original, _ -> original },
        ),

        ActionDef(
            title = "Else",
            category = "Flow",
            create = { ActionSpec.Else },
            write = { _, _ -> ActionSpec.Else },
        ),

        ActionDef(
            title = "End if",
            category = "Flow",
            create = { ActionSpec.EndIf },
            write = { _, _ -> ActionSpec.EndIf },
        ),

        ActionDef(
            title = "Repeat",
            category = "Flow",
            help = "Repeats every step up to the matching End repeat.",
            fields = listOf(FieldDef("times", "Times", FieldType.NUMBER)),
            create = { ActionSpec.Repeat() },
            read = { (it as ActionSpec.Repeat).let { a -> mapOf("times" to a.times.toString()) } },
            write = { _, v -> ActionSpec.Repeat(Fields.int(v, "times", 2)) },
        ),

        ActionDef(
            title = "End repeat",
            category = "Flow",
            create = { ActionSpec.EndRepeat },
            write = { _, _ -> ActionSpec.EndRepeat },
        ),

        ActionDef(
            title = "Stop this rule",
            category = "Flow",
            help = "Ends the run without recording a failure. Useful inside an If.",
            create = { ActionSpec.StopRule },
            write = { _, _ -> ActionSpec.StopRule },
        ),

        ActionDef(
            title = "Run another rule",
            category = "Flow",
            help = "Runs a rule by name and passes the same trigger event to it.",
            fields = listOf(FieldDef("ruleName", "Rule name", FieldType.TEXT)),
            create = { ActionSpec.RunRule() },
            read = { (it as ActionSpec.RunRule).let { a -> mapOf("ruleName" to a.ruleName) } },
            write = { _, v -> ActionSpec.RunRule(Fields.text(v, "ruleName")) },
        ),

        ActionDef(
            title = "Write a log note",
            category = "Flow",
            fields = listOf(FieldDef("message", "Message", FieldType.TEXT)),
            create = { ActionSpec.Log() },
            read = { (it as ActionSpec.Log).let { a -> mapOf("message" to a.message) } },
            write = { _, v -> ActionSpec.Log(Fields.text(v, "message")) },
        ),
    )

    val categories: List<String> = all.map { it.category }.distinct()

    /** Looks up the definition matching a concrete spec instance. */
    fun defFor(action: ActionSpec): ActionDef =
        all.first { it.create()::class == action::class }
}
