package com.autoflow.app.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-arms the engine after a reboot so rules survive a restart. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        RuleEngine.init(context)
        AutoFlowForegroundService.start(context)
        RuleEngine.submit(TriggerEvent.of(TriggerEvent.Source.BOOT))
    }
}
