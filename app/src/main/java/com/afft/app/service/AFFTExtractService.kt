package com.afft.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager

class AFFTExtractService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AFFT Extraction",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Menjaga proses ekstraksi tetap berjalan"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this).setPriority(Notification.PRIORITY_LOW)
        }

        return builder
            .setContentTitle("AFFT")
            .setContentText("Sedang mengekstrak...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()
    }
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "AFFT:ExtractWakeLock"
            )
            wakeLock?.acquire(10 * 60 * 1000L) // 10 menit timeout safety
            android.util.Log.d("AFFTExtractService", "WakeLock acquired")
        } catch (e: Exception) {
            android.util.Log.e("AFFTExtractService", "Failed to acquire WakeLock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.release()
            wakeLock = null
            android.util.Log.d("AFFTExtractService", "WakeLock released")
        } catch (e: Exception) {
            android.util.Log.e("AFFTExtractService", "Failed to release WakeLock: ${e.message}")
        }
    }

    companion object {
        const val CHANNEL_ID = "afft_extract"
        const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, AFFTExtractService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                @Suppress("DEPRECATION")
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AFFTExtractService::class.java))
        }
    }
}
