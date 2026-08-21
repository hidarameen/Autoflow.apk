package com.autoflow.app.ui.catalog

import com.autoflow.app.data.ConditionSpec
import com.autoflow.app.data.EventField
import com.autoflow.app.data.MatchMode
import com.autoflow.app.data.MatchSpec

data class ConditionDef(
    val title: String,
    val help: String = "",
    val fields: List<FieldDef> = emptyList(),
    val create: () -> ConditionSpec,
    val read: (ConditionSpec) -> Map<String, String> = { emptyMap() },
    val write: (Map<String, String>) -> ConditionSpec,
)

/** Registry for rule guards, used both by the rule editor and by the If action. */
object ConditionCatalog {

    val all: List<ConditionDef> = listOf(

        ConditionDef(
            title = "Message text matches",
            help = "Tests a field of the event that started the rule.",
            fields = listOf(
                FieldDef("field", "Field", FieldType.ENUM, options = EventField.entries.map { it.name }),
                FieldDef("mode", "Test", FieldType.ENUM, options = MatchMode.entries.map { it.name }),
                FieldDef("value", "Value", FieldType.TEXT),
            ),
            create = { ConditionSpec.Text() },
            read = {
                (it as ConditionSpec.Text).let { c ->
                    mapOf(
                        "field" to c.field.name,
                        "mode" to c.match.mode.name,
                        "value" to c.match.value,
                    )
                }
            },
            write = { v ->
                ConditionSpec.Text(
                    field = Fields.enum(v, "field", EventField.TEXT),
                    match = MatchSpec(
                        mode = Fields.enum(v, "mode", MatchMode.CONTAINS),
                        value = Fields.text(v, "value"),
                    ),
                )
            },
        ),

        ConditionDef(
            title = "Within a time window",
            help = "Minutes are 0-59. A start later than the end wraps past midnight.",
            fields = listOf(
                FieldDef("startHour", "From hour", FieldType.NUMBER),
                FieldDef("startMinute", "From minute", FieldType.NUMBER),
                FieldDef("endHour", "To hour", FieldType.NUMBER),
                FieldDef("endMinute", "To minute", FieldType.NUMBER),
            ),
            create = { ConditionSpec.TimeWindow() },
            read = {
                (it as ConditionSpec.TimeWindow).let { c ->
                    mapOf(
                        "startHour" to (c.startMinutes / 60).toString(),
                        "startMinute" to (c.startMinutes % 60).toString(),
                        "endHour" to (c.endMinutes / 60).toString(),
                        "endMinute" to (c.endMinutes % 60).toString(),
                    )
                }
            },
            write = { v ->
                ConditionSpec.TimeWindow(
                    startMinutes = Fields.int(v, "startHour", 9).coerceIn(0, 23) * 60 +
                        Fields.int(v, "startMinute", 0).coerceIn(0, 59),
                    endMinutes = Fields.int(v, "endHour", 17).coerceIn(0, 23) * 60 +
                        Fields.int(v, "endMinute", 0).coerceIn(0, 59),
                )
            },
        ),

        ConditionDef(
            title = "On certain days",
            help = "1 is Sunday through 7 for Saturday.",
            fields = listOf(FieldDef("days", "Days", FieldType.DAYS)),
            create = { ConditionSpec.DayOfWeek() },
            read = {
                (it as ConditionSpec.DayOfWeek).let { c ->
                    mapOf("days" to Fields.daysToString(c.days))
                }
            },
            write = { v -> ConditionSpec.DayOfWeek(Fields.days(v, "days", listOf(2, 3, 4, 5, 6))) },
        ),

        ConditionDef(
            title = "Screen is on or off",
            fields = listOf(FieldDef("on", "Require screen on", FieldType.BOOL)),
            create = { ConditionSpec.ScreenState() },
            read = { (it as ConditionSpec.ScreenState).let { c -> mapOf("on" to c.on.toString()) } },
            write = { v -> ConditionSpec.ScreenState(Fields.bool(v, "on", true)) },
        ),

        ConditionDef(
            title = "Charging state",
            fields = listOf(FieldDef("charging", "Require charging", FieldType.BOOL)),
            create = { ConditionSpec.Charging() },
            read = {
                (it as ConditionSpec.Charging).let { c -> mapOf("charging" to c.charging.toString()) }
            },
            write = { v -> ConditionSpec.Charging(Fields.bool(v, "charging", true)) },
        ),

        ConditionDef(
            title = "Battery level",
            fields = listOf(
                FieldDef("below", "Require below", FieldType.BOOL),
                FieldDef("percent", "Percent", FieldType.NUMBER),
            ),
            create = { ConditionSpec.BatteryLevel() },
            read = {
                (it as ConditionSpec.BatteryLevel).let { c ->
                    mapOf("below" to c.below.toString(), "percent" to c.percent.toString())
                }
            },
            write = { v ->
                ConditionSpec.BatteryLevel(
                    below = Fields.bool(v, "below", true),
                    percent = Fields.int(v, "percent", 20).coerceIn(1, 100),
                )
            },
        ),

        ConditionDef(
            title = "Wi-Fi state",
            fields = listOf(FieldDef("connected", "Require connected", FieldType.BOOL)),
            create = { ConditionSpec.WifiConnected() },
            read = {
                (it as ConditionSpec.WifiConnected).let { c ->
                    mapOf("connected" to c.connected.toString())
                }
            },
            write = { v -> ConditionSpec.WifiConnected(Fields.bool(v, "connected", true)) },
        ),

        ConditionDef(
            title = "An app is in the foreground",
            fields = listOf(FieldDef("packageName", "App", FieldType.APP)),
            create = { ConditionSpec.ForegroundApp() },
            read = {
                (it as ConditionSpec.ForegroundApp).let { c -> mapOf("packageName" to c.packageName) }
            },
            write = { v -> ConditionSpec.ForegroundApp(Fields.text(v, "packageName")) },
        ),

        ConditionDef(
            title = "A variable matches",
            help = "Tests something a previous step stored with Set a variable.",
            fields = listOf(
                FieldDef("name", "Variable", FieldType.TEXT),
                FieldDef("mode", "Test", FieldType.ENUM, options = MatchMode.entries.map { it.name }),
                FieldDef("value", "Value", FieldType.TEXT),
            ),
            create = { ConditionSpec.Variable() },
            read = {
                (it as ConditionSpec.Variable).let { c ->
                    mapOf(
                        "name" to c.name,
                        "mode" to c.match.mode.name,
                        "value" to c.match.value,
                    )
                }
            },
            write = { v ->
                ConditionSpec.Variable(
                    name = Fields.text(v, "name"),
                    match = MatchSpec(
                        mode = Fields.enum(v, "mode", MatchMode.CONTAINS),
                        value = Fields.text(v, "value"),
                    ),
                )
            },
        ),

        ConditionDef(
            title = "An app is installed",
            fields = listOf(
                FieldDef("packageName", "App", FieldType.APP),
                FieldDef("installed", "Require installed", FieldType.BOOL),
            ),
            create = { ConditionSpec.AppInstalled() },
            read = {
                (it as ConditionSpec.AppInstalled).let { c ->
                    mapOf(
                        "packageName" to c.packageName,
                        "installed" to c.installed.toString(),
                    )
                }
            },
            write = { v ->
                ConditionSpec.AppInstalled(
                    packageName = Fields.text(v, "packageName"),
                    installed = Fields.bool(v, "installed", true),
                )
            },
        ),
    )

    fun defFor(condition: ConditionSpec): ConditionDef =
        all.first { it.create()::class == condition::class }
}
