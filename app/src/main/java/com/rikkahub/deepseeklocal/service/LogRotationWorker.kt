package com.rikkahub.deepseeklocal.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rikkahub.deepseeklocal.data.repository.LogRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Periodic worker that prunes log rows older than 7 days. */
@AndroidEntryPoint
class LogRotationWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    @Inject lateinit var logRepository: LogRepository

    override suspend fun doWork(): Result = try {
        logRepository.rotate(7)
        Result.success()
    } catch (t: Throwable) {
        Result.retry()
    }
}
