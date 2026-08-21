package com.autoflow.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** How a rule's condition list is combined. */
@Serializable
enum class ConditionLogic { ALL, ANY }

/**
 * A guard evaluated after the trigger fires but before any action runs. Conditions are what
 * turn "on every Telegram message" into "on every Telegram message from Ali, on a weekday,
 * during work hours, while charging".
 */
@Serializable
sealed interface ConditionSpec {

    /** Tests a field of the event that started the rule. */
    @Serializable
    @SerialName("text")
    data class Text(
        val field: EventField = EventField.TEXT,
        val match: MatchSpec = MatchSpec.ANY,
    ) : ConditionSpec

    /** Only between two times of day, in minutes since midnight. Wraps past midnight. */
    @Serializable
    @SerialName("time_window")
    data class TimeWindow(
        val startMinutes: Int = 9 * 60,
        val endMinutes: Int = 17 * 60,
    ) : ConditionSpec

    /** Days are java.util.Calendar values: Sunday = 1 ... Saturday = 7. */
    @Serializable
    @SerialName("day_of_week")
    data class DayOfWeek(val days: List<Int> = listOf(2, 3, 4, 5, 6)) : ConditionSpec

    @Serializable
    @SerialName("screen_state")
    data class ScreenState(val on: Boolean = true) : ConditionSpec

    @Serializable
    @SerialName("charging")
    data class Charging(val charging: Boolean = true) : ConditionSpec

    @Serializable
    @SerialName("battery")
    data class BatteryLevel(val below: Boolean = true, val percent: Int = 20) : ConditionSpec

    @Serializable
    @SerialName("wifi")
    data class WifiConnected(val connected: Boolean = true) : ConditionSpec

    /** True while [packageName] is the app on screen. */
    @Serializable
    @SerialName("foreground_app")
    data class ForegroundApp(val packageName: String = "") : ConditionSpec

    /** Tests a variable set earlier in the same run by SetVariable. */
    @Serializable
    @SerialName("variable")
    data class Variable(
        val name: String = "",
        val match: MatchSpec = MatchSpec.ANY,
    ) : ConditionSpec

    @Serializable
    @SerialName("app_installed")
    data class AppInstalled(val packageName: String = "", val installed: Boolean = true) : ConditionSpec
}

val ConditionSpec.label: String
    get() = when (this) {
        is ConditionSpec.Text -> "${field.display} ${match.label}"
        is ConditionSpec.TimeWindow -> "between ${formatMinutes(startMinutes)} and ${formatMinutes(endMinutes)}"
        is ConditionSpec.DayOfWeek -> "on ${days.joinToString { dayName(it) }}"
        is ConditionSpec.ScreenState -> if (on) "screen is on" else "screen is off"
        is ConditionSpec.Charging -> if (charging) "charging" else "not charging"
        is ConditionSpec.BatteryLevel -> "battery ${if (below) "below" else "above"} $percent%"
        is ConditionSpec.WifiConnected -> if (connected) "Wi-Fi connected" else "Wi-Fi disconnected"
        is ConditionSpec.ForegroundApp -> "$packageName is in the foreground"
        is ConditionSpec.Variable -> "variable $name ${match.label}"
        is ConditionSpec.AppInstalled ->
            "$packageName is ${if (installed) "installed" else "not installed"}"
    }

private fun formatMinutes(total: Int): String =
    "%02d:%02d".format((total / 60) % 24, total % 60)

private fun dayName(day: Int): String = when (day) {
    1 -> "Sun"
    2 -> "Mon"
    3 -> "Tue"
    4 -> "Wed"
    5 -> "Thu"
    6 -> "Fri"
    7 -> "Sat"
    else -> "?"
}
