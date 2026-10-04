package com.rikkahub.deepseeklocal.service

import android.util.Log
import com.rikkahub.deepseeklocal.data.local.db.LogDao
import com.rikkahub.deepseeklocal.data.local.db.LogEntity
import com.rikkahub.deepseeklocal.domain.model.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Captures logcat lines belonging to this app's PID and mirrors them into Room.
 * Runs a single background thread; safe to start/stop repeatedly.
 */
class LogcatInterceptor(private val dao: LogDao, private val scope: CoroutineScope) {

    private val running = AtomicBoolean(false)
    private var job: Job? = null

    /** Starts the logcat reader if not already running. */
    fun start() {
        if (!running.compareAndSet(false, true)) return
        job = scope.launch(Dispatchers.IO) {
            val pid = android.os.Process.myPid().toString()
            try {
                val process = ProcessBuilder("logcat", "-v", "brief", "--pid=$pid").redirectErrorStream(true).start()
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    while (running.get()) {
                        val line = reader.readLine() ?: break
                        persist(line)
                    }
                }
                process.destroy()
            } catch (t: Throwable) {
                Log.w(TAG, "logcat reader stopped", t)
            } finally {
                running.set(false)
            }
        }
    }

    /** Stops the logcat reader. */
    fun stop() {
        running.set(false)
        job?.cancel()
        job = null
    }

    private fun persist(raw: String) {
        val level = when {
            raw.contains(" E ") -> LogLevel.ERROR
            raw.contains(" W ") -> LogLevel.WARN
            raw.contains(" D ") -> LogLevel.DEBUG
            else -> LogLevel.INFO
        }
        val tag = raw.substringAfter(' ').substringBefore(':').trim().take(24).ifEmpty { "app" }
        scope.launch {
            runCatching {
                dao.insert(LogEntity(timestamp = System.currentTimeMillis(), level = level.name, tag = tag, message = raw.take(2000)))
            }
        }
    }

    companion object {
        private const val TAG = "LogcatInterceptor"
    }
}
