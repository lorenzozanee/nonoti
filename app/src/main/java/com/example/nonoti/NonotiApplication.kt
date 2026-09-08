package com.example.nonoti

import android.app.Application
import com.example.nonoti.data.FocusBlockRepository
import com.example.nonoti.data.NonotiDatabase
import com.example.nonoti.data.BoxRepository
import com.example.nonoti.notifications.AndroidFocusSchedule
import com.example.nonoti.notifications.NonotiPlatform
import com.example.nonoti.notifications.NotificationPermissionMonitor
import com.example.nonoti.settings.AppSettingsRepository
import kotlinx.coroutines.runBlocking
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NonotiApplication : Application() {
    private lateinit var notificationPermissionMonitor: NotificationPermissionMonitor

    override fun onCreate() {
        super.onCreate()
        notificationPermissionMonitor = NotificationPermissionMonitor(this).also { it.start() }
        Thread {
            NonotiPlatform.restore(this)
            runBlocking { settingsRepository.applyRetention() }
            AndroidFocusSchedule(this).reconcile()
        }.start()
    }

    @Inject lateinit var database: NonotiDatabase
    @Inject lateinit var focusBlockRepository: FocusBlockRepository
    @Inject lateinit var boxRepository: BoxRepository
    @Inject lateinit var settingsRepository: AppSettingsRepository
}
