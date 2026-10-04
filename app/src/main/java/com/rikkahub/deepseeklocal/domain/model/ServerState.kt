package com.rikkahub.deepseeklocal.domain.model

/** Lifecycle state of the embedded Ktor server. */
sealed interface ServerState {
    data object Stopped : ServerState
    data class Starting(val progress: Float = 0f) : ServerState
    data class Running(val baseUrl: String, val startedAtMs: Long) : ServerState
    data class Error(val message: String) : ServerState
}
