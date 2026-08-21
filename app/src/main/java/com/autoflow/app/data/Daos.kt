package com.autoflow.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {

    @Query("SELECT * FROM rules ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Rule>>

    @Query("SELECT * FROM rules WHERE enabled = 1")
    suspend fun enabledRules(): List<Rule>

    @Query("SELECT * FROM rules WHERE id = :id")
    suspend fun byId(id: Long): Rule?

    @Upsert
    suspend fun upsert(rule: Rule): Long

    @Update
    suspend fun update(rule: Rule)

    @Delete
    suspend fun delete(rule: Rule)

    @Query("UPDATE rules SET lastFiredAt = :at, runCount = runCount + 1 WHERE id = :id")
    suspend fun markFired(id: Long, at: Long)

    @Query("SELECT * FROM rules WHERE name = :name LIMIT 1")
    suspend fun byName(name: String): Rule?

    @Query("SELECT COUNT(*) FROM logs WHERE ruleId = :id AND success = 1 AND timestamp >= :since")
    suspend fun successesSince(id: Long, since: Long): Int

    @Query("UPDATE rules SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)
}

@Dao
interface LogDao {

    @Query("SELECT * FROM logs ORDER BY timestamp DESC LIMIT 300")
    fun observeRecent(): Flow<List<RuleLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: RuleLog)

    @Query("DELETE FROM logs")
    suspend fun clear()

    @Query("DELETE FROM logs WHERE timestamp < :before")
    suspend fun trim(before: Long)
}
