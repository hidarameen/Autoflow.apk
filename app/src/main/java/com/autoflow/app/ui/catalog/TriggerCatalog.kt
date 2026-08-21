package com.autoflow.app.ui.catalog

import com.autoflow.app.data.MatchMode
import com.autoflow.app.data.MatchSpec
import com.autoflow.app.data.TriggerSpec

data class TriggerDef(
    val title: String,
    val category: String,
    val help: String = "",
    val fields: List<FieldDef> = emptyList(),
    val create: () -> TriggerSpec,
    val read: (TriggerSpec) -> Map<String, String> = { emptyMap() },
    val write: (Map<String, String>) -> TriggerSpec,
)

/** Same registry idea as [ActionCatalog], for the "when should this run" half of a rule. */
object TriggerCatalog {

    private fun matchFields(prefix: String, label: String) = listOf(
        FieldDef("${prefix}_mode", "$label test", FieldType.ENUM, options = MatchMode.entries.map { it.name }),
        FieldDef(prefix, label, FieldType.TEXT),
    )

    private fun readMatch(prefix: String, match: MatchSpec) = mapOf(
        "${prefix}_mode" to match.mode.name,
        prefix to match.value,
    )

    private fun writeMatch(values: Map<String, String>, prefix: String) = MatchSpec(
        mode = Fields.enum(values, "${prefix}_mode", MatchMode.ANY),
        value = Fields.text(values, prefix),
    )

    val all: List<TriggerDef> = listOf(

        // ---- WhatsApp --------------------------------------------------------

        TriggerDef(
            title = "WhatsApp message received",
            category = "WhatsApp",
            help = "Triggers when a WhatsApp message arrives from any contact or a specific sender.",
            fields = matchFields("sender", "Sender / Contact name") +
                matchFields("text", "Message body") +
                listOf(FieldDef("ignoreDuplicates", "Ignore repeated identical messages", FieldType.BOOL)),
            create = { TriggerSpec.WhatsAppMessage() },
            read = {
                (it as TriggerSpec.WhatsAppMessage).let { t ->
                    readMatch("sender", t.sender) +
                        readMatch("text", t.text) +
                        mapOf("ignoreDuplicates" to t.ignoreDuplicates.toString())
                }
            },
            write = { v ->
                TriggerSpec.WhatsAppMessage(
                    sender = writeMatch(v, "sender"),
                    text = writeMatch(v, "text"),
                    ignoreDuplicates = Fields.bool(v, "ignoreDuplicates", true),
                )
            },
        ),

        // ---- Telegram --------------------------------------------------------

        TriggerDef(
            title = "Telegram message received",
            category = "Telegram",
            help = "Triggers when a Telegram message arrives from a contact, group, or channel.",
            fields = matchFields("sender", "Sender / Group name") +
                matchFields("text", "Message body") +
                listOf(FieldDef("ignoreDuplicates", "Ignore repeated identical messages", FieldType.BOOL)),
            create = { TriggerSpec.TelegramMessage() },
            read = {
                (it as TriggerSpec.TelegramMessage).let { t ->
                    readMatch("sender", t.sender) +
                        readMatch("text", t.text) +
                        mapOf("ignoreDuplicates" to t.ignoreDuplicates.toString())
                }
            },
            write = { v ->
                TriggerSpec.TelegramMessage(
                    sender = writeMatch(v, "sender"),
                    text = writeMatch(v, "text"),
                    ignoreDuplicates = Fields.bool(v, "ignoreDuplicates", true),
                )
            },
        ),

        // ---- Instagram -------------------------------------------------------

        TriggerDef(
            title = "Instagram notification received",
            category = "Social Media",
            help = "Triggers on Instagram DMs, mentions, or alerts.",
            fields = matchFields("sender", "User / Sender") + matchFields("text", "Notification text"),
            create = { TriggerSpec.InstagramNotification() },
            read = {
                (it as TriggerSpec.InstagramNotification).let { t ->
                    readMatch("sender", t.sender) + readMatch("text", t.text)
                }
            },
            write = { v ->
                TriggerSpec.InstagramNotification(
                    sender = writeMatch(v, "sender"),
                    text = writeMatch(v, "text"),
                )
            },
        ),

        // ---- YouTube ---------------------------------------------------------

        TriggerDef(
            title = "YouTube notification received",
            category = "Social Media",
            help = "Triggers on new videos or community notifications from YouTube.",
            fields = matchFields("channel", "Channel name") + matchFields("title", "Video title / text"),
            create = { TriggerSpec.YouTubeNotification() },
            read = {
                (it as TriggerSpec.YouTubeNotification).let { t ->
                    readMatch("channel", t.channel) + readMatch("title", t.title)
                }
            },
            write = { v ->
                TriggerSpec.YouTubeNotification(
                    channel = writeMatch(v, "channel"),
                    title = writeMatch(v, "title"),
                )
            },
        ),

        // ---- X (Twitter) -----------------------------------------------------

        TriggerDef(
            title = "X (Twitter) notification received",
            category = "Social Media",
            help = "Triggers on mentions, replies, or notifications in X.",
            fields = matchFields("sender", "Username / Sender") + matchFields("text", "Tweet / content"),
            create = { TriggerSpec.XNotification() },
            read = {
                (it as TriggerSpec.XNotification).let { t ->
                    readMatch("sender", t.sender) + readMatch("text", t.text)
                }
            },
            write = { v ->
                TriggerSpec.XNotification(
                    sender = writeMatch(v, "sender"),
                    text = writeMatch(v, "text"),
                )
            },
        ),

        TriggerDef(
            title = "A notification arrives",
            category = "Apps",
            help = "The most useful trigger. Leave the app blank to watch every app.",
            fields = listOf(
                FieldDef("packageNames", "From app", FieldType.APP),
                FieldDef("ignoreDuplicates", "Ignore repeated identical messages", FieldType.BOOL),
            ) + matchFields("title", "Sender / title") + matchFields("text", "Message body"),
            create = { TriggerSpec.Notification() },
            read = {
                (it as TriggerSpec.Notification).let { t ->
                    mapOf(
                        "packageNames" to t.packageNames.joinToString(","),
                        "ignoreDuplicates" to t.ignoreDuplicates.toString(),
                    ) + readMatch("title", t.title) + readMatch("text", t.text)
                }
            },
            write = { v ->
                TriggerSpec.Notification(
                    packageNames = Fields.packages(v, "packageNames"),
                    title = writeMatch(v, "title"),
                    text = writeMatch(v, "text"),
                    ignoreDuplicates = Fields.bool(v, "ignoreDuplicates", true),
                )
            },
        ),

        TriggerDef(
            title = "A notification is dismissed",
            category = "Apps",
            fields = listOf(FieldDef("packageNames", "From app", FieldType.APP)),
            create = { TriggerSpec.NotificationRemoved() },
            read = {
                (it as TriggerSpec.NotificationRemoved).let { t ->
                    mapOf("packageNames" to t.packageNames.joinToString(","))
                }
            },
            write = { v -> TriggerSpec.NotificationRemoved(Fields.packages(v, "packageNames")) },
        ),

        TriggerDef(
            title = "Text appears on screen",
            category = "Apps",
            help = "Watches the live screen. Needs a text test, or it would fire constantly.",
            fields = listOf(FieldDef("packageName", "In app", FieldType.APP)) +
                matchFields("text", "Screen text"),
            create = { TriggerSpec.ScreenText(text = MatchSpec(MatchMode.CONTAINS, "")) },
            read = {
                (it as TriggerSpec.ScreenText).let { t ->
                    mapOf("packageName" to t.packageName) + readMatch("text", t.text)
                }
            },
            write = { v ->
                TriggerSpec.ScreenText(
                    packageName = Fields.text(v, "packageName"),
                    text = writeMatch(v, "text"),
                )
            },
        ),

        TriggerDef(
            title = "An app opens",
            category = "Apps",
            fields = listOf(FieldDef("packageName", "App", FieldType.APP)),
            create = { TriggerSpec.AppOpened() },
            read = { (it as TriggerSpec.AppOpened).let { t -> mapOf("packageName" to t.packageName) } },
            write = { v -> TriggerSpec.AppOpened(Fields.text(v, "packageName")) },
        ),

        TriggerDef(
            title = "An app closes",
            category = "Apps",
            fields = listOf(FieldDef("packageName", "App", FieldType.APP)),
            create = { TriggerSpec.AppClosed() },
            read = { (it as TriggerSpec.AppClosed).let { t -> mapOf("packageName" to t.packageName) } },
            write = { v -> TriggerSpec.AppClosed(Fields.text(v, "packageName")) },
        ),

        TriggerDef(
            title = "At a time of day",
            category = "Time",
            help = "Days use 1 for Sunday through 7 for Saturday.",
            fields = listOf(
                FieldDef("hour", "Hour (0-23)", FieldType.NUMBER),
                FieldDef("minute", "Minute", FieldType.NUMBER),
                FieldDef("days", "Days", FieldType.DAYS),
            ),
            create = { TriggerSpec.Schedule() },
            read = {
                (it as TriggerSpec.Schedule).let { t ->
                    mapOf(
                        "hour" to t.hour.toString(),
                        "minute" to t.minute.toString(),
                        "days" to Fields.daysToString(t.days),
                    )
                }
            },
            write = { v ->
                TriggerSpec.Schedule(
                    hour = Fields.int(v, "hour", 9).coerceIn(0, 23),
                    minute = Fields.int(v, "minute", 0).coerceIn(0, 59),
                    days = Fields.days(v, "days", listOf(1, 2, 3, 4, 5, 6, 7)),
                )
            },
        ),

        TriggerDef(
            title = "Every N minutes",
            category = "Time",
            help = "The system batches alarms in Doze, so very short intervals may drift.",
            fields = listOf(FieldDef("minutes", "Minutes", FieldType.NUMBER)),
            create = { TriggerSpec.Interval() },
            read = { (it as TriggerSpec.Interval).let { t -> mapOf("minutes" to t.minutes.toString()) } },
            write = { v -> TriggerSpec.Interval(Fields.int(v, "minutes", 30).coerceAtLeast(1)) },
        ),

        TriggerDef(
            title = "An SMS arrives",
            category = "Messaging",
            fields = matchFields("from", "Sender") + matchFields("body", "Message body"),
            create = { TriggerSpec.SmsReceived() },
            read = {
                (it as TriggerSpec.SmsReceived).let { t ->
                    readMatch("from", t.from) + readMatch("body", t.body)
                }
            },
            write = { v ->
                TriggerSpec.SmsReceived(writeMatch(v, "from"), writeMatch(v, "body"))
            },
        ),

        TriggerDef(
            title = "The screen turns on",
            category = "Device",
            create = { TriggerSpec.ScreenOn },
            write = { TriggerSpec.ScreenOn },
        ),

        TriggerDef(
            title = "The screen turns off",
            category = "Device",
            create = { TriggerSpec.ScreenOff },
            write = { TriggerSpec.ScreenOff },
        ),

        TriggerDef(
            title = "The device is unlocked",
            category = "Device",
            create = { TriggerSpec.DeviceUnlocked },
            write = { TriggerSpec.DeviceUnlocked },
        ),

        TriggerDef(
            title = "A charger is connected",
            category = "Device",
            create = { TriggerSpec.PowerConnected },
            write = { TriggerSpec.PowerConnected },
        ),

        TriggerDef(
            title = "A charger is disconnected",
            category = "Device",
            create = { TriggerSpec.PowerDisconnected },
            write = { TriggerSpec.PowerDisconnected },
        ),

        TriggerDef(
            title = "The battery passes a level",
            category = "Device",
            fields = listOf(
                FieldDef("percent", "Percent", FieldType.NUMBER),
                FieldDef("below", "Trigger when below", FieldType.BOOL),
            ),
            create = { TriggerSpec.BatteryLevel() },
            read = {
                (it as TriggerSpec.BatteryLevel).let { t ->
                    mapOf("percent" to t.percent.toString(), "below" to t.below.toString())
                }
            },
            write = { v ->
                TriggerSpec.BatteryLevel(
                    percent = Fields.int(v, "percent", 20).coerceIn(1, 100),
                    below = Fields.bool(v, "below", true),
                )
            },
        ),

        TriggerDef(
            title = "Wi-Fi connects",
            category = "Device",
            create = { TriggerSpec.WifiConnected },
            write = { TriggerSpec.WifiConnected },
        ),

        TriggerDef(
            title = "Wi-Fi disconnects",
            category = "Device",
            create = { TriggerSpec.WifiDisconnected },
            write = { TriggerSpec.WifiDisconnected },
        ),

        TriggerDef(
            title = "Headphones plugged or unplugged",
            category = "Device",
            fields = listOf(FieldDef("plugged", "Trigger when plugged in", FieldType.BOOL)),
            create = { TriggerSpec.Headset() },
            read = { (it as TriggerSpec.Headset).let { t -> mapOf("plugged" to t.plugged.toString()) } },
            write = { v -> TriggerSpec.Headset(Fields.bool(v, "plugged", true)) },
        ),

        TriggerDef(
            title = "The clipboard changes",
            category = "Device",
            help = "Android 10+ hides clipboard content from background apps, so {{text}} " +
                "may be empty. The trigger still fires.",
            create = { TriggerSpec.ClipboardChanged },
            write = { TriggerSpec.ClipboardChanged },
        ),

        TriggerDef(
            title = "An app is installed",
            category = "Device",
            create = { TriggerSpec.AppInstalled },
            write = { TriggerSpec.AppInstalled },
        ),

        TriggerDef(
            title = "An app is uninstalled",
            category = "Device",
            create = { TriggerSpec.AppUninstalled },
            write = { TriggerSpec.AppUninstalled },
        ),

        TriggerDef(
            title = "The device finishes booting",
            category = "Device",
            create = { TriggerSpec.BootCompleted },
            write = { TriggerSpec.BootCompleted },
        ),

        TriggerDef(
            title = "The device is shaken",
            category = "Device",
            create = { TriggerSpec.Shake },
            write = { TriggerSpec.Shake },
        ),

        TriggerDef(
            title = "The Quick Settings tile is tapped",
            category = "Device",
            help = "Add AutoFlow's tile to your shade to use this.",
            create = { TriggerSpec.QuickTile },
            write = { TriggerSpec.QuickTile },
        ),

        TriggerDef(
            title = "Only when I tap Run",
            category = "Manual",
            create = { TriggerSpec.Manual },
            write = { TriggerSpec.Manual },
        ),
    )

    val categories: List<String> = all.map { it.category }.distinct()

    fun defFor(trigger: TriggerSpec): TriggerDef =
        all.first { it.create()::class == trigger::class }
}
