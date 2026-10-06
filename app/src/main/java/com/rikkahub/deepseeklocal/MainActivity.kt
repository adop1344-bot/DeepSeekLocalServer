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
import androidx.hilt.navigation.compose.hiltViewModel
import com.rikkahub.deepseeklocal.presentation.AppRoot
import com.rikkahub.deepseeklocal.presentation.MainActivityViewModel
import com.rikkahub.deepseeklocal.presentation.theme.DeepSeekTheme
import dagger.hilt.android.AndroidEntryPoint

/** Single-activity host for the whole Compose UI. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: MainActivityViewModel = hiltViewModel()
            val settings by vm.settings.collectAsState()
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
