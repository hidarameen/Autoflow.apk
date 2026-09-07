package com.autoflow.app.trigger

import android.util.Log
import com.autoflow.app.data.Rule
import com.autoflow.app.data.TriggerSpec
import com.autoflow.app.engine.RuleEngine
import com.autoflow.app.engine.TriggerEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Polls the Telegram Bot API for new messages.
 *
 * This is the notification-free, server-side path: it talks to Telegram directly over
 * HTTPS, so it keeps working with the screen off, with Telegram uninstalled, and with
 * notifications disabled. Long polling means the request parks on Telegram's side until a
 * message arrives, which costs far less battery than repeatedly asking.
 *
 * The bot must be an administrator of any channel it should read.
 */
class TelegramBotPoller(
    private val context: android.content.Context,
    private val scope: CoroutineScope,
) {

    private val client = OkHttpClient.Builder()
        // Must exceed the long-poll timeout or every wait would look like a failure.
        .readTimeout(LONG_POLL_SECONDS + 15L, TimeUnit.SECONDS)
        .connectTimeout(20, TimeUnit.SECONDS)
        .build()

    /** One poll loop per distinct bot token. */
    private val loops = mutableMapOf<String, Job>()

    /** Telegram's cursor: only updates newer than this are returned. */
    private val offsets = mutableMapOf<String, Long>()

    /** Restarts the loops so they exactly match the enabled rules. */
    fun sync(rules: List<Rule>) {
        val wanted = rules
            .filter { it.enabled }
            .mapNotNull { it.trigger as? TriggerSpec.TelegramBot }
            .map { it.token }
            .filter { it.isNotBlank() }
            .toSet()

        (loops.keys - wanted).forEach { token ->
            loops.remove(token)?.cancel()
        }
        wanted.filter { it !in loops }.forEach { token ->
            loops[token] = scope.launch { poll(token) }
        }
    }

    fun stop() {
        loops.values.forEach { it.cancel() }
        loops.clear()
    }

    private suspend fun poll(token: String) {
        while (scope.isActive) {
            try {
                val offset = offsets[token] ?: 0L
                val body = request(token, offset)
                if (body != null) handleUpdates(token, body)
            } catch (error: Exception) {
                // A network blip must not kill the loop; back off and try again.
                Log.w(TAG, "poll failed: ${error.message}")
                delay(ERROR_BACKOFF_MS)
            }
        }
    }

    private suspend fun request(token: String, offset: Long): String? = withContext(Dispatchers.IO) {
        val url = "https://api.telegram.org/bot$token/getUpdates" +
            "?timeout=$LONG_POLL_SECONDS" +
            if (offset > 0) "&offset=$offset" else ""

        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "getUpdates HTTP ${response.code}")
                delay(ERROR_BACKOFF_MS)
                return@withContext null
            }
            response.body?.string()
        }
    }

    /**
     * Picks the media out of an update, if any.
     *
     * A photo arrives as an array of sizes; the last entry is the largest, which is the one
     * worth forwarding. Everything else exposes a single file object.
     */
    private fun findMedia(message: JSONObject): Pair<String, String>? {
        message.optJSONArray("photo")?.let { sizes ->
            val largest = sizes.optJSONObject(sizes.length() - 1)
            val id = largest?.optString("file_id").orEmpty()
            if (id.isNotBlank()) return id to "image/jpeg"
        }
        for ((key, mime) in MEDIA_KINDS) {
            val obj = message.optJSONObject(key) ?: continue
            val id = obj.optString("file_id")
            if (id.isBlank()) continue
            // Telegram reports the real type for documents; the rest have a fixed kind.
            val type = obj.optString("mime_type").ifBlank { mime }
            return id to type
        }
        return null
    }

    /** Resolves a file_id to a local content:// URI other apps can read. */
    private suspend fun download(token: String, fileId: String, mime: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val infoUrl = "https://api.telegram.org/bot$token/getFile?file_id=$fileId"
                val info = client.newCall(Request.Builder().url(infoUrl).build()).execute()
                    .use { if (!it.isSuccessful) return@withContext null else it.body?.string() }
                    ?: return@withContext null

                val path = JSONObject(info).optJSONObject("result")?.optString("file_path")
                if (path.isNullOrBlank()) return@withContext null

                val dir = java.io.File(context.cacheDir, "telegram").apply { mkdirs() }
                // Keep the original extension so the receiving app recognises the format.
                val target = java.io.File(dir, "tg_${System.currentTimeMillis()}_${path.substringAfterLast('/')}")

                val fileUrl = "https://api.telegram.org/file/bot$token/$path"
                client.newCall(Request.Builder().url(fileUrl).build()).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    response.body?.byteStream()?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    } ?: return@withContext null
                }

                // Old downloads would otherwise accumulate in the cache forever.
                dir.listFiles()
                    ?.sortedByDescending { it.lastModified() }
                    ?.drop(KEEP_FILES)
                    ?.forEach { runCatching { it.delete() } }

                androidx.core.content.FileProvider.getUriForFile(
                    context, "${context.packageName}.files", target,
                ).toString()
            } catch (error: Exception) {
                Log.w(TAG, "media download failed: ${error.message}")
                null
            }
        }

    private suspend fun handleUpdates(token: String, payload: String) {
        val root = JSONObject(payload)
        if (!root.optBoolean("ok")) return
        val updates = root.optJSONArray("result") ?: return

        // Pass 1: acknowledge every update and pull out its message object.
        val messages = ArrayList<JSONObject>()
        for (i in 0 until updates.length()) {
            val update = updates.optJSONObject(i) ?: continue
            // Move the cursor past this update, or Telegram resends it.
            offsets[token] = update.optLong("update_id") + 1
            (update.optJSONObject("message") ?: update.optJSONObject("channel_post"))
                ?.let { messages.add(it) }
        }

        // Pass 2: an album arrives as several consecutive messages sharing one
        // media_group_id. Fold them into a single event so the rule forwards one
        // carousel instead of firing once per photo.
        var i = 0
        while (i < messages.size) {
            val groupId = messages[i].optString("media_group_id")
            var j = i + 1
            if (groupId.isNotBlank()) {
                while (j < messages.size && messages[j].optString("media_group_id") == groupId) j++
            }
            dispatch(token, messages.subList(i, j))
            i = j
        }
    }

    /** Turns one message, or one album's worth of messages, into a single trigger event. */
    private suspend fun dispatch(token: String, group: List<JSONObject>) {
        val head = group.first()

        // The caption usually rides on the first item; take whichever one carries it.
        val text = group.firstNotNullOfOrNull { m ->
            m.optString("text").ifBlank { m.optString("caption") }.takeIf { it.isNotBlank() }
        }.orEmpty()

        val medias = group.mapNotNull { findMedia(it) }

        // A photo with no caption is still worth forwarding; skip only when there is neither.
        if (text.isBlank() && medias.isEmpty()) return

        val uris = ArrayList<String>()
        for (m in medias) download(token, m.first, m.second)?.let { uris.add(it) }

        val chat = head.optJSONObject("chat")
        val chatTitle = chat?.optString("title").orEmpty()
        val from = head.optJSONObject("from")
        val sender = listOfNotNull(
            from?.optString("first_name")?.takeIf { it.isNotBlank() },
            from?.optString("last_name")?.takeIf { it.isNotBlank() },
        ).joinToString(" ").ifBlank { chatTitle }

        RuleEngine.submit(
            TriggerEvent(
                source = TriggerEvent.Source.TELEGRAM_BOT,
                packageName = "telegram.bot",
                appLabel = "Telegram Bot",
                title = sender,
                text = text,
                tag = chat?.optLong("id")?.toString().orEmpty(),
                // Several URIs joined by "," — ActionExecutor.shareMedia splits them back
                // out and posts an album via ACTION_SEND_MULTIPLE.
                mediaUri = uris.joinToString(","),
                mediaType = medias.firstOrNull()?.second.orEmpty(),
            )
        )
    }

    private companion object {
        const val TAG = "AutoFlowTgBot"
        const val LONG_POLL_SECONDS = 50
        const val ERROR_BACKOFF_MS = 5_000L
        const val KEEP_FILES = 20

        /** Update keys that carry a single file, with the type to assume when unstated. */
        val MEDIA_KINDS = listOf(
            "video" to "video/mp4",
            "voice" to "audio/ogg",
            "audio" to "audio/mpeg",
            "animation" to "video/mp4",
            "document" to "application/octet-stream",
        )
    }
}
