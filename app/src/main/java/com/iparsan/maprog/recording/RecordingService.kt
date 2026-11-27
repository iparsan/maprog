package com.iparsan.maprog.recording

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.iparsan.maprog.R
import com.iparsan.maprog.ui.MainActivity
import com.iparsan.maprog.util.Fmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano que recibe el GPS mientras grabas o sigues una ruta,
 * aunque la app esté en segundo plano o con la pantalla apagada.
 * Usa el LocationManager de Android (sin servicios de Google) para que sea 100 % libre.
 */
class RecordingService : Service(), LocationListener {

    private lateinit var lm: LocationManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ticker: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createChannel(this)
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopEverything()
            return START_NOT_STICKY
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), type)
        try {
            lm.removeUpdates(this)
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 2f, this, Looper.getMainLooper())
            }
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000L, 10f, this, Looper.getMainLooper())
            }
        } catch (_: SecurityException) { stopEverything() }

        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                delay(5000)
                val nm = getSystemService(NotificationManager::class.java)
                nm.notify(NOTIF_ID, buildNotification())
            }
        }
        return START_STICKY
    }

    override fun onLocationChanged(location: Location) {
        // Si llega una posición de red pero ya hay GPS reciente, se ignora (es menos precisa)
        if (location.provider == LocationManager.NETWORK_PROVIDER && location.accuracy > 30) {
            if (Recorder.state.value.lastLat != null) return
        }
        val ev = Recorder.onLocation(
            location.latitude, location.longitude,
            if (location.hasAltitude()) location.altitude else Recorder.state.value.lastEle,
            location.accuracy, if (location.hasSpeed()) location.speed else 0f,
            location.time,
        )
        when (ev) {
            FollowEvent.OFF_ROUTE -> vibrate(longArrayOf(0, 400, 200, 400, 200, 400))
            FollowEvent.BACK_ON_ROUTE -> vibrate(longArrayOf(0, 150))
            FollowEvent.NEAR_WAYPOINT -> vibrate(longArrayOf(0, 200, 150, 200))
            FollowEvent.FINISHED -> vibrate(longArrayOf(0, 600))
            null -> {}
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    private fun vibrate(pattern: LongArray) {
        val v: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        v.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    private fun buildNotification(): Notification {
        val s = Recorder.state.value
        val title = when {
            s.follow != null && s.isOffRoute -> "⚠️ Te has salido de la ruta"
            s.follow != null -> "Siguiendo: ${s.follow.name}"
            s.status == RecStatus.PAUSED -> "Grabación en pausa"
            else -> "Grabando ruta"
        }
        val text = "${Fmt.duration(s.elapsedMs() / 1000)} · ${Fmt.km(s.stats.distanceM)} · +${s.stats.elevGainM.toInt()} m"
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .build()
    }

    private fun stopEverything() {
        try { lm.removeUpdates(this) } catch (_: Exception) {}
        ticker?.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        try { lm.removeUpdates(this) } catch (_: Exception) {}
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "recording"
        private const val NOTIF_ID = 42
        const val ACTION_STOP = "stop"

        fun createChannel(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Grabación de rutas", NotificationManager.IMPORTANCE_LOW))
        }

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, RecordingService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, RecordingService::class.java).setAction(ACTION_STOP))
        }
    }
}
