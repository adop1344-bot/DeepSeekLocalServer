package com.rikkahub.deepseeklocal.domain

import com.rikkahub.deepseeklocal.domain.model.ServerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide source of truth for the embedded server lifecycle.
 * Both [com.rikkahub.deepseeklocal.service.ServerService] and the UI observe this,
 * so the status screen always reflects reality regardless of tab switches.
 */
@Singleton
class ServerStateHolder @Inject constructor() {
    private val _state = MutableStateFlow<ServerState>(ServerState.Stopped)
    val state: StateFlow<ServerState> = _state.asStateFlow()

    fun set(value: ServerState) { _state.value = value }
    val current: ServerState get() = _state.value
}
