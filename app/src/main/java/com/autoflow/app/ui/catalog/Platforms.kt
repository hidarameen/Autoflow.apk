package com.autoflow.app.ui.catalog

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Tag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.autoflow.app.data.ActionSpec
import com.autoflow.app.data.GlobalActionType
import com.autoflow.app.data.MatchMode
import com.autoflow.app.data.MatchSpec
import com.autoflow.app.data.ScrollDirection
import com.autoflow.app.data.TextFieldTarget
import com.autoflow.app.data.TriggerSpec
import com.autoflow.app.util.KnownPackages

/**
 * Per-app trigger and action presets.
 *
 * These are deliberately NOT new engine primitives. Each entry compiles down to the same
 * generic [TriggerSpec] / [ActionSpec] vocabulary the engine already runs, pre-filled with
 * the matchers and step sequences that work for that specific app. That keeps one tested
 * matcher and one tested executor while still giving the user an app-shaped menu.
 *
 * The notification matchers encode how each app actually formats its notifications — e.g.
 * WhatsApp puts "Sender: message" in the body for group chats but only the message for a
 * one-to-one chat, which is what makes the group/direct split below possible.
 */

data class PlatformTrigger(
    val id: String,
    val title: String,
    val description: String,
    val fields: List<FieldDef> = emptyList(),
    val build: (Map<String, String>) -> TriggerSpec,
)

data class PlatformAction(
    val id: String,
    val title: String,
    val description: String,
    val fields: List<FieldDef> = emptyList(),
    /** May expand to several steps — "reply in chat" is type-then-send. */
    val build: (Map<String, String>) -> List<ActionSpec>,
)

data class Platform(
    val id: String,
    val name: String,
    val packageName: String,
    val color: Color,
    val icon: ImageVector,
    val triggers: List<PlatformTrigger>,
    val actions: List<PlatformAction>,
)

private fun contains(v: String) = MatchSpec(MatchMode.CONTAINS, v)
private fun exact(v: String) = MatchSpec(MatchMode.EQUALS, v)
private fun regex(v: String) = MatchSpec(MatchMode.REGEX, v)

private fun notif(
    pkg: String,
    title: MatchSpec = MatchSpec.ANY,
    text: MatchSpec = MatchSpec.ANY,
) = TriggerSpec.Notification(packageNames = listOf(pkg), title = title, text = text)

private val senderField = FieldDef("sender", "Sender name", FieldType.TEXT, "Exactly as it appears in the notification")
private val groupField = FieldDef("group", "Group name", FieldType.TEXT)
private val keywordField = FieldDef("keyword", "Keyword", FieldType.TEXT)
private val messageField = FieldDef("message", "Message", FieldType.MULTILINE, "Supports {{text}}, {{title}}, {{app}}")

/** Body pattern WhatsApp/Telegram use for group messages: "Sender: body". */
private const val GROUP_BODY = "^[^:]{1,40}:\\s"


/**
 * Entries that do NOT depend on notifications.
 *
 * The accessibility service reads message bubbles straight off the conversation screen, so
 * these keep working when the user has notifications for that app switched off. Pair the
 * scheduled "open a chat and read it" action with a time trigger to poll a conversation.
 */
private fun chatTriggers(pkg: String) = listOf(
    PlatformTrigger(
        "${pkg}_from_number",
        "Message from a phone number (unsaved contact)",
        "Matches the digits, so it works whether the sender shows as +967 779 788 716, " +
            "00967779788716 or a saved name for the same number.",
        listOf(FieldDef("number", "Phone number", FieldType.TEXT, "Any format")),
    ) {
        val number = Fields.text(it, "number")
        TriggerSpec.Notification(
            packageNames = listOf(pkg),
            title = if (number.isBlank()) MatchSpec.ANY else MatchSpec(MatchMode.PHONE, number),
        )
    },

    PlatformTrigger(
        "${pkg}_chat_any",
        "New message on screen (no notification needed)",
        "Reads the conversation directly. Works with notifications disabled, but only " +
            "while the chat is open — pair it with a scheduled \"open chat\" rule to poll.",
    ) { TriggerSpec.ChatMessage(packageNames = listOf(pkg)) },

    PlatformTrigger(
        "${pkg}_chat_from",
        "On-screen message from a person (no notification needed)",
        "Same as above, limited to one sender.",
        listOf(senderField),
    ) {
        val who = Fields.text(it, "sender")
        TriggerSpec.ChatMessage(
            packageNames = listOf(pkg),
            sender = if (who.isBlank()) MatchSpec.ANY else contains(who),
        )
    },

    PlatformTrigger(
        "${pkg}_chat_keyword",
        "On-screen message containing a keyword (no notification needed)",
        "",
        listOf(keywordField),
    ) {
        val word = Fields.text(it, "keyword")
        TriggerSpec.ChatMessage(
            packageNames = listOf(pkg),
            text = if (word.isBlank()) MatchSpec.ANY else contains(word),
        )
    },
)

private fun chatActions(pkg: String) = listOf(
    PlatformAction(
        "${pkg}_reply_notif",
        "Reply through the notification (screen can stay off)",
        "Uses the reply box built into the notification. Instant, never opens the app, and " +
            "works while the phone is locked.",
        listOf(messageField),
    ) {
        listOf(ActionSpec.ReplyToNotification(message = Fields.text(it, "message")))
    },

    PlatformAction(
        "${pkg}_open_read",
        "Open a chat and read its messages",
        "Brings the conversation on screen and stores what it finds in {{messages}}. " +
            "Put this on a schedule to collect messages without notifications.",
        listOf(
            FieldDef("contact", "Contact or group name", FieldType.TEXT, "Leave blank to read whatever is open"),
            FieldDef("limit", "How many messages", FieldType.NUMBER),
        ),
    ) {
        listOf(
            ActionSpec.OpenChat(packageName = pkg, contact = Fields.text(it, "contact")),
            ActionSpec.Delay(1_500),
            ActionSpec.ReadChat(variable = "messages", limit = Fields.int(it, "limit", 10)),
        )
    },

    PlatformAction(
        "${pkg}_read_only",
        "Read the messages already on screen",
        "Stores them in {{messages}}, {{messages_last}} and {{messages_count}}.",
        listOf(FieldDef("limit", "How many messages", FieldType.NUMBER)),
    ) {
        listOf(ActionSpec.ReadChat(variable = "messages", limit = Fields.int(it, "limit", 10)))
    },
)


/** Splits the comma-separated URI field the media presets share. */
private fun uriList(values: Map<String, String>, key: String): List<String> =
    Fields.text(values, key).split(",").map { it.trim() }.filter { it.isNotEmpty() }

private val mediaField =
    FieldDef("media", "Media URIs, comma separated", FieldType.MULTILINE,
        "content:// or file:// URIs. Several images post as an album.")

private val mediaTypeField =
    FieldDef("type", "Type", FieldType.ENUM,
        options = listOf("image/*", "video/*", "audio/*", "application/*", "*/*"))

/**
 * Media presets shared by every platform that accepts an Android share intent.
 *
 * [chatTarget] is WhatsApp's "jid" trick: passing <number>@s.whatsapp.net with its contact
 * picker component delivers straight into one conversation instead of asking the user to
 * choose a recipient.
 */
/**
 * "Forward whatever arrived" presets.
 *
 * These use {{media}} and {{text}} rather than fixed values, so one rule relays a text
 * message, a photo with a caption or a voice note without the user choosing a shape up
 * front. When the trigger carried no media the URI resolves to nothing and the step falls
 * back to a plain text share.
 */
private fun forwardActions(pkg: String, label: String, activity: String = "", chatTarget: Boolean = false) =
    buildList {
        add(
            PlatformAction(
                "${pkg}_forward_asis", "Forward the message as it arrived to $label",
                "Relays text, photo, video, voice or document exactly as received.",
                if (chatTarget) listOf(FieldDef("contact", "Contact or group name", FieldType.TEXT))
                else emptyList(),
            ) { values ->
                buildList {
                    add(
                        ActionSpec.ShareMedia(
                            packageName = pkg,
                            activityClass = activity,
                            text = "{{text}}",
                            mediaUris = listOf("{{media}}"),
                            // The real type of what arrived, so WhatsApp attaches the file
                            // instead of falling back to a text-only send.
                            mimeType = "{{media_type}}",
                        )
                    )
                    add(ActionSpec.Delay(3_500))
                    if (chatTarget) {
                        val who = Fields.text(values, "contact")
                        add(ActionSpec.ClickViewId("menuitem_search", timeoutMs = 8_000, label = "افتح البحث"))
                        add(ActionSpec.Delay(1_000))
                        add(ActionSpec.SetText(who, TextFieldTarget.BY_VIEW_ID, "search_src_text", 8_000, label = "اكتب الوجهة"))
                        add(ActionSpec.Delay(2_000))
                        // Match the row by name so it never picks a random first result;
                        // aborts the rule if the destination is not in the list.
                        add(ActionSpec.WaitForText(who, timeoutMs = 8_000))
                        add(ActionSpec.ClickText(who, exact = false, timeoutMs = 8_000, label = "اختر \"$who\""))
                        add(ActionSpec.Delay(1_500))
                        add(ActionSpec.ClickViewId("send", timeoutMs = 10_000, label = "إرسال"))
                        add(ActionSpec.Delay(2_500))
                        add(ActionSpec.IgnoreErrors(true))
                        // Media opens a composer with its own send button; text does not.
                        add(ActionSpec.ClickViewId("send", timeoutMs = 6_000, label = "إرسال (محرّر الوسائط)"))
                        add(ActionSpec.Delay(2_500))
                        // Channels ask "share to channel?" — button1 is "Continue".
                        add(ActionSpec.ClickViewId("button1", timeoutMs = 4_000, label = "متابعة (تأكيد القناة)"))
                        add(ActionSpec.Delay(1_500))
                        add(ActionSpec.ClickViewId("button1", timeoutMs = 3_000, label = "متابعة"))
                        add(ActionSpec.IgnoreErrors(false))
                    }
                }
            }
        )
    }

private fun mediaActions(
    pkg: String,
    label: String,
    activity: String = "",
    supportsChatTarget: Boolean = false,
) = buildList {
    add(
        PlatformAction(
            "${pkg}_media", "Send photos or video to $label",
            "One file sends a single item; several send an album where the app supports it.",
            listOf(messageField, mediaField, mediaTypeField),
        ) {
            listOf(
                ActionSpec.ShareMedia(
                    packageName = pkg,
                    activityClass = activity,
                    text = Fields.text(it, "message"),
                    mediaUris = uriList(it, "media"),
                    mimeType = Fields.text(it, "type", "image/*"),
                )
            )
        }
    )

    add(
        PlatformAction(
            "${pkg}_document", "Send a document to $label",
            "PDF, text, spreadsheet and so on.",
            listOf(messageField, mediaField),
        ) {
            listOf(
                ActionSpec.ShareMedia(
                    packageName = pkg,
                    activityClass = activity,
                    text = Fields.text(it, "message"),
                    mediaUris = uriList(it, "media"),
                    mimeType = "application/*",
                )
            )
        }
    )

    add(
        PlatformAction(
            "${pkg}_audio", "Send audio to $label",
            "Sends an audio file as an attachment.",
            listOf(messageField, mediaField),
        ) {
            listOf(
                ActionSpec.ShareMedia(
                    packageName = pkg,
                    activityClass = activity,
                    text = Fields.text(it, "message"),
                    mediaUris = uriList(it, "media"),
                    mimeType = "audio/*",
                )
            )
        }
    )

    if (supportsChatTarget) {
        add(
            PlatformAction(
                "${pkg}_media_to_contact",
                "Send media to a contact, fully automatic",
                "WhatsApp will not let another app open one chat directly any more (its " +
                    "ContactPicker is not exported), so this drives WhatsApp's own recipient " +
                    "list: it searches for the contact rather than hunting the visible rows, " +
                    "which is what makes it work with hundreds of chats.",
                listOf(
                    FieldDef("contact", "Contact name or number", FieldType.TEXT,
                        "Exactly as WhatsApp shows it"),
                    messageField,
                    mediaField,
                    mediaTypeField,
                ),
            ) {
                val who = Fields.text(it, "contact")
                listOf(
                    ActionSpec.ShareMedia(
                        packageName = pkg,
                        text = Fields.text(it, "message"),
                        mediaUris = uriList(it, "media"),
                        mimeType = Fields.text(it, "type", "image/*"),
                    ),
                    ActionSpec.Delay(3_500),
                    // Search first so the list holds one row no matter how many chats exist.
                    ActionSpec.ClickViewId("menuitem_search", timeoutMs = 8_000, label = "افتح البحث"),
                    ActionSpec.Delay(1_000),
                    ActionSpec.SetText(who, TextFieldTarget.BY_VIEW_ID, "search_src_text", 8_000, label = "اكتب الوجهة"),
                    ActionSpec.Delay(2_000),
                    // Match by name so a missing destination aborts instead of picking a
                    // random first row.
                    ActionSpec.WaitForText(who, timeoutMs = 8_000),
                    ActionSpec.ClickText(who, exact = false, timeoutMs = 8_000, label = "اختر \"$who\""),
                    ActionSpec.Delay(1_500),
                    ActionSpec.ClickViewId("send", timeoutMs = 10_000, label = "إرسال"),
                    ActionSpec.Delay(2_500),
                    ActionSpec.IgnoreErrors(true),
                    // Media opens a preview (DocumentPreviewActivity for files, the media
                    // composer for photos/video) with its own send button; text does not.
                    ActionSpec.ClickViewId("send", timeoutMs = 6_000, label = "إرسال (المعاينة)"),
                    ActionSpec.Delay(2_500),
                    // Channels/announcement groups ask to confirm — button1 is "Continue".
                    ActionSpec.ClickViewId("button1", timeoutMs = 4_000, label = "متابعة (تأكيد)"),
                    ActionSpec.Delay(1_500),
                    ActionSpec.ClickViewId("button1", timeoutMs = 3_000, label = "متابعة"),
                    ActionSpec.IgnoreErrors(false),
                )
            }
        )
    }
}

// ============================================================================
// Granular, single-gesture actions.
//
// Each of these is one tap / type / key press, carrying a human [label] so the
// step list reads "Tap “Send”" instead of "Tap id send". Chain them freely to
// script any screen by hand. They still compile to the same generic primitives
// the executor already runs.
// ============================================================================

/** A one-step "tap this view id" action with a friendly name. */
private fun tapId(id: String, name: String, viewId: String, timeout: Long = 6_000) =
    PlatformAction(id, "Tap “$name”", "Taps $viewId.") {
        listOf(ActionSpec.ClickViewId(viewId, timeout, label = "Tap “$name”"))
    }

/** A one-step "tap the icon whose accessibility label is X" action. */
private fun tapDesc(id: String, name: String, desc: String, timeout: Long = 6_000) =
    PlatformAction(id, "Tap “$name”", "Taps the icon labelled \"$desc\".") {
        listOf(ActionSpec.ClickDescription(desc, timeout, label = "Tap “$name”"))
    }

/** A one-step "tap the visible text X" action. */
private fun tapText(id: String, name: String, text: String, exact: Boolean = true, timeout: Long = 6_000) =
    PlatformAction(id, "Tap “$name”", "Taps the on-screen text \"$text\".") {
        listOf(ActionSpec.ClickText(text, exact, timeout, label = "Tap “$name”"))
    }

/** Universal UI micro-actions every platform gets on top of its presets. */
private fun uiActions(): List<PlatformAction> = listOf(
    PlatformAction(
        "ui_tap_text", "Tap a button or text",
        "Taps the first on-screen element showing this text — OK, Send, Next, a name…",
        listOf(
            FieldDef("text", "Text on the button", FieldType.TEXT, "e.g. OK, Send, NEXT"),
            FieldDef("exact", "Whole text must match", FieldType.BOOL),
        ),
    ) {
        listOf(
            ActionSpec.ClickText(
                Fields.text(it, "text"), Fields.bool(it, "exact", false), 8_000,
                label = "Tap \"${Fields.text(it, "text")}\"",
            )
        )
    },
    PlatformAction(
        "ui_tap_desc", "Tap an icon by its label",
        "Uses the icon's accessibility label — Search, Attach, More options, Voice call…",
        listOf(FieldDef("desc", "Icon label", FieldType.TEXT)),
    ) {
        listOf(ActionSpec.ClickDescription(Fields.text(it, "desc"), 8_000, label = "Tap ${Fields.text(it, "desc")}"))
    },
    PlatformAction(
        "ui_tap_id", "Tap a view by its id (advanced)",
        "The app's internal resource id, e.g. send, entry, fab.",
        listOf(
            FieldDef("id", "Resource id", FieldType.TEXT),
            FieldDef("name", "Show it in the list as", FieldType.TEXT),
        ),
    ) {
        val nm = Fields.text(it, "name").ifBlank { Fields.text(it, "id") }
        listOf(ActionSpec.ClickViewId(Fields.text(it, "id"), 8_000, label = "Tap $nm"))
    },
    PlatformAction(
        "ui_long_press_text", "Long-press text",
        "Opens the context menu on a message or list row.",
        listOf(FieldDef("text", "Text to hold", FieldType.TEXT)),
    ) {
        listOf(ActionSpec.LongClickText(Fields.text(it, "text"), false, 8_000, label = "Long-press \"${Fields.text(it, "text")}\""))
    },
    PlatformAction(
        "ui_type", "Type into the current box",
        "Types into the focused field, or the first one that accepts text.",
        listOf(messageField),
    ) {
        listOf(ActionSpec.SetText(Fields.text(it, "message"), TextFieldTarget.FIRST_EDITABLE, "", 8_000, label = "Type text"))
    },
    PlatformAction(
        "ui_type_id", "Type into a specific box",
        "Targets an input by its resource id (e.g. entry, search_input).",
        listOf(FieldDef("id", "Box id", FieldType.TEXT), messageField),
    ) {
        listOf(ActionSpec.SetText(Fields.text(it, "message"), TextFieldTarget.BY_VIEW_ID, Fields.text(it, "id"), 8_000, label = "Type into ${Fields.text(it, "id")}"))
    },
    PlatformAction(
        "ui_wait_text", "Wait until text appears",
        "Holds the sequence until this shows up, then continues.",
        listOf(
            FieldDef("text", "Text to wait for", FieldType.TEXT),
            FieldDef("seconds", "Give up after (seconds)", FieldType.NUMBER),
        ),
    ) {
        listOf(ActionSpec.WaitForText(Fields.text(it, "text"), Fields.long(it, "seconds", 10L) * 1000))
    },
    PlatformAction(
        "ui_wait", "Wait a moment",
        "A fixed pause — give the screen time to settle between taps.",
        listOf(FieldDef("ms", "Milliseconds", FieldType.NUMBER)),
    ) {
        listOf(ActionSpec.Delay(Fields.long(it, "ms", 1_000L)))
    },
    PlatformAction(
        "ui_confirm", "Tap OK / Allow / Continue",
        "Tries the usual confirm buttons in turn and skips the ones that are not there.",
    ) {
        listOf(
            ActionSpec.IgnoreErrors(true),
            ActionSpec.ClickText("OK", false, 2_000, label = "Tap OK"),
            ActionSpec.ClickText("ALLOW", false, 1_500, label = "Tap Allow"),
            ActionSpec.ClickText("Allow", false, 1_500, label = "Tap Allow"),
            ActionSpec.ClickText("Continue", false, 1_500, label = "Tap Continue"),
            ActionSpec.ClickText("NEXT", false, 1_500, label = "Tap Next"),
            ActionSpec.IgnoreErrors(false),
        )
    },
    PlatformAction("ui_back", "Press Back", "") { listOf(ActionSpec.Global(GlobalActionType.BACK)) },
    PlatformAction("ui_home", "Press Home", "") { listOf(ActionSpec.Global(GlobalActionType.HOME)) },
    PlatformAction("ui_recents", "Open Recent apps", "") { listOf(ActionSpec.Global(GlobalActionType.RECENTS)) },
    PlatformAction("ui_scroll_down", "Scroll down", "") { listOf(ActionSpec.Scroll(ScrollDirection.FORWARD)) },
    PlatformAction("ui_scroll_up", "Scroll up", "") { listOf(ActionSpec.Scroll(ScrollDirection.BACKWARD)) },
    PlatformAction(
        "ui_swipe_reply", "Swipe right to reply",
        "Swipes across the last bubble — the reply gesture in most chat apps.",
    ) { listOf(ActionSpec.Swipe(120, 1500, 720, 1500, 220)) },
    PlatformAction("ui_screenshot", "Take a screenshot", "Android 11+.") { listOf(ActionSpec.Screenshot) },
)

/** WhatsApp: every button on the chat list and the conversation screen. */
private fun whatsappTaps(): List<PlatformAction> = listOf(
    tapId("wa_t_newchat", "New chat", "fab"),
    tapId("wa_t_search", "Search", "menuitem_search"),
    PlatformAction(
        "wa_t_search_type", "Search for…", "Opens Search and types your text.",
        listOf(FieldDef("q", "Search text", FieldType.TEXT)),
    ) {
        listOf(
            ActionSpec.ClickViewId("menuitem_search", 6_000, label = "Tap “Search”"),
            ActionSpec.Delay(700),
            ActionSpec.SetText(Fields.text(it, "q"), TextFieldTarget.FIRST_EDITABLE, "", 6_000, label = "Type “${Fields.text(it, "q")}”"),
        )
    },
    PlatformAction(
        "wa_t_type", "Type in the message box", "Into WhatsApp's entry field.",
        listOf(messageField),
    ) {
        listOf(ActionSpec.SetText(Fields.text(it, "message"), TextFieldTarget.BY_VIEW_ID, "entry", 8_000, label = "Type message"))
    },
    tapId("wa_t_send", "Send", "send", timeout = 8_000),
    tapId("wa_t_attach", "Attach", "input_attach_button"),
    tapId("wa_t_camera", "Camera", "camera_btn"),
    tapId("wa_t_emoji", "Emoji", "emoji_picker_btn"),
    tapId("wa_t_open_info", "Open the chat's info", "conversation_contact_name"),
    tapDesc("wa_t_more", "⋮ menu", "More options"),
    tapDesc("wa_t_voice_call", "Voice call", "Voice call"),
    tapDesc("wa_t_video_call", "Video call", "Video call"),
    seqAction("wa_t_attach_gallery", "Attach → Gallery", "input_attach_button", "Gallery"),
    seqAction("wa_t_attach_document", "Attach → Document", "input_attach_button", "Document"),
    seqAction("wa_t_attach_contact", "Attach → Contact", "input_attach_button", "Contact"),
    seqAction("wa_t_attach_location", "Attach → Location", "input_attach_button", "Location"),
    tapText("wa_t_tab_chats", "Chats tab", "Chats"),
    tapText("wa_t_tab_updates", "Updates / Status tab", "Updates"),
    tapText("wa_t_tab_calls", "Calls tab", "Calls"),
    PlatformAction(
        "wa_t_msg_menu", "Long-press a message, then…",
        "Holds a message that contains your text and taps a menu button.",
        listOf(
            FieldDef("contains", "Message contains", FieldType.TEXT),
            FieldDef(
                "op", "Then tap", FieldType.ENUM,
                options = listOf("Reply", "Forward", "Copy", "Star", "Delete", "Info"),
            ),
        ),
    ) {
        val op = Fields.text(it, "op", "Forward")
        listOf(
            ActionSpec.LongClickText(Fields.text(it, "contains"), false, 8_000, label = "Long-press the message"),
            ActionSpec.Delay(600),
            ActionSpec.ClickDescription(op, 6_000, label = "Tap $op"),
        )
    },
    tapId("wa_t_forward_send", "Send (on the forward screen)", "send", timeout = 8_000),
)

/** Two taps: open a sheet by id, then tap a labelled row in it. */
private fun seqAction(id: String, title: String, openId: String, rowText: String) =
    PlatformAction(id, title, "$openId → \"$rowText\"") {
        listOf(
            ActionSpec.ClickViewId(openId, 6_000, label = "Open the sheet"),
            ActionSpec.Delay(700),
            ActionSpec.ClickText(rowText, false, 5_000, label = "Tap “$rowText”"),
        )
    }

/** Telegram: chat list and conversation buttons. */
private fun telegramTaps(): List<PlatformAction> = listOf(
    tapDesc("tg_t_newmsg", "New message", "New Message"),
    tapDesc("tg_t_search", "Search", "Search"),
    PlatformAction(
        "tg_t_search_type", "Search for…", "Opens Search and types.",
        listOf(FieldDef("q", "Search text", FieldType.TEXT)),
    ) {
        listOf(
            ActionSpec.ClickDescription("Search", 6_000, label = "Tap “Search”"),
            ActionSpec.Delay(600),
            ActionSpec.SetText(Fields.text(it, "q"), TextFieldTarget.FIRST_EDITABLE, "", 6_000, label = "Type “${Fields.text(it, "q")}”"),
        )
    },
    PlatformAction(
        "tg_t_type", "Type in the message box", "Into Telegram's chat field.",
        listOf(messageField),
    ) {
        listOf(ActionSpec.SetText(Fields.text(it, "message"), TextFieldTarget.BY_VIEW_ID, "chat_text_edit", 8_000, label = "Type message"))
    },
    tapId("tg_t_send", "Send", "send_button", timeout = 8_000),
    tapDesc("tg_t_attach", "Attach", "Attach media"),
    tapDesc("tg_t_emoji", "Emoji / stickers", "Emoji, stickers, and GIFs"),
    tapDesc("tg_t_menu", "⋮ menu", "More options"),
    tapDesc("tg_t_call", "Call", "Call"),
    PlatformAction(
        "tg_t_msg_menu", "Long-press a message, then…", "",
        listOf(
            FieldDef("contains", "Message contains", FieldType.TEXT),
            FieldDef(
                "op", "Then tap", FieldType.ENUM,
                options = listOf("Reply", "Forward", "Copy", "Pin", "Edit", "Delete"),
            ),
        ),
    ) {
        val op = Fields.text(it, "op", "Forward")
        listOf(
            ActionSpec.LongClickText(Fields.text(it, "contains"), false, 8_000, label = "Long-press the message"),
            ActionSpec.Delay(600),
            ActionSpec.ClickText(op, false, 6_000, label = "Tap $op"),
        )
    },
)

/** Instagram: bottom bar and post controls. */
private fun instagramTaps(): List<PlatformAction> = listOf(
    tapDesc("ig_t_home", "Home", "Home"),
    tapDesc("ig_t_explore", "Search / Explore", "Search and explore"),
    tapDesc("ig_t_reels", "Reels", "Reels"),
    tapDesc("ig_t_new", "New post (+)", "New post"),
    tapDesc("ig_t_direct", "Direct / Messages", "Direct"),
    tapDesc("ig_t_like", "Like", "Like"),
    tapDesc("ig_t_comment", "Comment", "Comment"),
    tapDesc("ig_t_share", "Share", "Share Post"),
    tapDesc("ig_t_save", "Save", "Save"),
    tapText("ig_t_follow", "Follow", "Follow"),
    tapText("ig_t_send_dm", "Send (DM)", "Send"),
)

/** Messenger / Facebook: chat and feed buttons. */
private fun facebookTaps(): List<PlatformAction> = listOf(
    tapDesc("fb_t_search", "Search", "Search"),
    tapDesc("fb_t_menu", "Menu", "Menu"),
    tapText("fb_t_like", "Like", "Like"),
    tapText("fb_t_comment", "Comment", "Comment"),
    tapText("fb_t_share", "Share", "Share"),
    tapText("fb_t_post", "Post", "Post"),
    tapDesc("fb_t_send_msg", "Send (Messenger)", "Send"),
    PlatformAction(
        "fb_t_type_msg", "Type in the Messenger box", "",
        listOf(messageField),
    ) {
        listOf(ActionSpec.SetText(Fields.text(it, "message"), TextFieldTarget.BY_HINT, "message", 8_000, label = "Type message"))
    },
)

/** X (Twitter): compose and post controls. */
private fun xTaps(): List<PlatformAction> = listOf(
    tapDesc("x_t_compose", "Compose / New post", "Post"),
    tapDesc("x_t_home", "Home", "Home"),
    tapDesc("x_t_search", "Search", "Search and Explore"),
    tapDesc("x_t_messages", "Messages", "Messages"),
    tapDesc("x_t_like", "Like", "Like"),
    tapDesc("x_t_repost", "Repost", "Repost"),
    tapDesc("x_t_reply", "Reply", "Reply"),
    tapText("x_t_post", "Post (confirm)", "Post"),
    PlatformAction(
        "x_t_type", "Type in the composer", "",
        listOf(messageField),
    ) {
        listOf(ActionSpec.SetText(Fields.text(it, "message"), TextFieldTarget.FIRST_EDITABLE, "", 8_000, label = "Type post"))
    },
)

/** YouTube: player and navigation buttons. */
private fun youtubeTaps(): List<PlatformAction> = listOf(
    tapDesc("yt_t_search", "Search", "Search"),
    tapText("yt_t_skip", "Skip ad", "Skip"),
    tapDesc("yt_t_like", "Like", "like this video"),
    tapText("yt_t_subscribe", "Subscribe", "Subscribe"),
    tapDesc("yt_t_share", "Share", "Share"),
    tapDesc("yt_t_fullscreen", "Fullscreen", "Enter fullscreen"),
    PlatformAction("yt_t_next_short", "Swipe to next Short", "") {
        listOf(ActionSpec.Swipe(540, 1600, 540, 500, 250))
    },
)

private fun appTaps(platformId: String): List<PlatformAction> = when (platformId) {
    "whatsapp" -> whatsappTaps()
    "telegram" -> telegramTaps()
    "instagram" -> instagramTaps()
    "facebook" -> facebookTaps()
    "x" -> xTaps()
    "youtube" -> youtubeTaps()
    else -> emptyList()
}

object Platforms {

    // ================= WhatsApp =================

    private val whatsapp = Platform(
        id = "whatsapp",
        name = "WhatsApp",
        packageName = KnownPackages.WHATSAPP,
        color = Color(0xFF25D366),
        icon = Icons.Default.Chat,
        triggers = listOf(
            PlatformTrigger("wa_any", "Any message received", "Fires on every incoming WhatsApp message.") {
                notif(KnownPackages.WHATSAPP)
            },
            PlatformTrigger(
                "wa_from", "Message from a specific contact",
                "Only messages whose notification title is this contact.",
                listOf(senderField),
            ) { notif(KnownPackages.WHATSAPP, title = exact(Fields.text(it, "sender"))) },
            PlatformTrigger(
                "wa_from_contains", "Message from a contact (partial name)",
                "Matches part of the contact name.", listOf(senderField),
            ) { notif(KnownPackages.WHATSAPP, title = contains(Fields.text(it, "sender"))) },
            PlatformTrigger(
                "wa_group", "Message in any group",
                "Group notifications carry \"Sender: message\" in the body.",
            ) { notif(KnownPackages.WHATSAPP, text = regex(GROUP_BODY)) },
            PlatformTrigger(
                "wa_group_named", "Message in a specific group",
                "The notification title is the group name.", listOf(groupField),
            ) { notif(KnownPackages.WHATSAPP, title = exact(Fields.text(it, "group"))) },
            PlatformTrigger(
                "wa_keyword", "Message containing a keyword",
                "Any chat, filtered by body text.", listOf(keywordField),
            ) { notif(KnownPackages.WHATSAPP, text = contains(Fields.text(it, "keyword"))) },
            PlatformTrigger("wa_mention", "You were mentioned", "Body contains an @ mention.") {
                notif(KnownPackages.WHATSAPP, text = contains("@"))
            },
            PlatformTrigger("wa_missed_call", "Missed call", "WhatsApp posts a missed-call notification.") {
                notif(KnownPackages.WHATSAPP, text = regex("(?i)missed (voice|video)? ?call"))
            },
            PlatformTrigger("wa_voice", "Voice message received", "Body reads as a voice message.") {
                notif(KnownPackages.WHATSAPP, text = regex("(?i)(voice message|audio)"))
            },
            PlatformTrigger("wa_photo", "Photo received", "") {
                notif(KnownPackages.WHATSAPP, text = regex("(?i)(photo|image|📷)"))
            },
            PlatformTrigger("wa_video", "Video received", "") {
                notif(KnownPackages.WHATSAPP, text = regex("(?i)(video|🎥)"))
            },
            PlatformTrigger("wa_document", "Document received", "") {
                notif(KnownPackages.WHATSAPP, text = regex("(?i)(document|📄)"))
            },
            PlatformTrigger("wa_opened", "WhatsApp opened", "Fires when WhatsApp comes to the foreground.") {
                TriggerSpec.AppOpened(KnownPackages.WHATSAPP)
            },
            PlatformTrigger("wa_closed", "WhatsApp closed", "Fires when you leave WhatsApp.") {
                TriggerSpec.AppClosed(KnownPackages.WHATSAPP)
            },
            PlatformTrigger(
                "wa_screen", "Text appears on the WhatsApp screen",
                "Watches the live screen instead of notifications.", listOf(keywordField),
            ) {
                TriggerSpec.ScreenText(KnownPackages.WHATSAPP, contains(Fields.text(it, "keyword")))
            },
        ),
        actions = listOf(
            PlatformAction(
                "wa_send_number", "Send a message to a number",
                "Opens the chat pre-filled through a wa.me link, then taps send.",
                listOf(
                    FieldDef("number", "Phone number", FieldType.TEXT, "International format, digits only"),
                    messageField,
                ),
            ) {
                listOf(
                    ActionSpec.OpenUrl(
                        "https://wa.me/${Fields.text(it, "number")}?text=${'$'}{{message_enc}}"
                            .replace("\${{message_enc}}", encodeTemplate(Fields.text(it, "message"))),
                        KnownPackages.WHATSAPP,
                    ),
                    ActionSpec.Delay(3_000),
                    ActionSpec.ClickViewId("send", timeoutMs = 10_000),
                )
            },
            PlatformAction(
                "wa_reply", "Reply in the chat that is open",
                "Types into the message box and sends.",
                listOf(messageField),
            ) {
                // View ids are language independent; the Send button's description is
                // localized ("Send", "إرسال", ...) and cannot be matched reliably.
                listOf(
                    ActionSpec.SetText(
                        text = Fields.text(it, "message"),
                        target = TextFieldTarget.BY_VIEW_ID,
                        selector = "entry",
                        timeoutMs = 8_000,
                    ),
                    ActionSpec.Delay(600),
                    ActionSpec.ClickViewId("send", timeoutMs = 8_000),
                )
            },
            PlatformAction(
                "wa_open_chat", "Open a chat with a number", "",
                listOf(FieldDef("number", "Phone number", FieldType.TEXT)),
            ) {
                listOf(ActionSpec.OpenUrl("https://wa.me/${Fields.text(it, "number")}", KnownPackages.WHATSAPP))
            },
            PlatformAction(
                "wa_open_contact", "Open a chat by contact name",
                "Searches inside WhatsApp, then opens the first result.",
                listOf(senderField),
            ) {
                listOf(
                    ActionSpec.LaunchApp(KnownPackages.WHATSAPP),
                    ActionSpec.WaitForApp(KnownPackages.WHATSAPP, 8_000),
                    ActionSpec.ClickDescription("Search", timeoutMs = 6_000),
                    ActionSpec.Delay(700),
                    ActionSpec.SetText(Fields.text(it, "sender"), TextFieldTarget.FIRST_EDITABLE),
                    ActionSpec.Delay(1_200),
                    ActionSpec.ClickText(Fields.text(it, "sender"), timeoutMs = 6_000),
                )
            },
            PlatformAction(
                "wa_share", "Share text to WhatsApp",
                "Opens the WhatsApp chooser with the text ready.",
                listOf(messageField),
            ) {
                listOf(ActionSpec.ShareText(KnownPackages.WHATSAPP, Fields.text(it, "message")))
            },
            PlatformAction("wa_open", "Open WhatsApp", "") {
                listOf(ActionSpec.LaunchApp(KnownPackages.WHATSAPP))
            },
            PlatformAction(
                "wa_call", "Call a number", "Opens the dialer with the number filled in.",
                listOf(FieldDef("number", "Phone number", FieldType.TEXT)),
            ) { listOf(ActionSpec.Dial(Fields.text(it, "number"))) },
            PlatformAction("wa_mark_read", "Mark the chat as read", "Opens the chat then goes back.") {
                listOf(
                    ActionSpec.LaunchApp(KnownPackages.WHATSAPP),
                    ActionSpec.Delay(1_500),
                    ActionSpec.Global(GlobalActionType.BACK),
                )
            },
            PlatformAction(
                "wa_search", "Search inside WhatsApp", "",
                listOf(keywordField),
            ) {
                listOf(
                    ActionSpec.LaunchApp(KnownPackages.WHATSAPP),
                    ActionSpec.WaitForApp(KnownPackages.WHATSAPP, 8_000),
                    ActionSpec.ClickDescription("Search", timeoutMs = 6_000),
                    ActionSpec.Delay(600),
                    ActionSpec.SetText(Fields.text(it, "keyword"), TextFieldTarget.FIRST_EDITABLE),
                )
            },
            PlatformAction("wa_forward_webhook", "Forward the message to a webhook",
                "Sends sender and body as JSON. Works with the screen off.",
                listOf(FieldDef("url", "Webhook URL", FieldType.TEXT)),
            ) {
                listOf(
                    ActionSpec.HttpRequest(
                        url = Fields.text(it, "url"),
                        method = "POST",
                        body = """{"app":"whatsapp","from":"{{title|json}}","text":"{{text|json}}"}""",
                    )
                )
            },
            PlatformAction("wa_copy", "Copy the message to the clipboard", "") {
                listOf(ActionSpec.CopyToClipboard("{{text}}"))
            },
            PlatformAction("wa_close", "Close WhatsApp", "Presses home.") {
                listOf(ActionSpec.Global(GlobalActionType.HOME))
            },
        ),
    )

    // ================= Telegram =================

    private val telegram = Platform(
        id = "telegram",
        name = "Telegram",
        packageName = KnownPackages.TELEGRAM,
        color = Color(0xFF229ED9),
        icon = Icons.AutoMirrored.Filled.Send,
        triggers = listOf(
            PlatformTrigger("tg_any", "Any message received", "Every Telegram notification.") {
                notif(KnownPackages.TELEGRAM)
            },
            PlatformTrigger(
                "tg_from", "Message from a specific contact", "", listOf(senderField),
            ) { notif(KnownPackages.TELEGRAM, title = exact(Fields.text(it, "sender"))) },
            PlatformTrigger(
                "tg_channel", "Post in a specific channel",
                "The notification title is the channel name.",
                listOf(FieldDef("channel", "Channel name", FieldType.TEXT)),
            ) { notif(KnownPackages.TELEGRAM, title = exact(Fields.text(it, "channel"))) },
            PlatformTrigger("tg_group", "Message in any group", "Body carries \"Sender: message\".") {
                notif(KnownPackages.TELEGRAM, text = regex(GROUP_BODY))
            },
            PlatformTrigger(
                "tg_group_named", "Message in a specific group", "", listOf(groupField),
            ) { notif(KnownPackages.TELEGRAM, title = exact(Fields.text(it, "group"))) },
            PlatformTrigger(
                "tg_keyword", "Message containing a keyword", "", listOf(keywordField),
            ) { notif(KnownPackages.TELEGRAM, text = contains(Fields.text(it, "keyword"))) },
            PlatformTrigger("tg_mention", "You were mentioned", "") {
                notif(KnownPackages.TELEGRAM, text = contains("@"))
            },
            PlatformTrigger("tg_link", "Message containing a link", "Body has an http(s) URL.") {
                notif(KnownPackages.TELEGRAM, text = regex("https?://"))
            },
            PlatformTrigger("tg_media", "Photo or video received", "") {
                notif(KnownPackages.TELEGRAM, text = regex("(?i)(photo|video|image|sticker|gif)"))
            },
            PlatformTrigger("tg_bot", "Message from a bot", "Title ends with \"bot\".") {
                notif(KnownPackages.TELEGRAM, title = regex("(?i)bot${'$'}"))
            },
            PlatformTrigger("tg_call", "Incoming or missed call", "") {
                notif(KnownPackages.TELEGRAM, text = regex("(?i)call"))
            },
            PlatformTrigger("tg_opened", "Telegram opened", "") {
                TriggerSpec.AppOpened(KnownPackages.TELEGRAM)
            },
            PlatformTrigger("tg_closed", "Telegram closed", "") {
                TriggerSpec.AppClosed(KnownPackages.TELEGRAM)
            },
            PlatformTrigger(
                "tg_screen", "Text appears on the Telegram screen", "", listOf(keywordField),
            ) { TriggerSpec.ScreenText(KnownPackages.TELEGRAM, contains(Fields.text(it, "keyword"))) },
        ),
        actions = listOf(
            PlatformAction(
                "tg_bot_send", "Send a message via Bot API",
                "Pure HTTPS — works with the screen off and never breaks on a UI change.",
                listOf(
                    FieldDef("token", "Bot token", FieldType.TEXT, "From @BotFather"),
                    FieldDef("chat", "Chat ID", FieldType.TEXT, "Numeric id or @channelname"),
                    messageField,
                ),
            ) {
                listOf(
                    ActionSpec.HttpRequest(
                        url = "https://api.telegram.org/bot${Fields.text(it, "token")}/sendMessage",
                        method = "POST",
                        body = """{"chat_id":"${Fields.text(it, "chat")}","text":"${jsonTemplate(Fields.text(it, "message"))}"}""",
                    )
                )
            },
            PlatformAction(
                "tg_bot_photo", "Send a photo via Bot API", "",
                listOf(
                    FieldDef("token", "Bot token", FieldType.TEXT),
                    FieldDef("chat", "Chat ID", FieldType.TEXT),
                    FieldDef("photo", "Photo URL", FieldType.TEXT),
                    FieldDef("caption", "Caption", FieldType.TEXT),
                ),
            ) {
                listOf(
                    ActionSpec.HttpRequest(
                        url = "https://api.telegram.org/bot${Fields.text(it, "token")}/sendPhoto",
                        method = "POST",
                        body = """{"chat_id":"${Fields.text(it, "chat")}","photo":"${Fields.text(it, "photo")}","caption":"${jsonTemplate(Fields.text(it, "caption"))}"}""",
                    )
                )
            },
            PlatformAction(
                "tg_open_user", "Open a chat by username", "Uses a t.me link.",
                listOf(FieldDef("username", "Username without @", FieldType.TEXT)),
            ) {
                listOf(ActionSpec.OpenUrl("https://t.me/${Fields.text(it, "username")}", KnownPackages.TELEGRAM))
            },
            PlatformAction(
                "tg_reply", "Reply in the chat that is open", "",
                listOf(messageField),
            ) {
                listOf(
                    ActionSpec.SetText(
                        text = Fields.text(it, "message"),
                        target = TextFieldTarget.BY_VIEW_ID,
                        selector = "chat_text_edit",
                        timeoutMs = 8_000,
                    ),
                    ActionSpec.Delay(600),
                    ActionSpec.ClickViewId("send_button", timeoutMs = 8_000),
                )
            },
            PlatformAction(
                "tg_share", "Share text to Telegram", "", listOf(messageField),
            ) { listOf(ActionSpec.ShareText(KnownPackages.TELEGRAM, Fields.text(it, "message"))) },
            PlatformAction("tg_open", "Open Telegram", "") {
                listOf(ActionSpec.LaunchApp(KnownPackages.TELEGRAM))
            },
            PlatformAction(
                "tg_search", "Search inside Telegram", "", listOf(keywordField),
            ) {
                listOf(
                    ActionSpec.LaunchApp(KnownPackages.TELEGRAM),
                    ActionSpec.WaitForApp(KnownPackages.TELEGRAM, 8_000),
                    ActionSpec.ClickDescription("Search", timeoutMs = 6_000),
                    ActionSpec.Delay(600),
                    ActionSpec.SetText(Fields.text(it, "keyword"), TextFieldTarget.FIRST_EDITABLE),
                )
            },
            PlatformAction(
                "tg_extract_link", "Extract the link and open it",
                "Pulls the first URL out of the message.",
            ) {
                listOf(
                    ActionSpec.ExtractRegex("{{text}}", "(https?://\\S+)", 1, "link"),
                    ActionSpec.OpenUrl("{{link}}", KnownPackages.CHROME),
                )
            },
            PlatformAction("tg_copy", "Copy the message to the clipboard", "") {
                listOf(ActionSpec.CopyToClipboard("{{text}}"))
            },
            PlatformAction(
                "tg_webhook", "Forward the message to a webhook", "",
                listOf(FieldDef("url", "Webhook URL", FieldType.TEXT)),
            ) {
                listOf(
                    ActionSpec.HttpRequest(
                        url = Fields.text(it, "url"),
                        method = "POST",
                        body = """{"app":"telegram","from":"{{title|json}}","text":"{{text|json}}"}""",
                    )
                )
            },
            PlatformAction("tg_close", "Close Telegram", "") {
                listOf(ActionSpec.Global(GlobalActionType.HOME))
            },
        ),
    )

    // ================= Instagram =================

    private val instagram = Platform(
        id = "instagram",
        name = "Instagram",
        packageName = KnownPackages.INSTAGRAM,
        color = Color(0xFFE1306C),
        icon = Icons.Default.PhotoCamera,
        triggers = listOf(
            PlatformTrigger("ig_any", "Any Instagram notification", "") {
                notif(KnownPackages.INSTAGRAM)
            },
            PlatformTrigger("ig_dm", "Direct message received", "Body reads as a message.") {
                notif(KnownPackages.INSTAGRAM, text = regex("(?i)(sent you a message|:|messaged)"))
            },
            PlatformTrigger(
                "ig_dm_from", "Message from a specific person", "", listOf(senderField),
            ) { notif(KnownPackages.INSTAGRAM, title = contains(Fields.text(it, "sender"))) },
            PlatformTrigger("ig_follower", "New follower", "") {
                notif(KnownPackages.INSTAGRAM, text = regex("(?i)started following"))
            },
            PlatformTrigger("ig_like", "Someone liked your post", "") {
                notif(KnownPackages.INSTAGRAM, text = regex("(?i)liked your"))
            },
            PlatformTrigger("ig_comment", "New comment", "") {
                notif(KnownPackages.INSTAGRAM, text = regex("(?i)commented"))
            },
            PlatformTrigger("ig_mention", "You were mentioned or tagged", "") {
                notif(KnownPackages.INSTAGRAM, text = regex("(?i)(mentioned|tagged) you"))
            },
            PlatformTrigger("ig_story", "Story reply or reaction", "") {
                notif(KnownPackages.INSTAGRAM, text = regex("(?i)(replied to your story|reacted)"))
            },
            PlatformTrigger("ig_live", "Someone started a live video", "") {
                notif(KnownPackages.INSTAGRAM, text = regex("(?i)(is live|started a live)"))
            },
            PlatformTrigger(
                "ig_keyword", "Notification containing a keyword", "", listOf(keywordField),
            ) { notif(KnownPackages.INSTAGRAM, text = contains(Fields.text(it, "keyword"))) },
            PlatformTrigger("ig_opened", "Instagram opened", "") {
                TriggerSpec.AppOpened(KnownPackages.INSTAGRAM)
            },
            PlatformTrigger("ig_closed", "Instagram closed", "") {
                TriggerSpec.AppClosed(KnownPackages.INSTAGRAM)
            },
        ),
        actions = listOf(
            PlatformAction("ig_open", "Open Instagram", "") {
                listOf(ActionSpec.LaunchApp(KnownPackages.INSTAGRAM))
            },
            PlatformAction(
                "ig_profile", "Open a profile", "Deep-links to instagram.com/username.",
                listOf(FieldDef("username", "Username without @", FieldType.TEXT)),
            ) {
                listOf(
                    ActionSpec.OpenUrl(
                        "https://www.instagram.com/${Fields.text(it, "username")}/",
                        KnownPackages.INSTAGRAM,
                    )
                )
            },
            PlatformAction("ig_dms", "Open direct messages", "") {
                listOf(
                    ActionSpec.LaunchApp(KnownPackages.INSTAGRAM),
                    ActionSpec.WaitForApp(KnownPackages.INSTAGRAM, 8_000),
                    ActionSpec.ClickDescription("Direct", timeoutMs = 8_000),
                )
            },
            PlatformAction(
                "ig_share", "Share text or a link to Instagram", "", listOf(messageField),
            ) { listOf(ActionSpec.ShareText(KnownPackages.INSTAGRAM, Fields.text(it, "message"))) },
            PlatformAction("ig_like", "Like the post on screen", "") {
                listOf(ActionSpec.ClickDescription("Like", timeoutMs = 6_000))
            },
            PlatformAction("ig_follow", "Follow the profile on screen", "") {
                listOf(ActionSpec.ClickText("Follow", exact = true, timeoutMs = 6_000))
            },
            PlatformAction("ig_explore", "Open Explore", "") {
                listOf(
                    ActionSpec.LaunchApp(KnownPackages.INSTAGRAM),
                    ActionSpec.WaitForApp(KnownPackages.INSTAGRAM, 8_000),
                    ActionSpec.ClickDescription("Search and explore", timeoutMs = 8_000),
                )
            },
            PlatformAction("ig_reels", "Open Reels", "") {
                listOf(
                    ActionSpec.LaunchApp(KnownPackages.INSTAGRAM),
                    ActionSpec.WaitForApp(KnownPackages.INSTAGRAM, 8_000),
                    ActionSpec.ClickDescription("Reels", timeoutMs = 8_000),
                )
            },
            PlatformAction("ig_scroll", "Scroll the feed", "") {
                listOf(ActionSpec.Scroll(ScrollDirection.FORWARD))
            },
            PlatformAction(
                "ig_webhook", "Forward the notification to a webhook", "",
                listOf(FieldDef("url", "Webhook URL", FieldType.TEXT)),
            ) {
                listOf(
                    ActionSpec.HttpRequest(
                        url = Fields.text(it, "url"),
                        method = "POST",
                        body = """{"app":"instagram","from":"{{title|json}}","text":"{{text|json}}"}""",
                    )
                )
            },
            PlatformAction("ig_close", "Close Instagram", "") {
                listOf(ActionSpec.Global(GlobalActionType.HOME))
            },
        ),
    )

    // ================= Facebook =================

    private val facebook = Platform(
        id = "facebook",
        name = "Facebook",
        packageName = KnownPackages.FACEBOOK,
        color = Color(0xFF1877F2),
        icon = Icons.Default.Groups,
        triggers = listOf(
            PlatformTrigger("fb_any", "Any Facebook notification", "") {
                notif(KnownPackages.FACEBOOK)
            },
            PlatformTrigger("fb_msg", "Messenger message received", "Watches the Messenger app.") {
                notif(KnownPackages.MESSENGER)
            },
            PlatformTrigger(
                "fb_msg_from", "Messenger message from a person", "", listOf(senderField),
            ) { notif(KnownPackages.MESSENGER, title = contains(Fields.text(it, "sender"))) },
            PlatformTrigger("fb_friend", "Friend request", "") {
                notif(KnownPackages.FACEBOOK, text = regex("(?i)friend request"))
            },
            PlatformTrigger("fb_reaction", "Someone reacted to your post", "") {
                notif(KnownPackages.FACEBOOK, text = regex("(?i)(reacted|likes your|liked your)"))
            },
            PlatformTrigger("fb_comment", "New comment on your post", "") {
                notif(KnownPackages.FACEBOOK, text = regex("(?i)commented"))
            },
            PlatformTrigger("fb_tag", "You were tagged", "") {
                notif(KnownPackages.FACEBOOK, text = regex("(?i)tagged you"))
            },
            PlatformTrigger("fb_birthday", "Birthday reminder", "") {
                notif(KnownPackages.FACEBOOK, text = regex("(?i)birthday"))
            },
            PlatformTrigger("fb_event", "Event reminder", "") {
                notif(KnownPackages.FACEBOOK, text = regex("(?i)event"))
            },
            PlatformTrigger("fb_live", "Someone went live", "") {
                notif(KnownPackages.FACEBOOK, text = regex("(?i)(is live|live video)"))
            },
            PlatformTrigger("fb_group", "Group activity", "") {
                notif(KnownPackages.FACEBOOK, text = regex("(?i)(posted in|group)"))
            },
            PlatformTrigger(
                "fb_keyword", "Notification containing a keyword", "", listOf(keywordField),
            ) { notif(KnownPackages.FACEBOOK, text = contains(Fields.text(it, "keyword"))) },
            PlatformTrigger("fb_opened", "Facebook opened", "") {
                TriggerSpec.AppOpened(KnownPackages.FACEBOOK)
            },
            PlatformTrigger("fb_closed", "Facebook closed", "") {
                TriggerSpec.AppClosed(KnownPackages.FACEBOOK)
            },
        ),
        actions = listOf(
            PlatformAction("fb_open", "Open Facebook", "") {
                listOf(ActionSpec.LaunchApp(KnownPackages.FACEBOOK))
            },
            PlatformAction(
                "fb_post", "Post text to your feed",
                "Opens the composer pre-filled, then taps Post.",
                listOf(messageField),
            ) {
                listOf(
                    ActionSpec.ShareText(KnownPackages.FACEBOOK, Fields.text(it, "message")),
                    ActionSpec.Delay(3_500),
                    ActionSpec.ClickText("Post", exact = true, timeoutMs = 10_000),
                )
            },
            PlatformAction("fb_messenger", "Open Messenger", "") {
                listOf(ActionSpec.LaunchApp(KnownPackages.MESSENGER))
            },
            PlatformAction(
                "fb_msg_send", "Reply in the Messenger chat that is open", "",
                listOf(messageField),
            ) {
                listOf(
                    ActionSpec.SetText(Fields.text(it, "message"), TextFieldTarget.BY_HINT, "message", 8_000),
                    ActionSpec.Delay(500),
                    ActionSpec.ClickDescription("Send", timeoutMs = 8_000),
                )
            },
            PlatformAction(
                "fb_share_link", "Share a link to Facebook", "",
                listOf(FieldDef("url", "Link", FieldType.TEXT)),
            ) { listOf(ActionSpec.ShareText(KnownPackages.FACEBOOK, Fields.text(it, "url"))) },
            PlatformAction(
                "fb_profile", "Open a profile or page", "",
                listOf(FieldDef("handle", "Profile or page name", FieldType.TEXT)),
            ) {
                listOf(
                    ActionSpec.OpenUrl(
                        "https://www.facebook.com/${Fields.text(it, "handle")}",
                        KnownPackages.FACEBOOK,
                    )
                )
            },
            PlatformAction("fb_like", "Like the post on screen", "") {
                listOf(ActionSpec.ClickDescription("Like", timeoutMs = 6_000))
            },
            PlatformAction("fb_notifications", "Open notifications", "") {
                listOf(
                    ActionSpec.LaunchApp(KnownPackages.FACEBOOK),
                    ActionSpec.WaitForApp(KnownPackages.FACEBOOK, 8_000),
                    ActionSpec.ClickDescription("Notifications", timeoutMs = 8_000),
                )
            },
            PlatformAction("fb_scroll", "Scroll the feed", "") {
                listOf(ActionSpec.Scroll(ScrollDirection.FORWARD))
            },
            PlatformAction(
                "fb_webhook", "Forward the notification to a webhook", "",
                listOf(FieldDef("url", "Webhook URL", FieldType.TEXT)),
            ) {
                listOf(
                    ActionSpec.HttpRequest(
                        url = Fields.text(it, "url"),
                        method = "POST",
                        body = """{"app":"facebook","from":"{{title|json}}","text":"{{text|json}}"}""",
                    )
                )
            },
            PlatformAction("fb_close", "Close Facebook", "") {
                listOf(ActionSpec.Global(GlobalActionType.HOME))
            },
        ),
    )

    // ================= YouTube =================

    private val youtube = Platform(
        id = "youtube",
        name = "YouTube",
        packageName = KnownPackages.YOUTUBE,
        color = Color(0xFFFF0000),
        icon = Icons.Default.SmartDisplay,
        triggers = listOf(
            PlatformTrigger("yt_any", "Any YouTube notification", "") {
                notif(KnownPackages.YOUTUBE)
            },
            PlatformTrigger(
                "yt_channel", "New video from a channel",
                "The notification title is the channel name.",
                listOf(FieldDef("channel", "Channel name", FieldType.TEXT)),
            ) { notif(KnownPackages.YOUTUBE, title = contains(Fields.text(it, "channel"))) },
            PlatformTrigger("yt_upload", "A subscription uploaded", "") {
                notif(KnownPackages.YOUTUBE, text = regex("(?i)(uploaded|new video|posted)"))
            },
            PlatformTrigger("yt_live", "A channel went live", "") {
                notif(KnownPackages.YOUTUBE, text = regex("(?i)(is live|live now|streaming)"))
            },
            PlatformTrigger("yt_premiere", "A premiere is starting", "") {
                notif(KnownPackages.YOUTUBE, text = regex("(?i)premiere"))
            },
            PlatformTrigger("yt_comment", "Reply to your comment", "") {
                notif(KnownPackages.YOUTUBE, text = regex("(?i)(replied|comment)"))
            },
            PlatformTrigger(
                "yt_keyword", "Notification containing a keyword", "", listOf(keywordField),
            ) { notif(KnownPackages.YOUTUBE, text = contains(Fields.text(it, "keyword"))) },
            PlatformTrigger(
                "yt_ad", "An ad appears on screen",
                "Watches for the Skip button so a rule can tap it.",
            ) { TriggerSpec.ScreenText(KnownPackages.YOUTUBE, contains("Skip")) },
            PlatformTrigger(
                "yt_screen", "Text appears on the YouTube screen", "", listOf(keywordField),
            ) { TriggerSpec.ScreenText(KnownPackages.YOUTUBE, contains(Fields.text(it, "keyword"))) },
            PlatformTrigger("yt_opened", "YouTube opened", "") {
                TriggerSpec.AppOpened(KnownPackages.YOUTUBE)
            },
            PlatformTrigger("yt_closed", "YouTube closed", "") {
                TriggerSpec.AppClosed(KnownPackages.YOUTUBE)
            },
        ),
        actions = listOf(
            PlatformAction("yt_open", "Open YouTube", "") {
                listOf(ActionSpec.LaunchApp(KnownPackages.YOUTUBE))
            },
            PlatformAction(
                "yt_search", "Search YouTube", "Deep-links straight to results.",
                listOf(keywordField),
            ) {
                listOf(
                    ActionSpec.OpenUrl(
                        "https://www.youtube.com/results?search_query=${urlTemplate(Fields.text(it, "keyword"))}",
                        KnownPackages.YOUTUBE,
                    )
                )
            },
            PlatformAction(
                "yt_video", "Open a video by URL or ID", "",
                listOf(FieldDef("video", "Video URL or ID", FieldType.TEXT)),
            ) {
                val v = Fields.text(it, "video")
                val url = if (v.startsWith("http")) v else "https://www.youtube.com/watch?v=$v"
                listOf(ActionSpec.OpenUrl(url, KnownPackages.YOUTUBE))
            },
            PlatformAction(
                "yt_channel_open", "Open a channel", "",
                listOf(FieldDef("channel", "Channel handle without @", FieldType.TEXT)),
            ) {
                listOf(
                    ActionSpec.OpenUrl(
                        "https://www.youtube.com/@${Fields.text(it, "channel")}",
                        KnownPackages.YOUTUBE,
                    )
                )
            },
            PlatformAction("yt_skip_ad", "Skip the ad", "Taps the Skip button when it appears.") {
                listOf(ActionSpec.ClickText("Skip", timeoutMs = 6_000))
            },
            PlatformAction("yt_like", "Like the video on screen", "") {
                listOf(ActionSpec.ClickDescription("like", timeoutMs = 6_000))
            },
            PlatformAction("yt_subscribe", "Subscribe to the channel on screen", "") {
                listOf(ActionSpec.ClickText("Subscribe", exact = true, timeoutMs = 6_000))
            },
            PlatformAction("yt_playpause", "Play or pause", "Sends a media key, works app-wide.") {
                listOf(ActionSpec.Media(com.autoflow.app.data.MediaCommand.PLAY_PAUSE))
            },
            PlatformAction("yt_next", "Next video", "") {
                listOf(ActionSpec.Media(com.autoflow.app.data.MediaCommand.NEXT))
            },
            PlatformAction("yt_scroll", "Scroll to the next short", "") {
                listOf(ActionSpec.Swipe(540, 1600, 540, 500, 250))
            },
            PlatformAction(
                "yt_webhook", "Forward the notification to a webhook", "",
                listOf(FieldDef("url", "Webhook URL", FieldType.TEXT)),
            ) {
                listOf(
                    ActionSpec.HttpRequest(
                        url = Fields.text(it, "url"),
                        method = "POST",
                        body = """{"app":"youtube","channel":"{{title|json}}","text":"{{text|json}}"}""",
                    )
                )
            },
            PlatformAction("yt_close", "Close YouTube", "") {
                listOf(ActionSpec.Global(GlobalActionType.HOME))
            },
        ),
    )

    // ================= X =================

    private val x = Platform(
        id = "x",
        name = "X (Twitter)",
        packageName = KnownPackages.X,
        color = Color(0xFF0F172A),
        icon = Icons.Default.Tag,
        triggers = listOf(
            PlatformTrigger("x_any", "Any X notification", "") { notif(KnownPackages.X) },
            PlatformTrigger("x_mention", "You were mentioned", "") {
                notif(KnownPackages.X, text = regex("(?i)(mentioned|@)"))
            },
            PlatformTrigger("x_follower", "New follower", "") {
                notif(KnownPackages.X, text = regex("(?i)followed you"))
            },
            PlatformTrigger("x_dm", "Direct message received", "") {
                notif(KnownPackages.X, text = regex("(?i)(sent you a message|direct message)"))
            },
            PlatformTrigger("x_like", "Someone liked your post", "") {
                notif(KnownPackages.X, text = regex("(?i)liked your"))
            },
            PlatformTrigger("x_repost", "Someone reposted you", "") {
                notif(KnownPackages.X, text = regex("(?i)(reposted|retweeted)"))
            },
            PlatformTrigger("x_reply", "Someone replied to you", "") {
                notif(KnownPackages.X, text = regex("(?i)replied"))
            },
            PlatformTrigger(
                "x_from", "Post from a specific account", "", listOf(senderField),
            ) { notif(KnownPackages.X, title = contains(Fields.text(it, "sender"))) },
            PlatformTrigger(
                "x_keyword", "Notification containing a keyword", "", listOf(keywordField),
            ) { notif(KnownPackages.X, text = contains(Fields.text(it, "keyword"))) },
            PlatformTrigger("x_opened", "X opened", "") { TriggerSpec.AppOpened(KnownPackages.X) },
            PlatformTrigger("x_closed", "X closed", "") { TriggerSpec.AppClosed(KnownPackages.X) },
        ),
        actions = listOf(
            PlatformAction(
                "x_post_share", "Post via the share sheet",
                "Pre-fills the composer, then taps Post. No API key needed.",
                listOf(messageField),
            ) {
                listOf(
                    ActionSpec.ShareText(KnownPackages.X, Fields.text(it, "message")),
                    ActionSpec.Delay(3_000),
                    ActionSpec.ClickText("Post", exact = true, timeoutMs = 10_000),
                )
            },
            PlatformAction(
                "x_post_api", "Post via the official API",
                "Works with the screen off. Needs a paid X tier and a bearer token.",
                listOf(
                    FieldDef("token", "Bearer token", FieldType.TEXT),
                    messageField,
                ),
            ) {
                listOf(
                    ActionSpec.HttpRequest(
                        url = "https://api.twitter.com/2/tweets",
                        method = "POST",
                        headers = mapOf("Authorization" to "Bearer ${Fields.text(it, "token")}"),
                        body = """{"text":"${jsonTemplate(Fields.text(it, "message"))}"}""",
                    )
                )
            },
            PlatformAction(
                "x_post_ui", "Post by driving the app",
                "Opens X, taps compose, types and posts.",
                listOf(messageField),
            ) {
                listOf(
                    ActionSpec.LaunchApp(KnownPackages.X),
                    ActionSpec.WaitForApp(KnownPackages.X, 10_000),
                    ActionSpec.Delay(2_000),
                    ActionSpec.ClickDescription("Post", timeoutMs = 10_000),
                    ActionSpec.Delay(1_500),
                    ActionSpec.SetText(Fields.text(it, "message"), TextFieldTarget.FIRST_EDITABLE, "", 10_000),
                    ActionSpec.Delay(1_000),
                    ActionSpec.ClickText("Post", exact = true, timeoutMs = 10_000),
                )
            },
            PlatformAction(
                "x_post_media", "Post with photos or a video",
                "One file posts a single item; several make an album. X needs a video of at " +
                    "least about one second.",
                listOf(
                    messageField,
                    FieldDef("media", "Media URIs, comma separated", FieldType.MULTILINE),
                    FieldDef("type", "Type", FieldType.ENUM, options = listOf("image/*", "video/*")),
                ),
            ) {
                listOf(
                    ActionSpec.ShareMedia(
                        packageName = KnownPackages.X,
                        activityClass = "com.x.android.lib.ComposerActivity",
                        text = Fields.text(it, "message"),
                        mediaUris = Fields.text(it, "media")
                            .split(",").map { u -> u.trim() }.filter { u -> u.isNotEmpty() },
                        mimeType = Fields.text(it, "type", "image/*"),
                    ),
                    ActionSpec.Delay(4_000),
                    ActionSpec.ClickDescription("Post", timeoutMs = 12_000),
                )
            },

            PlatformAction("x_open", "Open X", "") { listOf(ActionSpec.LaunchApp(KnownPackages.X)) },
            PlatformAction(
                "x_profile", "Open a profile", "",
                listOf(FieldDef("handle", "Handle without @", FieldType.TEXT)),
            ) {
                listOf(ActionSpec.OpenUrl("https://x.com/${Fields.text(it, "handle")}", KnownPackages.X))
            },
            PlatformAction("x_like", "Like the post on screen", "") {
                listOf(ActionSpec.ClickDescription("Like", timeoutMs = 6_000))
            },
            PlatformAction("x_repost", "Repost the post on screen", "") {
                listOf(
                    ActionSpec.ClickDescription("Repost", timeoutMs = 6_000),
                    ActionSpec.Delay(800),
                    ActionSpec.ClickText("Repost", exact = true, timeoutMs = 6_000),
                )
            },
            PlatformAction(
                "x_search", "Search X", "",
                listOf(keywordField),
            ) {
                listOf(
                    ActionSpec.OpenUrl(
                        "https://x.com/search?q=${urlTemplate(Fields.text(it, "keyword"))}",
                        KnownPackages.X,
                    )
                )
            },
            PlatformAction("x_dms", "Open direct messages", "") {
                listOf(
                    ActionSpec.LaunchApp(KnownPackages.X),
                    ActionSpec.WaitForApp(KnownPackages.X, 8_000),
                    ActionSpec.ClickDescription("Messages", timeoutMs = 8_000),
                )
            },
            PlatformAction("x_scroll", "Scroll the timeline", "") {
                listOf(ActionSpec.Scroll(ScrollDirection.FORWARD))
            },
            PlatformAction(
                "x_webhook", "Forward the notification to a webhook", "",
                listOf(FieldDef("url", "Webhook URL", FieldType.TEXT)),
            ) {
                listOf(
                    ActionSpec.HttpRequest(
                        url = Fields.text(it, "url"),
                        method = "POST",
                        body = """{"app":"x","from":"{{title|json}}","text":"{{text|json}}"}""",
                    )
                )
            },
            PlatformAction("x_close", "Close X", "") {
                listOf(ActionSpec.Global(GlobalActionType.HOME))
            },
        ),
    )

    /**
     * Chat-reading entries and the granular tap catalog are attached here so every
     * platform picks them up identically.
     */
    val all: List<Platform> = listOf(whatsapp, telegram, instagram, facebook, youtube, x)
        .map { platform ->
            // Universal micro-actions + this app's own button list, for both YouTube
            // (no conversation screen) and the messengers.
            val taps = uiActions() + appTaps(platform.id)
            // YouTube has no conversation screen to read.
            if (platform.id == "youtube") {
                return@map platform.copy(actions = platform.actions + taps)
            }
            // Facebook chats live in Messenger, not the main app.
            val chatPackage =
                if (platform.id == "facebook") KnownPackages.MESSENGER else platform.packageName
            val media = when (platform.id) {
                "whatsapp" -> mediaActions(KnownPackages.WHATSAPP, "WhatsApp", supportsChatTarget = true) +
                    forwardActions(KnownPackages.WHATSAPP, "WhatsApp", chatTarget = true)
                "telegram" -> mediaActions(KnownPackages.TELEGRAM, "Telegram") +
                    forwardActions(KnownPackages.TELEGRAM, "Telegram")
                "instagram" -> mediaActions(KnownPackages.INSTAGRAM, "Instagram") +
                    forwardActions(KnownPackages.INSTAGRAM, "Instagram")
                "facebook" -> mediaActions(KnownPackages.FACEBOOK, "Facebook") +
                    forwardActions(KnownPackages.FACEBOOK, "Facebook")
                "x" -> mediaActions(
                    KnownPackages.X, "X",
                    activity = "com.x.android.lib.ComposerActivity",
                ) + forwardActions(
                    KnownPackages.X, "X",
                    activity = "com.x.android.lib.ComposerActivity",
                )
                else -> emptyList()
            }
            platform.copy(
                triggers = platform.triggers + chatTriggers(chatPackage),
                actions = platform.actions + chatActions(chatPackage) + media + taps,
            )
        }

    fun byId(id: String): Platform? = all.firstOrNull { it.id == id }

    val triggerCount: Int get() = all.sumOf { it.triggers.size }
    val actionCount: Int get() = all.sumOf { it.actions.size }
}

/**
 * A user-entered value may itself contain `{{text}}`; those must survive into the final
 * spec so the engine resolves them at run time. Only the surrounding literal is escaped.
 */
private fun jsonTemplate(value: String): String =
    if (value.contains("{{")) value else value.replace("\"", "\\\"").replace("\n", "\\n")

private fun urlTemplate(value: String): String =
    if (value.contains("{{")) value else java.net.URLEncoder.encode(value, "UTF-8")

private fun encodeTemplate(value: String): String =
    if (value.contains("{{")) value else java.net.URLEncoder.encode(value, "UTF-8")
