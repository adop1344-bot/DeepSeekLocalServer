package com.rikkahub.deepseeklocal.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.rikkahub.deepseeklocal.MainActivity
import com.rikkahub.deepseeklocal.R
import com.rikkahub.deepseeklocal.data.local.prefs.SettingsDataStore
import com.rikkahub.deepseeklocal.data.remote.deepseek.DeepSeekClient
import com.rikkahub.deepseeklocal.data.repository.LogRepository
import com.rikkahub.deepseeklocal.domain.model.LogLevel
import com.rikkahub.deepseeklocal.server.OpenAiRoutes
import com.rikkahub.deepseeklocal.server.SessionManager
import dagger.hilt.android.AndroidEntryPoint
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.stop
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service hosting the embedded Ktor server.
 * Acquires a partial wake lock and keeps the notification for the entire lifetime.
 */
@AndroidEntryPoint
class ServerService : Service() {

    @Inject lateinit var settings: SettingsDataStore
    @Inject lateinit var tokenStore: com.rikkahub.deepseeklocal.data.local.prefs.TokenStore
    @Inject lateinit var client: DeepSeekClient
    @Inject lateinit var sessions: SessionManager
    @Inject lateinit var logRepository: LogRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var engine: io.ktor.server.engine.EmbeddedServer<*, *>? = null
    private var startedAt = 0L
    private var currentUrl: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopEverything(); return START_NOT_STICKY }
            ACTION_START, null -> { ensureChannel(); startForegroundWithNotification(); startServer() }
        }
        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification("Запуск…")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun startServer() {
        scope.launch {
            try {
                val cfg = settings.settings.first()
                val token = tokenStore.getToken()
                startedAt = System.currentTimeMillis()
                val host = if (cfg.bindLan) "0.0.0.0" else "127.0.0.1"
                val lanIp = if (cfg.bindLan) LanAddress.find() else "127.0.0.1"
                currentUrl = "http://$lanIp:${cfg.port}/v1"

                val routes = OpenAiRoutes(
                    client = client,
                    sessions = sessions,
                    tokenProvider = { tokenStore.getToken() },
                    modelProvider = { cfg.model },
                    onTokenCaptured = { tokenStore.setToken(it) },
                    onTokenAction = { action, id, tok ->
                        when (action) {
                            "add" -> { if (tok != null) tokenStore.add(tok); true }
                            "update" -> { if (tok != null) tokenStore.update(id, tok) else false }
                            "remove" -> { tokenStore.remove(id); true }
                            "setActive" -> { tokenStore.setActive(id); true }
                            else -> false
                        }
                    },
                    onTokenFailed = { tokenStore.markFailActive() },
                    onLog = { level, msg -> scope.launch { logRepository.append(runCatching { LogLevel.valueOf(level) }.getOrDefault(LogLevel.INFO), "Server", msg) } },
                )

                engine = embeddedServer(Netty, port = cfg.port, host = host) {
                    install(ContentNegotiation) { json() }
                    routing { routes.install(this) }
                }.start(wait = false)

                logRepository.append(LogLevel.INFO, "Server", "started on $host:${cfg.port}")
                updateNotification("Слушает $currentUrl (uptime: 0s)")
                startUptimeUpdater()
            } catch (t: Throwable) {
                Log.e(TAG, "server start failed", t)
                scope.launch { logRepository.append(LogLevel.ERROR, "Server", "start failed: ${t.message}") }
                updateNotification("Ошибка: ${t.message}")
            }
        }
    }

    private fun startUptimeUpdater() {
        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(60_000L)
                val uptime = (System.currentTimeMillis() - startedAt) / 1000
                updateNotification("Слушает $currentUrl (uptime: ${uptime / 60}m)")
            }
        }
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getBroadcast(
            this, 1,
            Intent(this, NotificationActionReceiver::class.java).apply { action = NotificationActionReceiver.ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.notif_action_stop), stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                        description = getString(R.string.notif_channel_desc)
                    },
                )
            }
        }
    }

    private fun stopEverything() {
        try { engine?.stop(1000, 2000) } catch (_: Throwable) {}
        engine = null
        scope.launch { logRepository.append(LogLevel.INFO, "Server", "stopped") }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        try { engine?.stop(1000, 2000) } catch (_: Throwable) {}
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ServerService"
        const val CHANNEL_ID = "deepseek_server"
        const val NOTIF_ID = 7801
        const val ACTION_START = "com.rikkahub.deepseeklocal.START"
        const val ACTION_STOP = "com.rikkahub.deepseeklocal.STOP"

        fun start(context: Context) {
            val i = Intent(context, ServerService::class.java).apply { action = ACTION_START }
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            val i = Intent(context, ServerService::class.java).apply { action = ACTION_STOP }
            context.startService(i)
        }
    }
}
