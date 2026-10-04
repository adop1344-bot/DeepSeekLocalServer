package com.rikkahub.deepseeklocal.data.local.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** User-selectable theme mode. */
enum class ThemeMode { LIGHT, DARK, SYSTEM }

/** Background persistence strategy for the server. */
enum class BackgroundMode { NORMAL, SHIZUKU }

/**
 * Immutable snapshot of all persisted user preferences.
 */
data class AppSettings(
    val port: Int = 8788,
    val bindLan: Boolean = true,
    val model: String = "deepseek_chat",
    val autostartOnLaunch: Boolean = false,
    val autostartOnBoot: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val showNotification: Boolean = true,
    val backgroundMode: BackgroundMode = BackgroundMode.NORMAL,
    val localApiKey: String = "",
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "deepseek_settings")

/**
 * DataStore-backed preference store. All reads return cold flows; writes are suspend.
 */
class SettingsDataStore(private val context: Context) {

    private object Keys {
        val PORT = intPreferencesKey("port")
        val BIND_LAN = booleanPreferencesKey("bind_lan")
        val MODEL = stringPreferencesKey("model")
        val AUTOSTART_LAUNCH = booleanPreferencesKey("autostart_launch")
        val AUTOSTART_BOOT = booleanPreferencesKey("autostart_boot")
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val NOTIFICATION = booleanPreferencesKey("notification")
        val BACKGROUND = stringPreferencesKey("background")
        val LOCAL_KEY = stringPreferencesKey("local_key")
    }

    /** Cold stream of the whole settings snapshot, defaulting where unset. */
    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            port = p[Keys.PORT] ?: 8788,
            bindLan = p[Keys.BIND_LAN] ?: true,
            model = p[Keys.MODEL] ?: "deepseek_chat",
            autostartOnLaunch = p[Keys.AUTOSTART_LAUNCH] ?: false,
            autostartOnBoot = p[Keys.AUTOSTART_BOOT] ?: false,
            themeMode = runCatching { ThemeMode.valueOf(p[Keys.THEME] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
            dynamicColor = p[Keys.DYNAMIC] ?: true,
            showNotification = p[Keys.NOTIFICATION] ?: true,
            backgroundMode = runCatching { BackgroundMode.valueOf(p[Keys.BACKGROUND] ?: "NORMAL") }.getOrDefault(BackgroundMode.NORMAL),
            localApiKey = p[Keys.LOCAL_KEY] ?: "",
        )
    }

    suspend fun setPort(value: Int) = context.dataStore.edit { it[Keys.PORT] = value.coerceIn(1024, 65535) }
    suspend fun setBindLan(value: Boolean) = context.dataStore.edit { it[Keys.BIND_LAN] = value }
    suspend fun setModel(value: String) = context.dataStore.edit { it[Keys.MODEL] = value }
    suspend fun setAutostartOnLaunch(value: Boolean) = context.dataStore.edit { it[Keys.AUTOSTART_LAUNCH] = value }
    suspend fun setAutostartOnBoot(value: Boolean) = context.dataStore.edit { it[Keys.AUTOSTART_BOOT] = value }
    suspend fun setThemeMode(value: ThemeMode) = context.dataStore.edit { it[Keys.THEME] = value.name }
    suspend fun setDynamicColor(value: Boolean) = context.dataStore.edit { it[Keys.DYNAMIC] = value }
    suspend fun setShowNotification(value: Boolean) = context.dataStore.edit { it[Keys.NOTIFICATION] = value }
    suspend fun setBackgroundMode(value: BackgroundMode) = context.dataStore.edit { it[Keys.BACKGROUND] = value.name }
    suspend fun setLocalApiKey(value: String) = context.dataStore.edit { it[Keys.LOCAL_KEY] = value }
}
