package com.rikkahub.deepseeklocal.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.rikkahub.deepseeklocal.R
import com.rikkahub.deepseeklocal.presentation.about.AboutScreen
import com.rikkahub.deepseeklocal.presentation.chat.ChatScreen
import com.rikkahub.deepseeklocal.presentation.logs.LogsScreen
import com.rikkahub.deepseeklocal.presentation.main.MainScreen
import com.rikkahub.deepseeklocal.presentation.onboarding.OnboardingScreen
import com.rikkahub.deepseeklocal.presentation.settings.SettingsScreen

private data class Tab(val labelRes: Int, val icon: ImageVector)

private val TABS = listOf(
    Tab(R.string.tab_server, Icons.Filled.Storage),
    Tab(R.string.tab_chat, Icons.Filled.Chat),
    Tab(R.string.tab_logs, Icons.Filled.List),
    Tab(R.string.tab_settings, Icons.Filled.Settings),
    Tab(R.string.tab_about, Icons.Filled.Info),
)

/** Top-level navigation shell with a bottom navigation bar. */
@Composable
fun AppRoot() {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var onboarding by remember { mutableStateOf(false) }

    if (onboarding) {
        OnboardingScreen(onDone = { onboarding = false })
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                TABS.forEachIndexed { i, tab ->
                    NavigationBarItem(
                        selected = selected == i,
                        onClick = { selected = i },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AnimatedContent(
                targetState = selected,
                transitionSpec = {
                    (slideInHorizontally { it / 6 } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it / 6 } + fadeOut())
                },
                label = "tab",
            ) { idx ->
                when (idx) {
                    0 -> MainScreen(onOpenChat = { selected = 1 })
                    1 -> ChatScreen()
                    2 -> LogsScreen()
                    3 -> SettingsScreen(onOpenOnboarding = { onboarding = true })
                    else -> AboutScreen()
                }
            }
        }
    }
}
