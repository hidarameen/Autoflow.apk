package com.autoflow.app.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class RuleRepository(context: Context) {

    private val db = AutoFlowDatabase.get(context)
    private val rules = db.ruleDao()
    private val logs = db.logDao()

    fun observeRules(): Flow<List<Rule>> = rules.observeAll()

    fun observeLogs(): Flow<List<RuleLog>> = logs.observeRecent()

    suspend fun enabledRules(): List<Rule> = rules.enabledRules()

    suspend fun anyRules(): Boolean = rules.count() > 0

    suspend fun rule(id: Long): Rule? = rules.byId(id)

    suspend fun ruleByName(name: String): Rule? = rules.byName(name)

    suspend fun save(rule: Rule): Long =
        rules.upsert(if (rule.createdAt == 0L) rule.copy(createdAt = System.currentTimeMillis()) else rule)

    suspend fun delete(rule: Rule) = rules.delete(rule)

    suspend fun setEnabled(id: Long, enabled: Boolean) = rules.setEnabled(id, enabled)

    suspend fun markFired(id: Long, at: Long) = rules.markFired(id, at)

    /** Used to enforce a rule's daily run limit. */
    suspend fun successesSince(id: Long, since: Long): Int = rules.successesSince(id, since)

    suspend fun log(
        ruleId: Long,
        ruleName: String,
        success: Boolean,
        message: String,
        durationMs: Long = 0,
    ) {
        logs.insert(
            RuleLog(
                ruleId = ruleId,
                ruleName = ruleName,
                timestamp = System.currentTimeMillis(),
                success = success,
                message = message,
                durationMs = durationMs,
            )
        )
    }

    suspend fun clearLogs() = logs.clear()

    companion object {
        @Volatile
        private var instance: RuleRepository? = null

        fun get(context: Context): RuleRepository =
            instance ?: synchronized(this) {
                instance ?: RuleRepository(context.applicationContext).also { instance = it }
            }
    }
}
