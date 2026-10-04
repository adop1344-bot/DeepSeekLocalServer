package com.rikkahub.deepseeklocal.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.rikkahub.deepseeklocal.MainActivity

/** Handles notification action buttons (Stop / Copy address). */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_STOP -> {
                val svc = Intent(context, ServerService::class.java).apply { action = ServerService.ACTION_STOP }
                context.startService(svc)
                NotificationManagerCompat.from(context).cancel(ServerService.NOTIF_ID)
            }
            ACTION_OPEN -> {
                context.startActivity(
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    companion object {
        const val ACTION_STOP = "com.rikkahub.deepseeklocal.NOTIF_STOP"
        const val ACTION_OPEN = "com.rikkahub.deepseeklocal.NOTIF_OPEN"
    }
}
