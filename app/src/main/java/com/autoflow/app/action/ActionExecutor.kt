package com.autoflow.app.action

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.telephony.SmsManager
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast as AndroidToast
import com.autoflow.app.data.ActionSpec
import com.autoflow.app.data.GlobalActionType
import com.autoflow.app.data.MediaCommand
import com.autoflow.app.data.ScrollDirection
import com.autoflow.app.data.SettingsPanel
import com.autoflow.app.data.TextFieldTarget
import com.autoflow.app.data.VolumeStream
import com.autoflow.app.engine.ConditionEvaluator
import com.autoflow.app.engine.RunContext
import com.autoflow.app.engine.Variables
import com.autoflow.app.trigger.AutoFlowAccessibilityService
import com.autoflow.app.util.KnownPackages
import com.autoflow.app.util.Notifier
import com.autoflow.app.util.Speaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Why a step stopped the rule. Carried into the log so failures are diagnosable. */
class ActionFailure(message: String) : Exception(message)

/** Thrown by StopRule; ends the run without marking it a failure. */
class RuleStopped : Exception()

/**
 * Runs a rule's steps. UI steps go through the accessibility service, network steps over
 * HTTP, and control-flow steps are interpreted here — both kinds sit behind one interface
 * so an API-backed step can replace a fragile UI step without rewriting the rule.
 */
class ActionExecutor(
    private val context: Context,
    private val conditions: ConditionEvaluator = ConditionEvaluator(context),
    /** Lets RunRule call back into the engine without a circular dependency. */
    private val runNestedRule: suspend (String, RunContext) -> Unit = { _, _ -> },
) {

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Walks [actions] with a small interpreter so If/Else/EndIf and Repeat/EndRepeat work
     * as a flat list. Throws [ActionFailure] on the first step that cannot complete.
     */
    suspend fun run(actions: List<ActionSpec>, run: RunContext) {
        // Each open Repeat pushes (startIndex, remainingIterations).
        val loops = ArrayDeque<Pair<Int, Int>>()
        var index = 0
        var guard = 0

        while (index < actions.size) {
            // A malformed rule must not spin forever.
            if (guard++ > MAX_STEPS) throw ActionFailure("rule exceeded $MAX_STEPS steps; check your loops")

            when (val action = actions[index]) {
                is ActionSpec.If -> {
                    if (!conditions.evaluate(action.condition, run)) {
                        index = jumpToElseOrEnd(actions, index)
                        continue
                    }
                }

                // Reached only by falling out of a taken If branch, so skip the else body.
                ActionSpec.Else -> {
                    index = matchingEndIf(actions, index)
                    continue
                }

                ActionSpec.EndIf -> Unit

                is ActionSpec.Repeat -> {
                    if (action.times <= 0) {
                        index = matchingEndRepeat(actions, index)
                        continue
                    }
                    loops.addLast(index to action.times - 1)
                }

                ActionSpec.EndRepeat -> {
                    val loop = loops.removeLastOrNull()
                    if (loop != null && loop.second > 0) {
                        loops.addLast(loop.first to loop.second - 1)
                        index = loop.first + 1
                        continue
                    }
                }

                ActionSpec.StopRule -> throw RuleStopped()

                else -> try {
                    execute(action, run)
                } catch (failure: ActionFailure) {
                    throw ActionFailure("Step ${index + 1} (${action::class.simpleName}): ${failure.message}")
                }
            }
            index++
        }
    }

    // ---- Block matching --------------------------------------------------

    /** Returns the index just after the matching Else, or after the matching EndIf. */
    private fun jumpToElseOrEnd(actions: List<ActionSpec>, ifIndex: Int): Int {
        var depth = 0
        for (i in ifIndex + 1 until actions.size) {
            when (actions[i]) {
                is ActionSpec.If -> depth++
                ActionSpec.Else -> if (depth == 0) return i + 1
                ActionSpec.EndIf -> if (depth == 0) return i + 1 else depth--
                else -> Unit
            }
        }
        return actions.size
    }

    private fun matchingEndIf(actions: List<ActionSpec>, from: Int): Int {
        var depth = 0
        for (i in from + 1 until actions.size) {
            when (actions[i]) {
                is ActionSpec.If -> depth++
                ActionSpec.EndIf -> if (depth == 0) return i + 1 else depth--
                else -> Unit
            }
        }
        return actions.size
    }

    private fun matchingEndRepeat(actions: List<ActionSpec>, from: Int): Int {
        var depth = 0
        for (i in from + 1 until actions.size) {
            when (actions[i]) {
                is ActionSpec.Repeat -> depth++
                ActionSpec.EndRepeat -> if (depth == 0) return i + 1 else depth--
                else -> Unit
            }
        }
        return actions.size
    }

    // ---- Individual steps ------------------------------------------------

    private suspend fun execute(action: ActionSpec, run: RunContext) {
        fun text(value: String) = Variables.resolve(value, run)

        when (action) {
            is ActionSpec.Delay -> delay(action.millis)

            is ActionSpec.LaunchApp -> launchApp(action.packageName)

            is ActionSpec.LaunchActivity -> {
                val intent = Intent().apply {
                    component = ComponentName(action.packageName, action.activityClass)
                }
                startActivity(intent)
            }

            is ActionSpec.OpenUrl -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(text(action.url)))
                if (action.packageName.isNotBlank()) intent.setPackage(action.packageName)
                startActivity(intent)
            }

            is ActionSpec.ClickText -> {
                val wanted = text(action.text)
                val node = awaitNode(action.timeoutMs) {
                    UiAutomator.matchesText(it, wanted, action.exact)
                } ?: throw ActionFailure("no view showing \"$wanted\"")
                if (!UiAutomator.click(node)) {
                    throw ActionFailure("\"$wanted\" was found but nothing around it is clickable")
                }
            }

            is ActionSpec.LongClickText -> {
                val wanted = text(action.text)
                val node = awaitNode(action.timeoutMs) {
                    UiAutomator.matchesText(it, wanted, action.exact)
                } ?: throw ActionFailure("no view showing \"$wanted\"")
                if (!UiAutomator.longClick(node)) throw ActionFailure("\"$wanted\" ignored a long press")
            }

            is ActionSpec.ClickDescription -> {
                val wanted = text(action.description)
                val node = awaitNode(action.timeoutMs) {
                    it.contentDescription?.toString()?.contains(wanted, ignoreCase = true) == true
                } ?: throw ActionFailure("no view described as \"$wanted\"")
                if (!UiAutomator.click(node)) throw ActionFailure("\"$wanted\" is not clickable")
            }

            is ActionSpec.ClickViewId -> {
                val node = awaitNode(action.timeoutMs) {
                    UiAutomator.matchesViewId(it, action.viewId)
                } ?: throw ActionFailure("no view with id ${action.viewId}")
                if (!UiAutomator.click(node)) throw ActionFailure("${action.viewId} is not clickable")
            }

            is ActionSpec.SetText -> setText(action, run)

            is ActionSpec.WaitForText -> {
                val wanted = text(action.text)
                awaitNode(action.timeoutMs) { UiAutomator.matchesText(it, wanted, exact = false) }
                    ?: throw ActionFailure("\"$wanted\" never appeared")
            }

            is ActionSpec.WaitForApp -> {
                val deadline = System.currentTimeMillis() + action.timeoutMs
                while (System.currentTimeMillis() < deadline) {
                    if (AutoFlowAccessibilityService.foregroundPackage == action.packageName) return
                    delay(200)
                }
                throw ActionFailure("${action.packageName} did not come to the foreground")
            }

            is ActionSpec.Scroll -> {
                val forward = action.direction == ScrollDirection.FORWARD
                val node = awaitNode(action.timeoutMs) { it.isScrollable }
                    ?: throw ActionFailure("nothing scrollable on screen")
                val command = if (forward) {
                    AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                } else {
                    AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                }
                if (!node.performAction(command)) throw ActionFailure("the list refused to scroll")
            }

            is ActionSpec.Tap -> {
                val service = requireService()
                if (!UiAutomator.dispatch(service, UiAutomator.tapPath(action.x, action.y), 50)) {
                    throw ActionFailure("tap gesture was rejected")
                }
            }

            is ActionSpec.LongPress -> {
                val service = requireService()
                val path = UiAutomator.tapPath(action.x, action.y)
                if (!UiAutomator.dispatch(service, path, action.durationMs)) {
                    throw ActionFailure("long press was rejected")
                }
            }

            is ActionSpec.Swipe -> {
                val service = requireService()
                val path = UiAutomator.swipePath(action.x1, action.y1, action.x2, action.y2)
                if (!UiAutomator.dispatch(service, path, action.durationMs)) {
                    throw ActionFailure("swipe gesture was rejected")
                }
            }

            is ActionSpec.Global -> {
                val service = requireService()
                val code = when (action.action) {
                    GlobalActionType.BACK -> AccessibilityService.GLOBAL_ACTION_BACK
                    GlobalActionType.HOME -> AccessibilityService.GLOBAL_ACTION_HOME
                    GlobalActionType.RECENTS -> AccessibilityService.GLOBAL_ACTION_RECENTS
                    GlobalActionType.NOTIFICATIONS -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
                    GlobalActionType.QUICK_SETTINGS -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
                    GlobalActionType.LOCK_SCREEN -> AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
                    GlobalActionType.POWER_DIALOG -> AccessibilityService.GLOBAL_ACTION_POWER_DIALOG
                }
                if (!service.performGlobalAction(code)) {
                    throw ActionFailure("${action.action} was rejected by the system")
                }
            }

            ActionSpec.Screenshot -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    throw ActionFailure("screenshots need Android 11 or newer")
                }
                requireService().performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
            }

            is ActionSpec.CopyToClipboard -> {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                    ?: throw ActionFailure("clipboard unavailable")
                clipboard.setPrimaryClip(ClipData.newPlainText("AutoFlow", text(action.text)))
            }

            is ActionSpec.ReadClipboard -> {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                val value = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                run.set(action.variable, value.orEmpty())
            }

            is ActionSpec.SetVariable -> run.set(action.name, text(action.value))

            is ActionSpec.ExtractRegex -> {
                val source = text(action.source)
                val result = runCatching {
                    Regex(action.pattern).find(source)?.groupValues?.getOrNull(action.group)
                }.getOrNull()
                    ?: throw ActionFailure("pattern did not match \"${source.take(60)}\"")
                run.set(action.variable, result)
            }

            is ActionSpec.ReadScreenText -> {
                val service = requireService()
                val screen = UiAutomator.collectText(service.rootInActiveWindow)
                run.set(action.variable, screen)
            }

            is ActionSpec.ShareText -> {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text(action.text))
                    if (action.packageName.isNotBlank()) setPackage(action.packageName)
                }
                startActivity(intent)
            }

            is ActionSpec.HttpRequest -> performHttp(action, run)

            is ActionSpec.SendSms -> sendSms(text(action.to), text(action.body))

            is ActionSpec.Dial ->
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${text(action.number)}")))

            is ActionSpec.Notify -> Notifier.post(context, text(action.title), text(action.message))

            is ActionSpec.Toast -> withContext(Dispatchers.Main) {
                AndroidToast.makeText(context, text(action.text), AndroidToast.LENGTH_SHORT).show()
            }

            is ActionSpec.Speak -> Speaker.speak(context, text(action.text))

            is ActionSpec.Vibrate -> vibrate(action.millis)

            is ActionSpec.SetVolume -> {
                val audio = context.getSystemService(AudioManager::class.java)
                    ?: throw ActionFailure("audio service unavailable")
                val stream = when (action.stream) {
                    VolumeStream.MUSIC -> AudioManager.STREAM_MUSIC
                    VolumeStream.RING -> AudioManager.STREAM_RING
                    VolumeStream.NOTIFICATION -> AudioManager.STREAM_NOTIFICATION
                    VolumeStream.ALARM -> AudioManager.STREAM_ALARM
                }
                val max = audio.getStreamMaxVolume(stream)
                val target = (max * action.percent.coerceIn(0, 100)) / 100
                runCatching { audio.setStreamVolume(stream, target, 0) }
                    .onFailure { throw ActionFailure("volume change refused: ${it.message}") }
            }

            is ActionSpec.Media -> {
                val audio = context.getSystemService(AudioManager::class.java)
                    ?: throw ActionFailure("audio service unavailable")
                val key = when (action.command) {
                    MediaCommand.PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                    MediaCommand.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
                    MediaCommand.PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                    MediaCommand.STOP -> KeyEvent.KEYCODE_MEDIA_STOP
                }
                audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
                audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
            }

            ActionSpec.WakeScreen -> wakeScreen()

            is ActionSpec.OpenSettingsPanel -> {
                val intentAction = when (action.panel) {
                    SettingsPanel.INTERNET -> Settings.Panel.ACTION_INTERNET_CONNECTIVITY
                    SettingsPanel.WIFI -> Settings.Panel.ACTION_WIFI
                    SettingsPanel.NFC -> Settings.Panel.ACTION_NFC
                    SettingsPanel.VOLUME -> Settings.Panel.ACTION_VOLUME
                }
                startActivity(Intent(intentAction))
            }

            is ActionSpec.RunRule -> runNestedRule(action.ruleName, run)

            is ActionSpec.Log -> run.set("_log", text(action.message))

            is ActionSpec.WhatsAppSend -> {
                val phone = text(action.phone).replace("[^0-9+]".toRegex(), "").removePrefix("+")
                val msg = text(action.message)
                val encodedMsg = URLEncoder.encode(msg, "UTF-8")
                val url = "https://wa.me/$phone?text=$encodedMsg"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    setPackage(KnownPackages.WHATSAPP)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                delay(2500)
                runCatching {
                    val sendNode = awaitNode(6_000) { node ->
                        node.contentDescription?.toString()?.contains("Send", ignoreCase = true) == true ||
                            node.contentDescription?.toString()?.contains("إرسال", ignoreCase = true) == true ||
                            node.viewIdResourceName?.contains("send", ignoreCase = true) == true
                    }
                    if (sendNode != null) UiAutomator.click(sendNode)
                }
            }

            is ActionSpec.WhatsAppReply -> {
                val msg = text(action.message)
                launchApp(KnownPackages.WHATSAPP)
                delay(1500)
                if (run.event.title.isNotBlank()) {
                    runCatching {
                        val senderNode = awaitNode(5_000) { UiAutomator.matchesText(it, run.event.title, false) }
                        if (senderNode != null) UiAutomator.click(senderNode)
                    }
                    delay(1000)
                }
                setText(
                    ActionSpec.SetText(
                        text = msg,
                        target = TextFieldTarget.BY_HINT,
                        selector = "message",
                        timeoutMs = 6_000,
                    ),
                    run,
                )
                delay(800)
                val sendNode = awaitNode(5_000) { node ->
                    node.contentDescription?.toString()?.contains("Send", ignoreCase = true) == true ||
                        node.contentDescription?.toString()?.contains("إرسال", ignoreCase = true) == true ||
                        node.viewIdResourceName?.contains("send", ignoreCase = true) == true
                } ?: throw ActionFailure("Could not find WhatsApp send button")
                UiAutomator.click(sendNode)
            }

            is ActionSpec.WhatsAppForward -> {
                val phone = text(action.phone).replace("[^0-9+]".toRegex(), "").removePrefix("+")
                val msg = run.event.text
                val encodedMsg = URLEncoder.encode(msg, "UTF-8")
                val url = "https://wa.me/$phone?text=$encodedMsg"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    setPackage(KnownPackages.WHATSAPP)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                delay(2500)
                runCatching {
                    val sendNode = awaitNode(6_000) { node ->
                        node.contentDescription?.toString()?.contains("Send", ignoreCase = true) == true ||
                            node.contentDescription?.toString()?.contains("إرسال", ignoreCase = true) == true ||
                            node.viewIdResourceName?.contains("send", ignoreCase = true) == true
                    }
                    if (sendNode != null) UiAutomator.click(sendNode)
                }
            }

            is ActionSpec.TelegramSend -> {
                val token = text(action.botToken)
                val chatId = text(action.chatId)
                val msg = text(action.message)
                val url = "https://api.telegram.org/bot$token/sendMessage"
                val jsonBody = """{"chat_id":"$chatId","text":"${Variables.jsonEscape(msg)}"}"""
                val request = Request.Builder()
                    .url(url)
                    .post(jsonBody.toRequestBody("application/json".toMediaType()))
                    .build()
                withContext(Dispatchers.IO) {
                    http.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw ActionFailure("Telegram API error: ${response.code} - ${response.body?.string()?.take(100)}")
                        }
                    }
                }
            }

            is ActionSpec.TelegramReply -> {
                val msg = text(action.message)
                launchApp(KnownPackages.TELEGRAM)
                delay(1500)
                if (run.event.title.isNotBlank()) {
                    runCatching {
                        val senderNode = awaitNode(5_000) { UiAutomator.matchesText(it, run.event.title, false) }
                        if (senderNode != null) UiAutomator.click(senderNode)
                    }
                    delay(1000)
                }
                setText(
                    ActionSpec.SetText(
                        text = msg,
                        target = TextFieldTarget.BY_HINT,
                        selector = "message",
                        timeoutMs = 6_000,
                    ),
                    run,
                )
                delay(800)
                val sendNode = awaitNode(5_000) { node ->
                    node.contentDescription?.toString()?.contains("Send", ignoreCase = true) == true ||
                        node.contentDescription?.toString()?.contains("إرسال", ignoreCase = true) == true
                } ?: throw ActionFailure("Could not find Telegram send button")
                UiAutomator.click(sendNode)
            }

            is ActionSpec.InstagramDirect -> {
                val user = text(action.username).trim().removePrefix("@")
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ig.me/m/$user")).apply {
                    setPackage(KnownPackages.INSTAGRAM)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }

            is ActionSpec.YouTubeOpen -> {
                val query = text(action.queryOrUrl).trim()
                val uri = if (query.startsWith("http://") || query.startsWith("https://")) {
                    Uri.parse(query)
                } else {
                    Uri.parse("https://www.youtube.com/results?search_query=${URLEncoder.encode(query, "UTF-8")}")
                }
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage(KnownPackages.YOUTUBE)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }

            is ActionSpec.XPost -> {
                val tweet = text(action.tweet)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, tweet)
                    setPackage(KnownPackages.X)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }

            // Handled by the interpreter in run(); unreachable here.
            is ActionSpec.If, ActionSpec.Else, ActionSpec.EndIf,
            is ActionSpec.Repeat, ActionSpec.EndRepeat, ActionSpec.StopRule -> Unit
        }
    }

    private suspend fun setText(action: ActionSpec.SetText, run: RunContext) {
        val value = Variables.resolve(action.text, run)
        val selector = Variables.resolve(action.selector, run)

        val node = when (action.target) {
            TextFieldTarget.FOCUSED -> awaitNode(action.timeoutMs) {
                it.isFocused && UiAutomator.isEditable(it)
            }

            TextFieldTarget.FIRST_EDITABLE -> awaitNode(action.timeoutMs) {
                UiAutomator.isEditable(it)
            }

            TextFieldTarget.BY_HINT -> awaitNode(action.timeoutMs) { candidate ->
                UiAutomator.isEditable(candidate) &&
                    listOfNotNull(
                        candidate.hintText?.toString(),
                        candidate.contentDescription?.toString(),
                        candidate.text?.toString(),
                    ).any { it.contains(selector, ignoreCase = true) }
            }

            TextFieldTarget.BY_VIEW_ID -> awaitNode(action.timeoutMs) {
                UiAutomator.matchesViewId(it, selector)
            }
        } ?: throw ActionFailure("no text field matched ${action.target}")

        if (!UiAutomator.setText(node, value)) throw ActionFailure("the field refused the text")
    }

    private suspend fun performHttp(action: ActionSpec.HttpRequest, run: RunContext) {
        val url = Variables.resolve(action.url, run)
        val body = Variables.resolve(action.body, run)
        val headers = Variables.resolve(action.headers, run)
        val method = action.method.uppercase()

        val requestBody = if (method == "GET" || method == "HEAD") {
            null
        } else {
            body.toRequestBody(action.contentType.toMediaType())
        }

        val request = Request.Builder()
            .url(url)
            .method(method, requestBody)
            .apply { headers.forEach { (name, value) -> addHeader(name, value) } }
            .build()

        val payload = withContext(Dispatchers.IO) {
            val response = try {
                http.newCall(request).execute()
            } catch (io: Exception) {
                throw ActionFailure("request failed: ${io.message}")
            }
            response.use {
                val text = runCatching { it.body?.string().orEmpty() }.getOrDefault("")
                if (!it.isSuccessful) throw ActionFailure("HTTP ${it.code} ${text.take(200)}")
                text
            }
        }

        if (action.saveResponseTo.isNotBlank()) run.set(action.saveResponseTo, payload)
    }

    private fun sendSms(to: String, body: String) {
        if (to.isBlank()) throw ActionFailure("no recipient")
        val manager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        } ?: throw ActionFailure("SMS unavailable on this device")

        runCatching {
            // Long messages must be split or the send silently fails.
            val parts = manager.divideMessage(body)
            manager.sendMultipartTextMessage(to, null, parts, null, null)
        }.onFailure { throw ActionFailure("could not send SMS: ${it.message}") }
    }

    private fun vibrate(millis: Long) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        } ?: return
        vibrator.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    @Suppress("DEPRECATION")
    private fun wakeScreen() {
        val power = context.getSystemService(PowerManager::class.java) ?: return
        val lock = power.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "autoflow:wake",
        )
        lock.acquire(5_000)
        lock.release()
    }

    private fun launchApp(packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: throw ActionFailure("$packageName is not installed")
        startActivity(intent)
    }

    private fun startActivity(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (error: Exception) {
            throw ActionFailure("could not start activity: ${error.message}")
        }
    }

    private suspend fun awaitNode(
        timeoutMs: Long,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? = UiAutomator.awaitNode(requireService(), timeoutMs, predicate)

    private fun requireService(): AutoFlowAccessibilityService =
        AutoFlowAccessibilityService.instance
            ?: throw ActionFailure("the accessibility service is off; enable AutoFlow in Settings")

    private companion object {
        const val MAX_STEPS = 5_000
    }
}
