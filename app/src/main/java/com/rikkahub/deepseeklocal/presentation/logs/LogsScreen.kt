package com.rikkahub.deepseeklocal.presentation.logs

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rikkahub.deepseeklocal.R
import com.rikkahub.deepseeklocal.domain.model.LogEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Filterable log list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(vm: LogsViewModel = hiltViewModel()) {
    val logs by vm.logs.collectAsState()
    val filter by vm.filter.collectAsState()
    val level by vm.level.collectAsState()
    val context = LocalContext.current
    val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_logs)) },
                actions = {
                    TextButton(onClick = {
                        val text = logs.joinToString("\n") { "${fmt.format(Date(it.timestamp))} ${it.level} ${it.tag}: ${it.message}" }
                        val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
                        context.startActivity(Intent.createChooser(i, null))
                    }) { Text(stringResource(R.string.logs_share)) }
                    TextButton(onClick = { vm.clear() }) { Text(stringResource(R.string.logs_clear)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(8.dp)) {
            OutlinedTextField(value = filter, onValueChange = { vm.filter.value = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text(stringResource(R.string.logs_search)) })
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(null, "DEBUG", "INFO", "WARN", "ERROR").forEach { lv ->
                    FilterChip(selected = level == lv, onClick = { vm.level.value = lv }, label = { Text(lv ?: stringResource(R.string.logs_filter_all)) })
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(logs, key = { it.id }) { e -> LogRow(e, fmt) }
            }
        }
    }
}

@Composable
private fun LogRow(e: LogEntry, fmt: SimpleDateFormat) {
    Text(
        "${fmt.format(Date(e.timestamp))}  ${e.level.name.take(1)}  ${e.tag}: ${e.message}",
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    )
}
