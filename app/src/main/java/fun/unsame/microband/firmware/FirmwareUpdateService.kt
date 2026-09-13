package com.unsame.microband.firmware

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.unsame.microband.MainActivity
import com.unsame.microband.MicrobandApplication
import com.unsame.microband.R
import com.unsame.microband.band.model.FirmwareUpdateStage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FirmwareUpdateService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var operation: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Band firmware updates", NotificationManager.IMPORTANCE_LOW),
        )
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Microband:FirmwareUpdate")
            .apply { acquire(15 * 60_000L) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (operation?.isActive == true) return START_NOT_STICKY
        val connection = (application as MicrobandApplication).connectionManager
        startForeground(NOTIFICATION_ID, notification("Preparing firmware update", 0, true))
        scope.launch {
            connection.firmwareUpdate.collectLatest { status ->
                getSystemService(NotificationManager::class.java).notify(
                    NOTIFICATION_ID,
                    notification(status.message.ifBlank { "Preparing firmware update" }, status.percent, status.isRunning),
                )
                if (status.stage == FirmwareUpdateStage.Complete || status.stage == FirmwareUpdateStage.Failed) {
                    stopForeground(STOP_FOREGROUND_DETACH)
                    stopSelf()
                }
            }
        }
        operation = scope.launch { connection.runLatestFirmwareUpdate() }
        return START_NOT_STICKY
    }

    private fun notification(message: String, progress: Int, ongoing: Boolean): Notification {
        val launch = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_microband_mark)
            .setContentTitle("Microband firmware update")
            .setContentText(message)
            .setContentIntent(launch)
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
            .setProgress(100, progress, progress == 0)
            .build()
    }

    override fun onDestroy() {
        wakeLock?.takeIf { it.isHeld }?.release()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "firmware_updates"
        private const val NOTIFICATION_ID = 5202

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, FirmwareUpdateService::class.java))
        }
    }
}
