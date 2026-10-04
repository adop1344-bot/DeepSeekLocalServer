package com.rikkahub.deepseeklocal.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Process
import android.util.Log
import rikka.shizuku.Shizuku

/**
 * Thin wrapper around the Rikka Shizuku API for permission negotiation and
 * shell-level process spawning. Degrades to no-op when Shizuku is unavailable.
 */
class ShizukuHelper(private val context: Context) {

    /** True when the Shizuku manager app is installed. */
    fun isAvailable(): Boolean = try {
        Shizuku.pingBinder()
    } catch (t: Throwable) {
        false
    }

    /** True when our app has been granted the Shizuku permission. */
    fun hasPermission(): Boolean = try {
        isAvailable() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        false
    }

    /** Requests the Shizuku permission; the callback receives the grant result. */
    fun requestPermission(requestCode: Int) {
        if (!isAvailable()) return
        if (hasPermission()) return
        try {
            Shizuku.requestPermission(requestCode)
        } catch (t: Throwable) {
            Log.w(TAG, "requestPermission failed", t)
        }
    }

    /**
     * Starts the given service via a Shizuku shell process, which survives app-kill
     * on Android 12+ where normal background starts are throttled.
     */
    fun startServiceViaShizuku(serviceClass: Class<*>): Boolean = try {
        if (!hasPermission()) return false
        val component = ComponentName(context, serviceClass)
        val cmd = arrayOf(
            "am", "start-foreground-service",
            "-n", component.flattenToString(),
        )
        val process = Shizuku.newProcess(cmd, null, null)
        process.waitFor()
        true
    } catch (t: Throwable) {
        Log.w(TAG, "startServiceViaShizuku failed", t)
        false
    }

    companion object {
        private const val TAG = "ShizukuHelper"
        /** Arbitrary request code for the permission dialog. */
        const val REQUEST_CODE = 12001
    }
}
