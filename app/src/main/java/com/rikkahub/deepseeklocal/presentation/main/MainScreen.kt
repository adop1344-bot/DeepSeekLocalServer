package com.rikkahub.deepseeklocal.presentation.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rikkahub.deepseeklocal.R
import com.rikkahub.deepseeklocal.domain.model.ServerState
import com.rikkahub.deepseeklocal.presentation.server.ServerController
import kotlinx.coroutines.launch

/** Main status screen with Material You styling. */
@Composable
fun MainScreen(vm: MainViewModel = hiltViewModel(), onOpenChat: () -> Unit = {}) {
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val appCtx = LocalContext.current

    val running = state is ServerState.Running
    val starting = state is ServerState.Starting
    val url = (state as? ServerState.Running)?.baseUrl ?: "—"
    val startedAt = (state as? ServerState.Running)?.startedAtMs ?: 0L

    // Pulsing animation for the running indicator.
    val pulse = rememberInfiniteTransition(label = "pulse")
    val ringScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (running) 1.18f else 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "ringScale",
    )
    val ringAlpha by pulse.animateFloat(
        initialValue = 0.45f,
        targetValue = if (running) 0.05f else 0.45f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "ringAlpha",
    )

    val accent = statusColor(state)
    val accentAnimated by animateColorAsState(accent, label = "accent")
    val buttonContainer by animateColorAsState(
        if (running) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
        label = "btnContainer",
    )
    val buttonContent by animateColorAsState(
        if (running) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
        label = "btnContent",
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Hero status card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(160.dp)) {
                        // animated halo ring
                        Box(
                            Modifier
                                .size(140.dp)
                                .scale(ringScale)
                                .background(accentAnimated.copy(alpha = ringAlpha), CircleShape),
                        )
                        // solid status disc
                        Box(
                            Modifier
                                .size(120.dp)
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            accentAnimated,
                                            accentAnimated.copy(alpha = 0.78f),
                                        ),
                                    ),
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Filled.PowerSettingsNew,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(34.dp),
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    statusLabel(state),
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Primary action
                    Button(
                        onClick = {
                            if (running) ServerController.stop(appCtx)
                            else ServerController.start(appCtx)
                        },
                        enabled = !starting,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = buttonContainer,
                            contentColor = buttonContent,
                        ),
                    ) {
                        Icon(Icons.Filled.PowerSettingsNew, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(if (running) R.string.server_stop else R.string.server_start),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Address card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        stringResource(R.string.tab_server).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                url,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        FilledTonalButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(url))
                                scope.launch { snackbar.showSnackbar("Скопировано") }
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }

                    if (running) {
                        Spacer(Modifier.height(14.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            InfoPill(
                                icon = { Icon(Icons.Filled.Timer, null, Modifier.size(16.dp)) },
                                text = uptime(startedAt),
                                modifier = Modifier.weight(1f),
                            )
                            InfoPill(
                                icon = { Icon(Icons.Filled.Speed, null, Modifier.size(16.dp)) },
                                text = "Online",
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Quick actions
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                QuickAction(
                    icon = Icons.Filled.Chat,
                    label = stringResource(R.string.tab_chat),
                    onClick = onOpenChat,
                    modifier = Modifier.weight(1f),
                )
                QuickAction(
                    icon = Icons.Filled.QrCode,
                    label = "QR",
                    onClick = {
                        clipboard.setText(AnnotatedString(url))
                        scope.launch { snackbar.showSnackbar("Адрес скопирован") }
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.common_http_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun InfoPill(icon: @Composable () -> Unit, text: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(16.dp)) { icon() }
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun QuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(84.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Column(
            Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun uptime(startedAtMs: Long): String {
    if (startedAtMs <= 0L) return "—"
    val secs = (System.currentTimeMillis() - startedAtMs) / 1000
    val h = secs / 3600
    val m = (secs % 3600) / 60
    val s = secs % 60
    return if (h > 0) "${h}ч ${m}м" else if (m > 0) "${m}м ${s}с" else "${s}с"
}

private fun statusColor(s: ServerState): Color = when (s) {
    is ServerState.Stopped -> Color(0xFF8A8F98)
    is ServerState.Starting -> Color(0xFFF9AB00)
    is ServerState.Running -> Color(0xFF2E9E5B)
    is ServerState.Error -> Color(0xFFD93025)
}

@Composable
private fun statusLabel(s: ServerState): String = when (s) {
    is ServerState.Stopped -> stringResource(R.string.server_stopped)
    is ServerState.Starting -> stringResource(R.string.server_starting)
    is ServerState.Running -> stringResource(R.string.server_running)
    is ServerState.Error -> stringResource(R.string.server_error)
}
