package com.example.nonoti.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

fun interface ZenPolicyPort {
    fun update(allowCalls: Boolean, allowAlarms: Boolean)
}

data class InstalledApp(val packageName: String, val label: String)

fun interface InstalledAppsPort {
    fun apps(): List<InstalledApp>
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: AppSettingsRepository,
    private val zenPolicy: ZenPolicyPort,
    installedApps: InstalledAppsPort,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    val installedApps: List<InstalledApp> = installedApps.apps()

    fun setAllowCalls(value: Boolean) {
        viewModelScope.launch {
            repository.setAllowCalls(value)
            zenPolicy.update(value, settings.value.allowAlarms)
        }
    }

    fun setAllowAlarms(value: Boolean) {
        viewModelScope.launch {
            repository.setAllowAlarms(value)
            zenPolicy.update(settings.value.allowCalls, value)
        }
    }

    fun setRetentionHours(value: Int) {
        viewModelScope.launch { repository.setRetentionHours(value) }
    }

    fun setAlwaysAllowed(packageName: String, allowed: Boolean) {
        viewModelScope.launch { repository.setAlwaysAllowed(packageName, allowed) }
    }
}
