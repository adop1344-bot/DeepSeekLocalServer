package com.rikkahub.deepseeklocal.presentation.server

import android.content.Context
import com.rikkahub.deepseeklocal.service.ServerService

/** Thin facade over ServerService for the UI layer. */
object ServerController {
    fun start(ctx: Context) = ServerService.start(ctx)
    fun stop(ctx: Context) = ServerService.stop(ctx)
}
