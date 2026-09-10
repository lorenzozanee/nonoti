package com.example.nonoti.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.nonoti.MainActivity
import com.example.nonoti.R

class FocusForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel(
                ChannelId,
                getString(R.string.focus_service_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        startForeground(NotificationId, notification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Thread { NonotiPlatform.restore(applicationContext) }.start()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification() = NotificationCompat.Builder(this, ChannelId)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(getString(R.string.focus_service_title))
        .setContentText(getString(R.string.focus_service_detail))
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                NotificationId,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .build()

    companion object {
        private const val ChannelId = "active-focus"
        private const val NotificationId = 41

        fun start(context: Context): Boolean {
            return runCatching {
                ContextCompat.startForegroundService(
                    context.applicationContext,
                    Intent(context.applicationContext, FocusForegroundService::class.java),
                )
            }.isSuccess
        }

        fun stop(context: Context) {
            context.applicationContext.stopService(
                Intent(context.applicationContext, FocusForegroundService::class.java),
            )
        }

    }
}
