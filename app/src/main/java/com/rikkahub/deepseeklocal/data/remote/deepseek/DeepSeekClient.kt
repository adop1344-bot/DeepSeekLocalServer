package com.rikkahub.deepseeklocal.data.remote.deepseek

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Base64
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Low-level client for the DeepSeek web API at chat.deepseek.com/api/v0.
 * Mirrors the reference `chat.js` + `session.js` behavior.
 *
 * The web token is always supplied per-call; it is never stored here.
 */
class DeepSeekClient(
    private val okHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build(),
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val powSolver = PowSolver()

    /** Result of a streamed completion, including the new parent message id. */
    data class AskResult(
        val text: String,
        val think: String,
        val parentMessageId: String?,
        val finished: Boolean,
    )

    /** Creates a fresh chat session and returns its id. */
    suspend fun createSession(token: String): String = withContext(Dispatchers.IO) {
        val body = buildJsonObject { }.toString().toRequestBody(JSON_MEDIA)
        val req = Request.Builder()
            .url(BASE + "/chat_session/create")
            .headers(baseHeaders(token))
            .post(body)
            .build()
        okHttp.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("create session HTTP ${resp.code}: ${raw.take(300)}")
            val obj = json.parseToJsonElement(raw).jsonObject
            val data = obj["data"]?.jsonObject
            val biz = data?.get("biz_data")?.jsonObject
            val sid = (biz?.get("id") as? JsonPrimitive)?.content
                ?: (data?.get("chat_session")?.jsonObject?.get("id") as? JsonPrimitive)?.content
            sid ?: error("No session id in response: ${raw.take(500)}")
        }
    }

    /**
     * Sends a completion request and streams deltas through [onDelta]/[onReasoning].
     *
     * @return [AskResult] with the accumulated text and the new parent message id
     */
    suspend fun ask(
        token: String,
        prompt: String,
        sessionId: String,
        parentMessageId: String?,
        thinking: Boolean,
        model: String,
        onDelta: (String) -> Unit,
        onReasoning: ((String) -> Unit)?,
    ): AskResult = withContext(Dispatchers.IO) {
        val powHeader = fetchPowHeader(token)

        val reqBody = buildJsonObject {
            put("chat_session_id", JsonPrimitive(sessionId))
            if (parentMessageId != null) put("parent_message_id", JsonPrimitive(parentMessageId))
            else put("parent_message_id", kotlinx.serialization.json.JsonNull)
            put("model", JsonPrimitive(model))
            put("prompt", JsonPrimitive(prompt))
            put("ref_file_ids", buildJsonArray { })
            put("thinking_enabled", JsonPrimitive(thinking))
            put("search_enabled", JsonPrimitive(false))
        }.toString().toRequestBody(JSON_MEDIA)

        val builder = Request.Builder()
            .url(BASE + "/chat/completion")
            .headers(baseHeaders(token))
            .post(reqBody)
        if (powHeader != null) builder.header("x-ds-pow-response", powHeader)

        okHttp.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) {
                val t = resp.body?.string().orEmpty()
                error("HTTP ${resp.code}: ${t.take(300)}")
            }
            val ct = resp.header("Content-Type").orEmpty()
            if (ct.contains("application/json")) {
                val raw = resp.body?.string().orEmpty()
                error("JSON instead of stream: ${raw.take(300)}")
            }
            val parser = SseParser(onDelta, onReasoning)
            val source = resp.body?.source() ?: error("empty body")
            BufferedReader(InputStreamReader(source.inputStream(), Charsets.UTF_8)).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    parser.processLine(line)
                }
            }
            parser.markFinished()
            val res = parser.finalize()
            AskResult(
                text = res.text,
                think = res.think,
                parentMessageId = res.responseMessageId ?: parentMessageId,
                finished = res.finished,
            )
        }
    }

    /** Fetches and solves a PoW challenge, returning the base64 header value or null. */
    private fun fetchPowHeader(token: String): String? {
        return try {
            val body = buildJsonObject {
                put("target_path", JsonPrimitive("/api/v0/chat/completion"))
            }.toString().toRequestBody(JSON_MEDIA)
            val req = Request.Builder()
                .url(BASE + "/chat/create_pow_challenge")
                .headers(baseHeaders(token))
                .post(body)
                .build()
            okHttp.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val raw = resp.body?.string().orEmpty()
                val obj = json.parseToJsonElement(raw).jsonObject
                val challenge = obj["data"]?.jsonObject?.get("biz_data")?.jsonObject
                    ?.get("challenge")?.jsonPrimitive?.content ?: return null
                powSolver.solve(challenge)
            }
        } catch (t: Throwable) {
            null
        }
    }

    /** Validates a token by attempting to create a session. */
    suspend fun validateToken(token: String): Boolean = try {
        createSession(token)
        true
    } catch (t: Throwable) {
        false
    }

    private fun baseHeaders(token: String): okhttp3.Headers = okhttp3.Headers.Builder()
        .add("Content-Type", "application/json")
        .add("User-Agent", "Mozilla/5.0 (Linux; Android 13)")
        .add("Origin", "https://chat.deepseek.com")
        .add("Referer", "https://chat.deepseek.com/")
        .add("Authorization", "Bearer $token")
        .build()

    companion object {
        /** Base URL of the DeepSeek web API. */
        const val BASE = "https://chat.deepseek.com/api/v0"
        private val JSON_MEDIA = "application/json".toMediaType()
    }
}
