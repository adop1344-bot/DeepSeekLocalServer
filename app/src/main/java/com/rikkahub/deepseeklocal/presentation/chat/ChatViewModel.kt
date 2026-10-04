package com.rikkahub.deepseeklocal.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rikkahub.deepseeklocal.data.local.prefs.SettingsDataStore
import com.rikkahub.deepseeklocal.domain.model.ChatMessage
import com.rikkahub.deepseeklocal.domain.model.ChatRole
import com.rikkahub.deepseeklocal.domain.model.ChatState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID
import javax.inject.Inject

/** Drives the built-in chat screen by talking to the local OpenAI-compatible endpoint. */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val settings: SettingsDataStore,
    private val okHttp: OkHttpClient,
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _state = MutableStateFlow<ChatState>(ChatState.Idle)
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private var job: Job? = null

    /** Sends [text] to the local server, streaming deltas into the message list. */
    fun send(text: String) {
        if (text.isBlank()) return
        val user = ChatMessage(id = UUID.randomUUID().toString(), role = ChatRole.USER, text = text)
        val assistant = ChatMessage(id = UUID.randomUUID().toString(), role = ChatRole.ASSISTANT, isStreaming = true)
        _messages.value = _messages.value + user + assistant
        _state.value = ChatState.Generating(assistant.id)

        job = viewModelScope.launch {
            try {
                val cfg = settings.settings.first()
                val host = if (cfg.bindLan) "127.0.0.1" else "127.0.0.1"
                val body = buildJsonObject {
                    put("model", cfg.model)
                    put("stream", true)
                    putJsonArray("messages") {
                        _messages.value.filter { it.role != ChatRole.ASSISTANT || !it.isStreaming }
                            .forEach { m ->
                                add(buildJsonObject {
                                    put("role", m.role.name.lowercase())
                                    put("content", m.text)
                                })
                            }
                    }
                }.toString().toRequestBody("application/json".toMediaType())
                val req = Request.Builder()
                    .url("http://$host:${cfg.port}/v1/chat/completions")
                    .post(body)
                    .build()
                withContext(Dispatchers.IO) {
                    okHttp.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            val err = resp.body?.string().orEmpty()
                            appendError(assistant.id, "HTTP ${resp.code}: ${err.take(200)}")
                            return@withContext
                        }
                        BufferedReader(InputStreamReader(resp.body!!.byteStream())).useLines { lines ->
                            lines.forEach { line ->
                                if (line.startsWith("data: ")) {
                                    val payload = line.substring(6)
                                    if (payload == "[DONE]") return@forEach
                                    val obj = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return@forEach
                                    val delta = obj["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("delta")?.jsonObject
                                    val content = (delta?.get("content") as? JsonPrimitive)?.content
                                    val reasoning = (delta?.get("reasoning_content") as? JsonPrimitive)?.content
                                    if (!content.isNullOrEmpty() || !reasoning.isNullOrEmpty()) {
                                        viewModelScope.launch {
                                            _messages.value = _messages.value.map { m ->
                                                if (m.id == assistant.id) m.copy(
                                                    text = m.text + (content ?: ""),
                                                    reasoningText = m.reasoningText + (reasoning ?: ""),
                                                ) else m
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                _messages.value = _messages.value.map { if (it.id == assistant.id) it.copy(isStreaming = false) else it }
                _state.value = ChatState.Idle
            } catch (t: Throwable) {
                appendError(assistant.id, t.message ?: "error")
            }
        }
    }

    /** Cancels an in-flight generation. */
    fun stop() {
        job?.cancel()
        _messages.value = _messages.value.map { it.copy(isStreaming = false) }
        _state.value = ChatState.Idle
    }

    /** Clears the local transcript. */
    fun clear() { _messages.value = emptyList() }

    private fun appendError(id: String, message: String) {
        _messages.value = _messages.value.map {
            if (it.id == id) it.copy(text = it.text + "\n[error] $message", isStreaming = false) else it
        }
        _state.value = ChatState.Error(message)
    }
}
