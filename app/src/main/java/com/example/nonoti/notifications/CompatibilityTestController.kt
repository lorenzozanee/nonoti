package com.example.nonoti.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.example.nonoti.R
import com.example.nonoti.focus.FocusSession
import java.time.Instant

object CompatibilityTestController {
    const val ChannelId = "compatibility-test"
    const val NotificationId = 43001
    private const val DurationSeconds = 30L

    fun start(context: Context): Boolean {
        val platform = AndroidFocusPlatform(context.applicationContext)
        val readiness = platform.readiness().copy(compatibility = true)
        if (!readiness.isReady) return false
        val now = Instant.now()
        val session = FocusSession("compatibility-${now.toEpochMilli()}", now, now.plusSeconds(DurationSeconds))
        CompatibilityStore(context).beginTest()
        if (NonotiPlatform.start(context, session, readiness) != FocusStartResult.Started) {
            CompatibilityStore(context).cancelTest()
            return false
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(ChannelId, context.getString(R.string.compatibility_channel), NotificationManager.IMPORTANCE_HIGH))
        manager.notify(
            NotificationId,
            NotificationCompat.Builder(context, ChannelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(context.getString(R.string.compatibility_notification_title))
                .setContentText(context.getString(R.string.compatibility_notification_text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build(),
        )
        return true
    }
}
