package com.rikkahub.deepseeklocal.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rikkahub.deepseeklocal.data.local.prefs.SettingsDataStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Starts the server after boot when autostart-on-boot is enabled. */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var settings: SettingsDataStore

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON") return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val s = settings.settings.first()
                if (s.autostartOnBoot) {
                    val svc = Intent(context, ServerService::class.java).apply { action = ServerService.ACTION_START }
                    context.startForegroundService(svc)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
