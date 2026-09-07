package com.autoflow.app.engine

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.autoflow.app.data.RuleRepository
import com.autoflow.app.trigger.DeviceWatchers
import com.autoflow.app.trigger.SystemEventReceiver
import com.autoflow.app.trigger.TelegramBotPoller
import com.autoflow.app.util.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Holds the process open so triggers keep arriving, and owns the runtime-registered
 * listeners. Without a foreground service, OEM battery managers kill the process within
 * minutes of the screen going off and every rule silently stops.
 */
class AutoFlowForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val systemEvents = SystemEventReceiver()
    private var watchers: DeviceWatchers? = null
    private var telegram: TelegramBotPoller? = null

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        startForegroundWith(activeRules = 0)

        RuleEngine.init(applicationContext)
        registerSystemEvents()

        watchers = DeviceWatchers(applicationContext).also { it.start() }
        telegram = TelegramBotPoller(applicationContext, scope)

        // Keep the badge honest and re-arm alarms whenever the rule set changes.
        scope.launch {
            RuleRepository.get(applicationContext).observeRules().collectLatest { rules ->
                startForegroundWith(rules.count { it.enabled })
                RuleEngine.syncSchedules(rules)
                // Starts a long-poll loop per bot token, and stops loops no rule needs.
                telegram?.sync(rules)
            }
        }
    }

    private fun registerSystemEvents() {
        // Screen, power and connectivity broadcasts are no longer delivered to
        // manifest-declared receivers, so they must be registered while running.
        ContextCompat.registerReceiver(
            this,
            systemEvents,
            SystemEventReceiver.filter(),
            ContextCompat.RECEIVER_EXPORTED,
        )
        ContextCompat.registerReceiver(
            this,
            systemEvents,
            SystemEventReceiver.packageFilter(),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runCatching { unregisterReceiver(systemEvents) }
        watchers?.stop()
        watchers = null
        telegram?.stop()
        telegram = null
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundWith(activeRules: Int) {
        val notification = Notifier.serviceNotification(this, activeRules)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, Notifier.SERVICE_NOTIFICATION_ID, notification, type)
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, AutoFlowForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AutoFlowForegroundService::class.java))
        }
    }
}
