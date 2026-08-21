package com.autoflow.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rules")
data class Rule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val enabled: Boolean = true,
    val trigger: TriggerSpec,
    /** Extra guards evaluated after the trigger fires and before any action runs. */
    val conditions: List<ConditionSpec> = emptyList(),
    val conditionLogic: ConditionLogic = ConditionLogic.ALL,
    val actions: List<ActionSpec>,
    /** Ignores repeat triggers inside this window. Stops notification storms. */
    val cooldownMs: Long = 5_000,
    /** Optional cap on how many times this rule may run per day. 0 means unlimited. */
    val dailyLimit: Int = 0,
    val notes: String = "",
    val createdAt: Long = 0,
    val lastFiredAt: Long = 0,
    val runCount: Int = 0,
)

@Entity(tableName = "logs")
data class RuleLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleId: Long,
    val ruleName: String,
    val timestamp: Long,
    val success: Boolean,
    val message: String,
    /** How long the whole action list took, for spotting rules that hang on a timeout. */
    val durationMs: Long = 0,
)
