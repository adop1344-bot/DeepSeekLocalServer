package com.rikkahub.deepseeklocal.presentation.main

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Power
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rikkahub.deepseeklocal.R
import com.rikkahub.deepseeklocal.domain.model.ServerState
import com.rikkahub.deepseeklocal.presentation.server.ServerController
import kotlinx.coroutines.launch

/** Status card + power button. */
@Composable
fun MainScreen(vm: MainViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current

    val running = state is ServerState.Running
    val url = (state as? ServerState.Running)?.baseUrl ?: "—"
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f, targetValue = if (running) 1.12f else 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "scale",
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (running) ServerController.stop() else ServerController.start()
                },
                icon = { Icon(Icons.Filled.Power, contentDescription = null) },
                text = { Text(stringResource(if (running) R.string.server_stop else R.string.server_start)) },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(160.dp).scale(scale).background(statusColor(state), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(statusLabel(state), color = Color.White, style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(24.dp))
            Card(Modifier.fillMaxWidth().animateContentSize()) {
                Column(Modifier.padding(20.dp)) {
                    Text(stringResource(R.string.tab_server), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(url, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = {
                            clipboard.setText(AnnotatedString(url))
                            scope.launch { snackbar.showSnackbar("Copied") }
                        }) { Text(stringResource(R.string.server_copy)) }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.common_http_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun statusColor(s: ServerState): Color = when (s) {
    is ServerState.Stopped -> Color(0xFF9AA0A6)
    is ServerState.Starting -> Color(0xFFF9AB00)
    is ServerState.Running -> Color(0xFF34A853)
    is ServerState.Error -> Color(0xFFEA4335)
}

@Composable
private fun statusLabel(s: ServerState): String = when (s) {
    is ServerState.Stopped -> stringResource(R.string.server_stopped)
    is ServerState.Starting -> stringResource(R.string.server_starting)
    is ServerState.Running -> stringResource(R.string.server_running)
    is ServerState.Error -> stringResource(R.string.server_error)
}
