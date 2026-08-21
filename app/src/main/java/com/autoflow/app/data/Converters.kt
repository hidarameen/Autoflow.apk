package com.autoflow.app.data

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString

class AutoFlowConverters {

    @TypeConverter
    fun triggerToJson(value: TriggerSpec): String = AppJson.encodeToString(value)

    @TypeConverter
    fun jsonToTrigger(value: String): TriggerSpec = AppJson.decodeFromString(value)

    @TypeConverter
    fun actionsToJson(value: List<ActionSpec>): String = AppJson.encodeToString(value)

    @TypeConverter
    fun jsonToActions(value: String): List<ActionSpec> = AppJson.decodeFromString(value)

    @TypeConverter
    fun conditionsToJson(value: List<ConditionSpec>): String = AppJson.encodeToString(value)

    @TypeConverter
    fun jsonToConditions(value: String): List<ConditionSpec> = AppJson.decodeFromString(value)

    @TypeConverter
    fun logicToString(value: ConditionLogic): String = value.name

    @TypeConverter
    fun stringToLogic(value: String): ConditionLogic =
        runCatching { ConditionLogic.valueOf(value) }.getOrDefault(ConditionLogic.ALL)
}
