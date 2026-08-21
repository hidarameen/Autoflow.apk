package com.autoflow.app.trigger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.autoflow.app.engine.RuleEngine
import com.autoflow.app.engine.TriggerEvent
import com.autoflow.app.util.AppInfo

/**
 * One receiver for every broadcast-shaped trigger. Registered at runtime by the foreground
 * service rather than in the manifest, because from Android 8 most of these (screen on/off,
 * connectivity, battery) are no longer delivered to manifest-declared receivers.
 */
class SystemEventReceiver : BroadcastReceiver() {

    /** Battery percent changes constantly; only report when the whole-number level moves. */
    private var lastBatteryPercent = -1

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_ON -> submit(TriggerEvent.Source.SCREEN_ON)
            Intent.ACTION_SCREEN_OFF -> submit(TriggerEvent.Source.SCREEN_OFF)
            Intent.ACTION_USER_PRESENT -> submit(TriggerEvent.Source.DEVICE_UNLOCKED)
            Intent.ACTION_POWER_CONNECTED -> submit(TriggerEvent.Source.POWER_CONNECTED)
            Intent.ACTION_POWER_DISCONNECTED -> submit(TriggerEvent.Source.POWER_DISCONNECTED)

            Intent.ACTION_BATTERY_CHANGED -> {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level < 0 || scale <= 0) return
                val percent = level * 100 / scale
                if (percent == lastBatteryPercent) return
                lastBatteryPercent = percent
                RuleEngine.submit(TriggerEvent(TriggerEvent.Source.BATTERY, value = percent))
            }

            Intent.ACTION_HEADSET_PLUG -> {
                val plugged = intent.getIntExtra("state", 0) == 1
                RuleEngine.submit(
                    TriggerEvent(TriggerEvent.Source.HEADSET, value = if (plugged) 1 else 0)
                )
            }

            Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_REMOVED -> {
                val target = intent.data?.schemeSpecificPart.orEmpty()
                // A replace is an update, not an install or an uninstall.
                if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
                val source = if (intent.action == Intent.ACTION_PACKAGE_ADDED) {
                    TriggerEvent.Source.APP_INSTALLED
                } else {
                    TriggerEvent.Source.APP_UNINSTALLED
                }
                RuleEngine.submit(
                    TriggerEvent(
                        source = source,
                        packageName = target,
                        appLabel = AppInfo.label(context, target),
                        text = target,
                    )
                )
            }

            SMS_RECEIVED -> handleSms(intent)
        }
    }

    private fun handleSms(intent: Intent) {
        val messages = runCatching {
            android.provider.Telephony.Sms.Intents.getMessagesFromIntent(intent)
        }.getOrNull() ?: return

        // A long SMS arrives split; the parts share a sender and must be rejoined.
        val sender = messages.firstOrNull()?.originatingAddress.orEmpty()
        val body = messages.joinToString("") { it.messageBody.orEmpty() }
        if (body.isBlank()) return

        RuleEngine.submit(
            TriggerEvent(
                source = TriggerEvent.Source.SMS,
                packageName = "android.sms",
                appLabel = "SMS",
                title = sender,
                text = body,
            )
        )
    }

    private fun submit(source: TriggerEvent.Source) = RuleEngine.submit(TriggerEvent.of(source))

    companion object {
        private const val SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED"

        fun filter(): IntentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(SMS_RECEIVED)
        }

        /** Package events need a data scheme, so they cannot share the filter above. */
        fun packageFilter(): IntentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
    }
}
