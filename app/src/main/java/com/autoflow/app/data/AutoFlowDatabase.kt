package com.autoflow.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [Rule::class, RuleLog::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(AutoFlowConverters::class)
abstract class AutoFlowDatabase : RoomDatabase() {

    abstract fun ruleDao(): RuleDao
    abstract fun logDao(): LogDao

    companion object {
        @Volatile
        private var instance: AutoFlowDatabase? = null

        fun get(context: Context): AutoFlowDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AutoFlowDatabase::class.java,
                    "autoflow.db",
                ).fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}
