package com.autoflow.app.engine

import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Substitutes `{{name}}` placeholders in action fields with values from the trigger event
 * or from variables set earlier in the run. This is what carries a Telegram message body
 * into a tweet, an HTTP payload, or a text field.
 *
 * Built-in names: text, title, package, app, time, date, datetime, timestamp.
 * Anything else resolves against the run's variables.
 *
 * Modifiers chain with `|`: `url`, `json`, `upper`, `lower`, `trim:N`, `line:N`, `default:X`.
 */
object Variables {

    private val PATTERN = Regex("""\{\{\s*([a-zA-Z_][a-zA-Z0-9_]*)((?:\s*\|[^}]*)*)\}\}""")

    fun resolve(template: String, context: RunContext): String =
        PATTERN.replace(template) { match ->
            val name = match.groupValues[1]
            val modifiers = match.groupValues[2]
                .split('|')
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            var value = baseValue(name, context) ?: ""
            for (modifier in modifiers) value = applyModifier(value, modifier)
            value
        }

    fun resolve(map: Map<String, String>, context: RunContext): Map<String, String> =
        map.mapValues { (_, v) -> resolve(v, context) }

    private fun baseValue(name: String, context: RunContext): String? {
        val event = context.event
        return when (name.lowercase()) {
            "text", "message", "body" -> event.text
            "title", "sender" -> event.title
            "package", "pkg" -> event.packageName
            "app" -> event.appLabel
            "time" -> format("HH:mm:ss", event.timestamp)
            "date" -> format("yyyy-MM-dd", event.timestamp)
            "datetime" -> format("yyyy-MM-dd HH:mm:ss", event.timestamp)
            "timestamp" -> event.timestamp.toString()
            else -> context.get(name)
        }
    }

    private fun applyModifier(value: String, modifier: String): String = when {
        modifier == "url" -> Uri.encode(value)
        modifier == "json" -> jsonEscape(value)
        modifier == "upper" -> value.uppercase()
        modifier == "lower" -> value.lowercase()
        modifier == "trimspace" -> value.trim()

        modifier.startsWith("trim:") -> {
            val limit = modifier.removePrefix("trim:").trim().toIntOrNull() ?: value.length
            if (value.length <= limit) value else value.take(limit)
        }

        modifier.startsWith("line:") -> {
            val index = modifier.removePrefix("line:").trim().toIntOrNull() ?: 0
            value.lines().getOrElse(index) { "" }
        }

        // Fills in a fallback when the variable resolved to nothing.
        modifier.startsWith("default:") -> value.ifBlank { modifier.removePrefix("default:") }

        else -> value
    }

    /** Escapes a value so it can be dropped straight into a JSON string literal. */
    fun jsonEscape(value: String): String = buildString(value.length + 16) {
        for (c in value) when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
        }
    }

    private fun format(pattern: String, millis: Long): String =
        SimpleDateFormat(pattern, Locale.US).format(Date(millis))
}
