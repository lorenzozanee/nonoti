package com.example.nonoti

import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.nonoti.theme.NonotiTheme
import com.example.nonoti.ui.NonotiApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val launchAction = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchAction.value = intent?.action
        setRecentsScreenshotEnabled(false)
        enableEdgeToEdge()
        setContent {
            val settingsViewModel: com.example.nonoti.settings.SettingsViewModel = hiltViewModel()
            val settings by settingsViewModel.settings.collectAsState()
            NonotiTheme(darkTheme = settings.darkTheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    NonotiApp(
                        focusViewModel = hiltViewModel(),
                        boxViewModel = hiltViewModel(),
                        settingsViewModel = settingsViewModel,
                        launchAction = launchAction.value,
                        onLaunchActionConsumed = { launchAction.value = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchAction.value = intent.action
    }

    companion object {
        const val ACTION_OPEN_BOX = "com.example.nonoti.OPEN_BOX"
        const val ACTION_EMERGENCY_ACCESS = "com.example.nonoti.EMERGENCY_ACCESS"
    }
}
