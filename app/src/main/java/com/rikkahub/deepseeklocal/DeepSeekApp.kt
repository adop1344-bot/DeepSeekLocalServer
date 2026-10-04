package com.rikkahub.deepseeklocal

import android.app.Application
import android.util.Log
import com.rikkahub.deepseeklocal.data.repository.LogRepository
import com.rikkahub.deepseeklocal.service.LogcatInterceptor
import com.rikkahub.deepseeklocal.shizuku.ShizukuHelper
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import javax.inject.Inject

/** Application entry point. Wires Shizuku listeners and starts logcat capture. */
@HiltAndroidApp
class DeepSeekApp : Application() {

    @Inject lateinit var logRepository: LogRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var logcat: LogcatInterceptor? = null

    override fun onCreate() {
        super.onCreate()
        val shizuku = ShizukuHelper(this)
        try {
            Shizuku.addBinderReceivedListener {
                Log.i(TAG, "Shizuku binder received")
            }
            Shizuku.addBinderDeadListener {
                Log.i(TAG, "Shizuku binder dead")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Shizuku listeners unavailable", t)
        }
        // Note: interceptor needs a DAO; keep the current app scope simple by
        // starting it lazily from ServerService instead.
        scope.launch {
            logRepository.append(com.rikkahub.deepseeklocal.domain.model.LogLevel.INFO, "App", "application started")
        }
    }

    companion object { private const val TAG = "DeepSeekApp" }
}
