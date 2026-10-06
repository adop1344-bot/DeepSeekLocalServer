package com.rikkahub.deepseeklocal.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rikkahub.deepseeklocal.R
import com.rikkahub.deepseeklocal.data.local.prefs.BackgroundMode
import com.rikkahub.deepseeklocal.data.local.prefs.ThemeMode
import com.rikkahub.deepseeklocal.domain.model.TokenStatus
import com.rikkahub.deepseeklocal.presentation.theme.ambientBackground
import com.rikkahub.deepseeklocal.presentation.theme.glassSurface

/** Full settings surface, iOS-glass styled. */
@Composable
fun SettingsScreen(onOpenOnboarding: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsState()
    val status by vm.tokenStatus.collectAsState()
    var show by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }

    Scaffold { padding ->
        Box(Modifier.fillMaxSize().ambientBackground().padding(padding)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.tab_settings), style = MaterialTheme.typography.headlineSmall)

                Column(
                    Modifier.fillMaxWidth().glassSurface(shape = RoundedCornerShape(22.dp), alpha = 0.5f).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.settings_token), style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = vm.tokenField.collectAsState().value,
                        onValueChange = { vm.saveToken(it) },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = { TextButton(onClick = { show = !show }) { Text(stringResource(if (show) R.string.settings_hide else R.string.settings_show)) } },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.checkToken() }) { Text(stringResource(R.string.settings_check)) }
                        OutlinedButton(onClick = onOpenOnboarding) { Text(stringResource(R.string.settings_get_token)) }
                    }
                    Text(when (status) { TokenStatus.VALID -> "✓ valid"; TokenStatus.INVALID -> "✗ invalid"; else -> "? unchecked" })
                }

                Column(
                    Modifier.fillMaxWidth().glassSurface(shape = RoundedCornerShape(22.dp), alpha = 0.5f).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(value = s.port.toString(), onValueChange = { it.toIntOrNull()?.let(vm::setPort) }, label = { Text(stringResource(R.string.settings_port)) })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = s.bindLan, onClick = { vm.setBindLan(true) }); Text(stringResource(R.string.settings_bind_lan))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = !s.bindLan, onClick = { vm.setBindLan(false) }); Text(stringResource(R.string.settings_bind_local))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.settings_model)); TextButton(onClick = { modelMenu = true }) { Text(s.model) }
                        DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                            DropdownMenuItem(text = { Text("deepseek_chat") }, onClick = { vm.setModel("deepseek_chat"); modelMenu = false })
                            DropdownMenuItem(text = { Text("deepseek_reasoner") }, onClick = { vm.setModel("deepseek_reasoner"); modelMenu = false })
                        }
                    }
                }

                Column(
                    Modifier.fillMaxWidth().glassSurface(shape = RoundedCornerShape(22.dp), alpha = 0.5f).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SwitchRow(stringResource(R.string.settings_autostart), s.autostartOnLaunch, vm::setAutostartOnLaunch)
                    SwitchRow(stringResource(R.string.settings_boot), s.autostartOnBoot, vm::setAutostartOnBoot)
                    SwitchRow(stringResource(R.string.settings_dynamic), s.dynamicColor, vm::setDynamic)
                    SwitchRow(stringResource(R.string.settings_notification), s.showNotification, vm::setNotification)
                }

                Column(
                    Modifier.fillMaxWidth().glassSurface(shape = RoundedCornerShape(22.dp), alpha = 0.5f).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.settings_theme))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.values().forEach { m ->
                            OutlinedButton(onClick = { vm.setTheme(m) }) { Text(m.name) }
                        }
                    }
                    Text(stringResource(R.string.settings_bg_mode))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BackgroundMode.values().forEach { m ->
                            OutlinedButton(onClick = { vm.setBackground(m) }) { Text(m.name) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label); Switch(checked = checked, onCheckedChange = onChange)
    }
}
