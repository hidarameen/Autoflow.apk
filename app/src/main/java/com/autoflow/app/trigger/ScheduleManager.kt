package com.autoflow.app.trigger

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.autoflow.app.engine.RuleEngine
import com.autoflow.app.engine.TriggerEvent

/**
 * Drives the time-based triggers. One alarm per rule, keyed by rule id, so editing a rule
 * simply overwrites its alarm and deleting one cancels it.
 */
object ScheduleManager {

    private const val ACTION_FIRE = "com.autoflow.app.ALARM_FIRE"
    private const val EXTRA_RULE_ID = "rule_id"
    private const val EXTRA_KIND = "kind"

    fun scheduleDaily(context: Context, ruleId: Long, hour: Int, minute: Int) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = nextOccurrence(hour, minute)

        // Inexact repeating is deliberate: it survives Doze far better than an exact alarm
        // and automation rules do not need second-level accuracy.
        alarms.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            AlarmManager.INTERVAL_DAY,
            pendingIntent(context, ruleId, "schedule"),
        )
    }

    fun scheduleInterval(context: Context, ruleId: Long, minutes: Int) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val period = minutes.coerceAtLeast(1) * 60_000L
        alarms.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + period,
            period,
            pendingIntent(context, ruleId, "interval"),
        )
    }

    fun cancel(context: Context, ruleId: Long) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        alarms.cancel(pendingIntent(context, ruleId, "schedule"))
        alarms.cancel(pendingIntent(context, ruleId, "interval"))
    }

    private fun nextOccurrence(hour: Int, minute: Int): Long {
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        return calendar.timeInMillis
    }

    private fun pendingIntent(context: Context, ruleId: Long, kind: String): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_RULE_ID, ruleId)
            putExtra(EXTRA_KIND, kind)
            // The extras are not part of intent identity, so vary the data URI instead or
            // every rule would share one alarm slot.
            data = android.net.Uri.parse("autoflow://rule/$ruleId/$kind")
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, ruleId.toInt(), intent, flags)
    }

    /** Receives the alarm and hands the rule id to the engine. */
    class AlarmReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_FIRE) return
            val ruleId = intent.getLongExtra(EXTRA_RULE_ID, -1L)
            if (ruleId < 0) return

            RuleEngine.init(context)
            val source = if (intent.getStringExtra(EXTRA_KIND) == "interval") {
                TriggerEvent.Source.INTERVAL
            } else {
                TriggerEvent.Source.SCHEDULE
            }
            RuleEngine.runScheduled(ruleId, TriggerEvent.of(source))
        }
    }
}
