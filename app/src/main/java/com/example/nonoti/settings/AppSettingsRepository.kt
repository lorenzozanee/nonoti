package com.example.nonoti.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import com.example.nonoti.data.BoxRepository
import com.example.nonoti.notifications.FocusRuntime
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

private val Context.nonotiSettings by preferencesDataStore("settings")

data class AppSettings(
    val allowCalls: Boolean = true,
    val allowAlarms: Boolean = true,
    val retentionHours: Int = 24,
    val alwaysAllowedPackages: Set<String> = emptySet(),
)

class AppSettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val boxRepository: BoxRepository,
) {
    val settings: Flow<AppSettings> = context.nonotiSettings.data.map { values ->
        AppSettings(
            allowCalls = values[AllowCalls] ?: true,
            allowAlarms = values[AllowAlarms] ?: true,
            retentionHours = values[RetentionHours] ?: 24,
            alwaysAllowedPackages = values[AlwaysAllowedPackages] ?: emptySet(),
        )
    }

    suspend fun setAllowCalls(value: Boolean) {
        context.nonotiSettings.edit { it[AllowCalls] = value }
    }

    suspend fun setAllowAlarms(value: Boolean) {
        context.nonotiSettings.edit { it[AllowAlarms] = value }
    }

    suspend fun setRetentionHours(value: Int) {
        require(value in setOf(24, 168, 720))
        context.nonotiSettings.edit { it[RetentionHours] = value }
        boxRepository.deleteOlderThan(
            System.currentTimeMillis() - value * 60L * 60L * 1000L,
            activeSessionId = FocusRuntime.current()?.id,
        )
    }

    suspend fun setAlwaysAllowed(packageName: String, allowed: Boolean) {
        context.nonotiSettings.edit { values ->
            val current = values[AlwaysAllowedPackages].orEmpty()
            values[AlwaysAllowedPackages] = if (allowed) current + packageName else current - packageName
        }
    }

    suspend fun applyRetention() {
        val hours = settings.first().retentionHours
        boxRepository.deleteOlderThan(
            System.currentTimeMillis() - hours * 60L * 60L * 1000L,
            activeSessionId = FocusRuntime.current()?.id,
        )
    }

    private companion object {
        val AllowCalls = booleanPreferencesKey("allow_calls")
        val AllowAlarms = booleanPreferencesKey("allow_alarms")
        val RetentionHours = intPreferencesKey("retention_hours")
        val AlwaysAllowedPackages = stringSetPreferencesKey("always_allowed_packages")
    }
}
