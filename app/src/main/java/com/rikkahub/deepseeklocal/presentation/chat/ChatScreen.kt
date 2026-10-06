package com.rikkahub.deepseeklocal.presentation.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rikkahub.deepseeklocal.R
import com.rikkahub.deepseeklocal.domain.model.ChatMessage
import com.rikkahub.deepseeklocal.domain.model.ChatRole
import com.rikkahub.deepseeklocal.presentation.theme.ambientBackground
import com.rikkahub.deepseeklocal.presentation.theme.glassSurface

/** Built-in chat surface, iOS-glass styled. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: ChatViewModel = hiltViewModel()) {
    val messages by vm.messages.collectAsState()
    var input by remember { mutableStateOf("") }
    val generating = messages.any { it.isStreaming }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold { padding ->
        Box(Modifier.fillMaxSize().ambientBackground().padding(padding)) {
            Column(Modifier.fillMaxSize().imePadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.tab_chat), style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = { vm.clear() }) { Text(stringResource(R.string.chat_clear)) }
                }
                if (messages.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.chat_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(messages, key = { it.id }) { m -> Bubble(m) }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = input, onValueChange = { input = it },
                        modifier = Modifier.weight(1f).glassSurface(shape = RoundedCornerShape(22.dp), alpha = 0.45f),
                        placeholder = { Text(stringResource(R.string.chat_hint)) }, maxLines = 4,
                    )
                    Spacer(Modifier.height(8.dp))
                    FilledIconButton(onClick = {
                        if (generating) vm.stop() else { vm.send(input); input = "" }
                    }) { Icon(if (generating) Icons.Filled.Stop else Icons.AutoMirrored.Filled.Send, contentDescription = null) }
                }
            }
        }
    }
}

@Composable
private fun Bubble(m: ChatMessage) {
    val mine = m.role == ChatRole.USER
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .widthIn(max = 320.dp)
                .glassSurface(
                    shape = RoundedCornerShape(22.dp),
                    tint = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    alpha = if (mine) 0.72f else 0.5f,
                )
                .padding(12.dp),
        ) {
            if (m.reasoningText.isNotBlank()) {
                Text("[thinking] ${m.reasoningText}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
            }
            Text(m.text + if (m.isStreaming) " ▍" else "")
        }
    }
}
