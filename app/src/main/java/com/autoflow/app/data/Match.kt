package com.autoflow.app.data

import kotlinx.serialization.Serializable

/** How a piece of text is compared. [ANY] matches everything and is the default "no filter". */
@Serializable
enum class MatchMode(val display: String) {
    ANY("anything"),
    CONTAINS("contains"),
    NOT_CONTAINS("does not contain"),
    EQUALS("is exactly"),
    STARTS_WITH("starts with"),
    ENDS_WITH("ends with"),
    REGEX("matches regex"),
    PHONE("is the phone number"),
    IS_EMPTY("is empty"),
    IS_NOT_EMPTY("is not empty"),
}

/** Fields of a trigger event that a filter can read. */
@Serializable
enum class EventField(val display: String) {
    TEXT("message body"),
    TITLE("sender / title"),
    PACKAGE("package name"),
    APP("app name"),
}

/**
 * One reusable text test. Used by triggers (which message should start this rule?) and by
 * conditions (should this rule proceed?), so filtering behaves identically in both places.
 */
@Serializable
data class MatchSpec(
    val mode: MatchMode = MatchMode.ANY,
    val value: String = "",
    val ignoreCase: Boolean = true,
) {
    fun matches(input: String): Boolean = when (mode) {
        MatchMode.ANY -> true
        MatchMode.IS_EMPTY -> input.isBlank()
        MatchMode.IS_NOT_EMPTY -> input.isNotBlank()
        MatchMode.CONTAINS -> value.isBlank() || input.contains(value, ignoreCase)
        MatchMode.NOT_CONTAINS -> value.isBlank() || !input.contains(value, ignoreCase)
        MatchMode.EQUALS -> input.equals(value, ignoreCase)
        MatchMode.STARTS_WITH -> input.startsWith(value, ignoreCase)
        MatchMode.ENDS_WITH -> input.endsWith(value, ignoreCase)
        // A malformed pattern must not take the whole engine down, so it simply fails to match.
        // Unsaved senders arrive as a formatted number, so compare digits from the right.
        MatchMode.PHONE -> com.autoflow.app.trigger.PhoneMatch.sameNumber(input, value)

        MatchMode.REGEX -> runCatching {
            val options = if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
            Regex(value, options).containsMatchIn(input)
        }.getOrDefault(false)
    }

    val isFilter: Boolean get() = mode != MatchMode.ANY

    val label: String
        get() = when (mode) {
            MatchMode.ANY -> "any"
            MatchMode.IS_EMPTY, MatchMode.IS_NOT_EMPTY -> mode.display
            else -> "${mode.display} \"$value\""
        }

    companion object {
        val ANY = MatchSpec()

        fun contains(value: String) = MatchSpec(MatchMode.CONTAINS, value)
    }
}
