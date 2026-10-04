package com.rikkahub.deepseeklocal.presentation.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rikkahub.deepseeklocal.R
import kotlinx.coroutines.launch

/** 5-page wizard that explains how to obtain the DeepSeek web token. */
@Composable
fun OnboardingScreen(onDone: () -> Unit, vm: OnboardingViewModel = hiltViewModel()) {
    val pager = rememberPagerState(pageCount = { 5 })
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    var tokenInput by remember { mutableStateOf("") }

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (page) {
                        0 -> { Text(stringResource(R.string.onboarding_step1_title), style = MaterialTheme.typography.headlineSmall); Text(stringResource(R.string.onboarding_step1_body)) }
                        1 -> { Text(stringResource(R.string.onboarding_step2_title), style = MaterialTheme.typography.headlineSmall); Text(stringResource(R.string.onboarding_step2_body)); OutlinedButton(onClick = { clipboard.setText(AnnotatedString(vm.bookmarklet)); }) { Text(stringResource(R.string.onboarding_copy_bookmarklet)) } }
                        2 -> { Text(stringResource(R.string.onboarding_step3_title), style = MaterialTheme.typography.headlineSmall); Text(stringResource(R.string.onboarding_step3_body)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = { clipboard.setText(AnnotatedString("https://chat.deepseek.com/sign_in")) }) { Text("Copy URL") }; Button(onClick = { vm.openUrl(ctx, "https://chat.deepseek.com/sign_in") }) { Text(stringResource(R.string.onboarding_open_browser)) } } }
                        3 -> { Text(stringResource(R.string.onboarding_step4_title), style = MaterialTheme.typography.headlineSmall); Text(stringResource(R.string.onboarding_step4_body)) }
                        else -> { Text(stringResource(R.string.onboarding_step5_title), style = MaterialTheme.typography.headlineSmall); Text(stringResource(R.string.onboarding_step5_body)); OutlinedTextField(value = tokenInput, onValueChange = { tokenInput = it }, modifier = Modifier.fillMaxWidth()); Button(onClick = { vm.saveToken(tokenInput); onDone() }) { Text(stringResource(R.string.common_ok)) } }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { scope.launch { if (pager.currentPage > 0) pager.animateScrollToPage(pager.currentPage - 1) } }) { Text(stringResource(R.string.onboarding_back)) }
                TextButton(onClick = { scope.launch { if (pager.currentPage < 4) pager.animateScrollToPage(pager.currentPage + 1) else onDone() } }) { Text(stringResource(if (pager.currentPage < 4) R.string.onboarding_next else R.string.onboarding_done)) }
            }
        }
    }
}
