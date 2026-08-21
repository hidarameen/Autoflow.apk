package com.autoflow.app.trigger

import android.content.ClipboardManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.autoflow.app.engine.RuleEngine
import com.autoflow.app.engine.TriggerEvent
import kotlin.math.sqrt

/**
 * Trigger sources that need a listener rather than a broadcast: Wi-Fi state, clipboard
 * changes and shake detection. Started and stopped together with the foreground service.
 */
class DeviceWatchers(private val context: Context) {

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var clipboardListener: ClipboardManager.OnPrimaryClipChangedListener? = null
    private var shakeListener: ShakeListener? = null

    fun start() {
        startWifiWatch()
        startClipboardWatch()
        startShakeWatch()
    }

    fun stop() {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        networkCallback?.let { runCatching { connectivity?.unregisterNetworkCallback(it) } }
        networkCallback = null

        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboardListener?.let { clipboard?.removePrimaryClipChangedListener(it) }
        clipboardListener = null

        val sensors = context.getSystemService(SensorManager::class.java)
        shakeListener?.let { sensors?.unregisterListener(it) }
        shakeListener = null
    }

    private fun startWifiWatch() {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) =
                RuleEngine.submit(TriggerEvent.of(TriggerEvent.Source.WIFI_CONNECTED))

            override fun onLost(network: Network) =
                RuleEngine.submit(TriggerEvent.of(TriggerEvent.Source.WIFI_DISCONNECTED))
        }
        runCatching { connectivity.registerNetworkCallback(request, callback) }
            .onSuccess { networkCallback = callback }
    }

    private fun startClipboardWatch() {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        val listener = ClipboardManager.OnPrimaryClipChangedListener {
            // Android 10+ only lets the foreground app or an IME read the clip, so the text
            // may come back empty. The trigger still fires; rules should not rely on {{text}}.
            val text = runCatching {
                clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
            }.getOrNull().orEmpty()

            RuleEngine.submit(
                TriggerEvent(
                    source = TriggerEvent.Source.CLIPBOARD,
                    packageName = "android.clipboard",
                    appLabel = "Clipboard",
                    text = text,
                )
            )
        }
        clipboard.addPrimaryClipChangedListener(listener)
        clipboardListener = listener
    }

    private fun startShakeWatch() {
        val sensors = context.getSystemService(SensorManager::class.java) ?: return
        val accelerometer = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        val listener = ShakeListener()
        sensors.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        shakeListener = listener
    }
}

/**
 * Fires when total acceleration passes a threshold well above gravity, with a cooldown so
 * one physical shake produces one event instead of a burst.
 */
private class ShakeListener : SensorEventListener {

    private var lastShakeAt = 0L

    override fun onSensorChanged(event: SensorEvent?) {
        val values = event?.values ?: return
        if (values.size < 3) return

        val gForce = sqrt(
            (values[0] * values[0] + values[1] * values[1] + values[2] * values[2]).toDouble()
        ) / SensorManager.GRAVITY_EARTH

        if (gForce < SHAKE_THRESHOLD) return

        val now = System.currentTimeMillis()
        if (now - lastShakeAt < SHAKE_COOLDOWN_MS) return
        lastShakeAt = now

        RuleEngine.submit(TriggerEvent.of(TriggerEvent.Source.SHAKE))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val SHAKE_THRESHOLD = 2.7
        const val SHAKE_COOLDOWN_MS = 1_200L
    }
}
