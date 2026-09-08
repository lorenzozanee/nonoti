package com.example.nonoti.di

import android.content.Context
import androidx.room.Room
import com.example.nonoti.data.NonotiDao
import com.example.nonoti.data.NonotiDatabase
import com.example.nonoti.focus.FocusSchedulePort
import com.example.nonoti.notifications.AndroidFocusSchedule
import com.example.nonoti.notifications.ZenRuleController
import com.example.nonoti.settings.AndroidInstalledApps
import com.example.nonoti.settings.InstalledAppsPort
import com.example.nonoti.settings.ZenPolicyPort
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): NonotiDatabase =
        Room.databaseBuilder(context, NonotiDatabase::class.java, "nonoti.db").build()

    @Provides
    fun dao(database: NonotiDatabase): NonotiDao = database.dao()

    @Provides
    fun focusSchedule(@ApplicationContext context: Context): FocusSchedulePort = AndroidFocusSchedule(context)

    @Provides
    fun zenPolicy(@ApplicationContext context: Context): ZenPolicyPort =
        ZenPolicyPort { calls, alarms -> ZenRuleController(context).updatePolicy(calls, alarms) }

    @Provides
    fun installedApps(@ApplicationContext context: Context): InstalledAppsPort = AndroidInstalledApps(context)
}
