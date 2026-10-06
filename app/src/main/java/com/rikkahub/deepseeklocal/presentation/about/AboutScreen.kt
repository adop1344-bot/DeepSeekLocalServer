package com.rikkahub.deepseeklocal.presentation.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rikkahub.deepseeklocal.BuildConfig
import com.rikkahub.deepseeklocal.R

/** Static about page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen() {
    val ctx = LocalContext.current
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_about)) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.about_creator))
            Text(stringResource(R.string.about_thanks))
            Text(stringResource(R.string.about_license))
            Button(onClick = {
                val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:lexove@proton.me?subject=DeepSeekLocalServer"))
                ctx.startActivity(i)
            }) { Text(stringResource(R.string.about_report_bug)) }
        }
    }
}
