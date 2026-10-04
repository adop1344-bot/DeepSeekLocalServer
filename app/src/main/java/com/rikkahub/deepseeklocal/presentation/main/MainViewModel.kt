package com.rikkahub.deepseeklocal.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rikkahub.deepseeklocal.data.local.prefs.SettingsDataStore
import com.rikkahub.deepseeklocal.data.local.prefs.TokenStore
import com.rikkahub.deepseeklocal.domain.model.ServerState
import com.rikkahub.deepseeklocal.service.LanAddress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Drives the main status screen. */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val settings: SettingsDataStore,
    private val tokenStore: TokenStore,
) : ViewModel() {

    private val _state = MutableStateFlow<ServerState>(ServerState.Stopped)
    val state: StateFlow<ServerState> = _state.asStateFlow()

    private val _hasToken = MutableStateFlow(false)
    val hasToken: StateFlow<Boolean> = _hasToken.asStateFlow()

    init { refresh() }

    /** Recomputes the base URL and token presence. */
    fun refresh() {
        viewModelScope.launch {
            _hasToken.value = tokenStore.getToken() != null
            val s = settings.settings.first()
            if (_state.value is ServerState.Running) {
                val ip = if (s.bindLan) LanAddress.find() else "127.0.0.1"
                _state.value = ServerState.Running("http://$ip:${s.port}/v1", System.currentTimeMillis())
            }
        }
    }

    /** Marks the server as started. */
    fun onStarted(port: Int, bindLan: Boolean) {
        viewModelScope.launch {
            val ip = if (bindLan) LanAddress.find() else "127.0.0.1"
            _state.value = ServerState.Running("http://$ip:$port/v1", System.currentTimeMillis())
        }
    }

    /** Marks the server as stopped. */
    fun onStopped() { _state.value = ServerState.Stopped }
}
