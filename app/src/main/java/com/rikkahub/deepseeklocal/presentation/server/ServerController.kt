package com.rikkahub.deepseeklocal.presentation.server

import android.annotation.SuppressLint
import android.content.Context
import com.rikkahub.deepseeklocal.service.ServerService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Thin facade over ServerService for the UI layer. */
@Singleton
class ServerController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Starts the foreground server service. */
    @SuppressLint("MissingPermission")
    fun start() = ServerService.start(context)

    /** Stops the foreground server service. */
    fun stop() = ServerService.stop(context)
}
