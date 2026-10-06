package com.rikkahub.deepseeklocal.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rikkahub.deepseeklocal.data.local.prefs.SettingsDataStore
import com.rikkahub.deepseeklocal.data.local.prefs.TokenStore
import com.rikkahub.deepseeklocal.domain.ServerStateHolder
import com.rikkahub.deepseeklocal.domain.model.ServerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Drives the main status screen. Reads the process-wide [ServerStateHolder]. */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val settings: SettingsDataStore,
    private val tokenStore: TokenStore,
    private val stateHolder: ServerStateHolder,
) : ViewModel() {

    /** Live server lifecycle state, shared with the service. */
    val state: StateFlow<ServerState> = stateHolder.state

    private val _hasToken = MutableStateFlow(false)
    val hasToken: StateFlow<Boolean> = _hasToken.asStateFlow()

    init { refresh() }

    /** Recomputes token presence (state comes from the holder). */
    fun refresh() {
        viewModelScope.launch {
            _hasToken.value = tokenStore.getToken() != null
            settings.settings.first()
        }
    }
}
