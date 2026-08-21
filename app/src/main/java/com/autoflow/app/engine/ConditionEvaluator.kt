package com.autoflow.app.engine

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.PowerManager
import com.autoflow.app.data.ConditionLogic
import com.autoflow.app.data.ConditionSpec
import com.autoflow.app.data.EventField
import com.autoflow.app.trigger.AutoFlowAccessibilityService
import java.util.Calendar

/**
 * Evaluates rule guards against live device state. Everything is read at evaluation time
 * rather than cached, because a rule may sit idle for hours between triggers.
 */
class ConditionEvaluator(private val context: Context) {

    fun evaluateAll(
        conditions: List<ConditionSpec>,
        logic: ConditionLogic,
        run: RunContext,
    ): Boolean {
        if (conditions.isEmpty()) return true
        return when (logic) {
            ConditionLogic.ALL -> conditions.all { evaluate(it, run) }
            ConditionLogic.ANY -> conditions.any { evaluate(it, run) }
        }
    }

    fun evaluate(condition: ConditionSpec, run: RunContext): Boolean = when (condition) {
        is ConditionSpec.Text -> {
            val source = when (condition.field) {
                EventField.TEXT -> run.event.text
                EventField.TITLE -> run.event.title
                EventField.PACKAGE -> run.event.packageName
                EventField.APP -> run.event.appLabel
            }
            condition.match.matches(Variables.resolve(source, run))
        }

        is ConditionSpec.TimeWindow -> {
            val now = Calendar.getInstance()
            val minutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            if (condition.startMinutes <= condition.endMinutes) {
                minutes in condition.startMinutes..condition.endMinutes
            } else {
                // Window wraps past midnight, e.g. 22:00 to 06:00.
                minutes >= condition.startMinutes || minutes <= condition.endMinutes
            }
        }

        is ConditionSpec.DayOfWeek ->
            Calendar.getInstance().get(Calendar.DAY_OF_WEEK) in condition.days

        is ConditionSpec.ScreenState -> isScreenOn() == condition.on

        is ConditionSpec.Charging -> isCharging() == condition.charging

        is ConditionSpec.BatteryLevel -> {
            val level = batteryPercent()
            if (condition.below) level < condition.percent else level > condition.percent
        }

        is ConditionSpec.WifiConnected -> isWifiConnected() == condition.connected

        is ConditionSpec.ForegroundApp -> {
            val current = AutoFlowAccessibilityService.foregroundPackage
            condition.packageName.isBlank() || current == condition.packageName
        }

        is ConditionSpec.Variable ->
            condition.match.matches(run.get(condition.name).orEmpty())

        is ConditionSpec.AppInstalled -> isInstalled(condition.packageName) == condition.installed
    }

    private fun isScreenOn(): Boolean =
        context.getSystemService(PowerManager::class.java)?.isInteractive ?: true

    private fun isCharging(): Boolean {
        val status = batteryIntent()?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: return false
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun batteryPercent(): Int {
        val intent = batteryIntent() ?: return 100
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return if (level < 0 || scale <= 0) 100 else level * 100 / scale
    }

    private fun batteryIntent(): Intent? =
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    private fun isWifiConnected(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun isInstalled(packageName: String): Boolean =
        packageName.isNotBlank() &&
            context.packageManager.getLaunchIntentForPackage(packageName) != null
}
