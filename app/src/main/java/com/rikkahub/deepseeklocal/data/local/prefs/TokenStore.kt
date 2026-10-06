package com.rikkahub.deepseeklocal.data.local.prefs

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

/**
 * Encrypted-at-rest storage for multiple DeepSeek web tokens + optional official API key.
 * Mirrors the ~/deepseek Node reference: a list of tokens, one active, per-token fail
 * counter with a 10-minute cooldown after 3 consecutive failures, and automatic
 * rotation to the next healthy token.
 */
class TokenStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    data class Entry(
        val id: Int,
        val name: String,
        val token: String,
        val addedAt: Long = System.currentTimeMillis(),
        val updatedAt: Long = 0L,
        val failCount: Int = 0,
        val lastFailAt: Long = 0L,
    )

    private fun loadList(): MutableList<Entry> {
        val raw = prefs.getString(KEY_LIST, null) ?: return mutableListOf()
        return try {
            val arr = JSONArray(raw)
            val out = mutableListOf<Entry>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    Entry(
                        id = o.optInt("id"),
                        name = o.optString("name"),
                        token = o.optString("token"),
                        addedAt = o.optLong("addedAt"),
                        updatedAt = o.optLong("updatedAt"),
                        failCount = o.optInt("failCount"),
                        lastFailAt = o.optLong("lastFailAt"),
                    ),
                )
            }
            out
        } catch (t: Throwable) {
            mutableListOf()
        }
    }

    private fun saveList(list: List<Entry>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(JSONObject().apply {
                put("id", e.id); put("name", e.name); put("token", e.token)
                put("addedAt", e.addedAt); put("updatedAt", e.updatedAt)
                put("failCount", e.failCount); put("lastFailAt", e.lastFailAt)
            })
        }
        prefs.edit().putString(KEY_LIST, arr.toString()).apply()
    }

    private var activeId: Int
        get() = prefs.getInt(KEY_ACTIVE, -1)
        set(v) { prefs.edit().putInt(KEY_ACTIVE, v).apply() }

    private val cooldownMs = 10 * 60 * 1000L

    private fun isCooling(e: Entry): Boolean {
        if (e.failCount < 3) return false
        val age = System.currentTimeMillis() - (if (e.lastFailAt > 0) e.lastFailAt else e.addedAt)
        return age < cooldownMs
    }

    private fun sweep(list: MutableList<Entry>) {
        var changed = false
        list.forEachIndexed { i, e ->
            if (e.failCount >= 3 && !isCooling(e)) { list[i] = e.copy(failCount = 0); changed = true }
        }
        if (changed) saveList(list)
    }

    // ----- public API -----

    fun list(): List<Entry> = loadList()

    fun add(token: String, name: String? = null): Int {
        val list = loadList()
        val id = (list.maxOfOrNull { it.id } ?: 0) + 1
        list.add(Entry(id = id, name = name ?: "token $id", token = token))
        saveList(list)
        if (activeId <= 0) activeId = id
        return id
    }

    fun update(id: Int, token: String): Boolean {
        val list = loadList()
        val i = list.indexOfFirst { it.id == id }
        if (i < 0) return false
        list[i] = list[i].copy(token = token, failCount = 0, updatedAt = System.currentTimeMillis())
        saveList(list); return true
    }

    fun remove(id: Int) {
        val list = loadList().filterNot { it.id == id }.toMutableList()
        if (activeId == id) activeId = list.firstOrNull()?.id ?: -1
        saveList(list)
    }

    fun setActive(id: Int) {
        if (loadList().any { it.id == id }) activeId = id
    }

    fun markFail(id: Int) {
        val list = loadList()
        val i = list.indexOfFirst { it.id == id }
        if (i < 0) return
        list[i] = list[i].copy(failCount = list[i].failCount + 1, lastFailAt = System.currentTimeMillis())
        saveList(list)
    }

    /** Current healthy token, rotating away from cooling tokens. Null if none. */
    fun getToken(): String? {
        val list = loadList()
        if (list.isEmpty()) return null
        sweep(list)
        val active = list.firstOrNull { it.id == activeId }
        if (active != null && !isCooling(active)) return active.token
        val ok = list.firstOrNull { !isCooling(it) }
        if (ok != null) { activeId = ok.id; return ok.token }
        val earliest = list.minByOrNull { it.lastFailAt }
        if (earliest != null) { activeId = earliest.id; return earliest.token }
        return null
    }

    /** Legacy single-token setter: replaces the active token or adds a first one. */
    fun setToken(value: String) {
        val list = loadList()
        val i = list.indexOfFirst { it.id == activeId }
        if (i >= 0) { list[i] = list[i].copy(token = value, failCount = 0) }
        else { add(value); return }
        saveList(list)
    }

    /** Marks the currently active token as failed (used on upstream errors). */
    fun markFailActive() {
        val id = activeId
        if (id > 0) markFail(id)
    }

    fun clearToken() {
        prefs.edit().remove(KEY_LIST).remove(KEY_ACTIVE).apply()
    }

    fun getApiKey(): String? = prefs.getString(KEY_API, null)?.takeIf { it.isNotBlank() }
    fun setApiKey(value: String) { prefs.edit().putString(KEY_API, value).apply() }

    companion object {
        private const val FILE_NAME = "deepseek_secure_prefs"
        private const val KEY_LIST = "deepseek_web_tokens"
        private const val KEY_ACTIVE = "deepseek_web_token_active"
        private const val KEY_API = "deepseek_api_key"
    }
}
