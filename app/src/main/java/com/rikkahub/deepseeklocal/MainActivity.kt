package com.rikkahub.deepseeklocal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.rikkahub.deepseeklocal.data.local.prefs.AppSettings
import com.rikkahub.deepseeklocal.data.repository.SettingsRepository
import com.rikkahub.deepseeklocal.presentation.AppRoot
import com.rikkahub.deepseeklocal.presentation.theme.DeepSeekTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Single-activity host for the whole Compose UI. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by settingsRepository.settings.collectAsState(initial = AppSettings())
            DeepSeekTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor,
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot()
                }
            }
        }
    }
}
