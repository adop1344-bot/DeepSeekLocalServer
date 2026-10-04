package com.rikkahub.deepseeklocal.data.repository

import com.rikkahub.deepseeklocal.data.local.prefs.AppSettings
import com.rikkahub.deepseeklocal.data.local.prefs.BackgroundMode
import com.rikkahub.deepseeklocal.data.local.prefs.SettingsDataStore
import com.rikkahub.deepseeklocal.data.local.prefs.ThemeMode
import com.rikkahub.deepseeklocal.data.local.prefs.TokenStore
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Single access point for persisted settings and the encrypted token. */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: SettingsDataStore,
    private val tokenStore: TokenStore,
) {
    val settings: Flow<AppSettings> = dataStore.settings

    suspend fun setPort(v: Int) = dataStore.setPort(v)
    suspend fun setBindLan(v: Boolean) = dataStore.setBindLan(v)
    suspend fun setModel(v: String) = dataStore.setModel(v)
    suspend fun setAutostartOnLaunch(v: Boolean) = dataStore.setAutostartOnLaunch(v)
    suspend fun setAutostartOnBoot(v: Boolean) = dataStore.setAutostartOnBoot(v)
    suspend fun setThemeMode(v: ThemeMode) = dataStore.setThemeMode(v)
    suspend fun setDynamicColor(v: Boolean) = dataStore.setDynamicColor(v)
    suspend fun setShowNotification(v: Boolean) = dataStore.setShowNotification(v)
    suspend fun setBackgroundMode(v: BackgroundMode) = dataStore.setBackgroundMode(v)
    suspend fun setLocalApiKey(v: String) = dataStore.setLocalApiKey(v)

    fun getToken(): String? = tokenStore.getToken()
    fun setToken(v: String) = tokenStore.setToken(v)
    fun clearToken() = tokenStore.clearToken()
}
