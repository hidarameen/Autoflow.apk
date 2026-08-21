package com.autoflow.app.ui.catalog

/** How a single editable field is rendered in the rule editor. */
enum class FieldType {
    TEXT,
    MULTILINE,
    NUMBER,
    BOOL,
    APP,
    ENUM,
    /** A MatchSpec: a mode dropdown plus a value box, stored under `key` and `key_mode`. */
    MATCH,
    /** Comma-free day picker for Calendar day numbers, stored as "2,3,4". */
    DAYS,
}

data class FieldDef(
    val key: String,
    val label: String,
    val type: FieldType,
    val help: String = "",
    val options: List<String> = emptyList(),
)

/** Reads typed values out of the flat string map the editor produces. */
object Fields {

    fun text(values: Map<String, String>, key: String, fallback: String = ""): String =
        values[key] ?: fallback

    fun long(values: Map<String, String>, key: String, fallback: Long): Long =
        values[key]?.trim()?.toLongOrNull() ?: fallback

    fun int(values: Map<String, String>, key: String, fallback: Int): Int =
        values[key]?.trim()?.toIntOrNull() ?: fallback

    fun bool(values: Map<String, String>, key: String, fallback: Boolean): Boolean =
        values[key]?.toBooleanStrictOrNull() ?: fallback

    inline fun <reified E : Enum<E>> enum(values: Map<String, String>, key: String, fallback: E): E =
        runCatching { enumValueOf<E>(values[key].orEmpty()) }.getOrDefault(fallback)

    /** "2,3,4" to [2, 3, 4]; blank means every day. */
    fun days(values: Map<String, String>, key: String, fallback: List<Int>): List<Int> {
        val raw = values[key] ?: return fallback
        val parsed = raw.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..7 }
        return parsed.ifEmpty { fallback }
    }

    fun daysToString(days: List<Int>): String = days.joinToString(",")

    /** One "Name: value" per line, which is far easier to edit than JSON. */
    fun headers(raw: String): Map<String, String> =
        raw.lines()
            .mapNotNull { line ->
                val separator = line.indexOf(':')
                if (separator <= 0) return@mapNotNull null
                line.substring(0, separator).trim() to line.substring(separator + 1).trim()
            }
            .filter { it.first.isNotEmpty() }
            .toMap()

    fun headersToString(headers: Map<String, String>): String =
        headers.entries.joinToString("\n") { "${it.key}: ${it.value}" }

    /** A single package name is stored as a comma list so the field can stay one text box. */
    fun packages(values: Map<String, String>, key: String): List<String> =
        text(values, key).split(',').map { it.trim() }.filter { it.isNotEmpty() }
}
