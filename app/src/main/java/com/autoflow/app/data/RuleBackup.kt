package com.autoflow.app.data

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File

/**
 * A plain-text snapshot of every rule, rewritten whenever the rule set changes.
 *
 * Room is configured to fall back to a destructive migration, which drops all tables when
 * the schema no longer matches — that silently destroyed a user's entire rule set once.
 * Rules represent real work, so they are also kept outside the database and restored
 * automatically if the table ever comes back empty.
 */
object RuleBackup {

    private const val TAG = "AutoFlowBackup"
    private const val FILE_NAME = "rules-backup.json"

    @Serializable
    private data class Snapshot(
        val savedAt: Long,
        val rules: List<Rule>,
    )

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    /** Overwrites the snapshot. Never throws: a failed backup must not break saving a rule. */
    fun write(context: Context, rules: List<Rule>) {
        if (rules.isEmpty()) return  // never let an empty table erase a good backup
        runCatching {
            val json = AppJson.encodeToString(Snapshot(System.currentTimeMillis(), rules))
            file(context).writeText(json)
        }.onFailure { Log.w(TAG, "could not write backup: ${it.message}") }
    }

    /** Rules from the last snapshot, or empty when there is none. */
    fun read(context: Context): List<Rule> {
        val source = file(context)
        if (!source.exists()) return emptyList()
        return runCatching {
            AppJson.decodeFromString<Snapshot>(source.readText()).rules
        }.onFailure { Log.w(TAG, "could not read backup: ${it.message}") }
            .getOrDefault(emptyList())
    }

    /**
     * Puts the snapshot back when the database has lost everything — which is what a
     * destructive migration looks like from the outside.
     */
    suspend fun restoreIfEmpty(context: Context, repository: RuleRepository) {
        if (repository.enabledRules().isNotEmpty()) return
        if (repository.anyRules()) return

        val saved = read(context)
        if (saved.isEmpty()) return

        Log.w(TAG, "rules table was empty; restoring ${saved.size} from backup")
        // Ids are reassigned so a restore cannot collide with anything already present.
        saved.forEach { repository.save(it.copy(id = 0)) }
    }
}
