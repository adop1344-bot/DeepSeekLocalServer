package com.rikkahub.deepseeklocal.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rikkahub.deepseeklocal.data.repository.LogRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** Periodic worker that prunes log rows older than 7 days. */
class LogRotationWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    /** Hilt entry point for retrieving the LogRepository inside a WorkManager worker. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun logRepository(): LogRepository
    }

    override suspend fun doWork(): Result = try {
        val ep = EntryPointAccessors.fromApplication(applicationContext, WorkerEntryPoint::class.java)
        ep.logRepository().rotate(7)
        Result.success()
    } catch (t: Throwable) {
        Result.retry()
    }
}
