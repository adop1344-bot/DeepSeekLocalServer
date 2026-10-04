package com.rikkahub.deepseeklocal.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rikkahub.deepseeklocal.data.local.prefs.AppSettings
import com.rikkahub.deepseeklocal.data.local.prefs.BackgroundMode
import com.rikkahub.deepseeklocal.data.local.prefs.SettingsDataStore
import com.rikkahub.deepseeklocal.data.local.prefs.ThemeMode
import com.rikkahub.deepseeklocal.data.local.prefs.TokenStore
import com.rikkahub.deepseeklocal.data.repository.ChatRepository
import com.rikkahub.deepseeklocal.domain.model.TokenStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backs the settings screen. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStore: SettingsDataStore,
    private val tokenStore: TokenStore,
    private val chatRepository: ChatRepository,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = dataStore.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())
    val tokenStatus = MutableStateFlow(TokenStatus.UNKNOWN)
    val tokenField = MutableStateFlow("")

    init { tokenField.value = tokenStore.getToken().orEmpty() }

    fun saveToken(t: String) { tokenStore.setToken(t); tokenField.value = t; tokenStatus.value = TokenStatus.UNKNOWN }
    fun clearToken() { tokenStore.clearToken(); tokenField.value = ""; tokenStatus.value = TokenStatus.UNKNOWN }
    fun checkToken() = viewModelScope.launch {
        val t = tokenStore.getToken() ?: return@launch
        tokenStatus.value = if (chatRepository.validateToken(t)) TokenStatus.VALID else TokenStatus.INVALID
    }
    fun setPort(v: Int) = viewModelScope.launch { dataStore.setPort(v) }
    fun setBindLan(v: Boolean) = viewModelScope.launch { dataStore.setBindLan(v) }
    fun setModel(v: String) = viewModelScope.launch { dataStore.setModel(v) }
    fun setAutostartOnLaunch(v: Boolean) = viewModelScope.launch { dataStore.setAutostartOnLaunch(v) }
    fun setAutostartOnBoot(v: Boolean) = viewModelScope.launch { dataStore.setAutostartOnBoot(v) }
    fun setTheme(v: ThemeMode) = viewModelScope.launch { dataStore.setThemeMode(v) }
    fun setDynamic(v: Boolean) = viewModelScope.launch { dataStore.setDynamicColor(v) }
    fun setNotification(v: Boolean) = viewModelScope.launch { dataStore.setShowNotification(v) }
    fun setBackground(v: BackgroundMode) = viewModelScope.launch { dataStore.setBackgroundMode(v) }
}
