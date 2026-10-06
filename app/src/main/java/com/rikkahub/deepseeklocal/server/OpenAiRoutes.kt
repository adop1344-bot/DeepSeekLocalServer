package com.rikkahub.deepseeklocal.server

import com.rikkahub.deepseeklocal.data.remote.deepseek.DeepSeekClient
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import java.io.Writer
import java.util.UUID

/**
 * OpenAI-compatible Ktor routes bridged to the DeepSeek web backend.
 */
class OpenAiRoutes(
    private val client: DeepSeekClient,
    private val sessions: SessionManager,
    private val tokenProvider: suspend () -> String?,
    private val modelProvider: suspend () -> String,
    private val onTokenCaptured: suspend (String) -> Unit,
    private val onLog: (String, String) -> Unit = { _, _ -> },
) {

    private val json = Json { ignoreUnknownKeys = true }

    fun install(routing: Route) {
        routing.route("/") {
            get("health") {
                call.respondText(
                    buildJsonObject { put("ok", JsonPrimitive(true)) }.toString(),
                    ContentType.Application.Json,
                )
            }
            post("reset") {
                sessions.resetAll()
                call.respondText(
                    buildJsonObject { put("ok", JsonPrimitive(true)) }.toString(),
                    ContentType.Application.Json,
                )
            }
            get("auth") {
                val token = tokenProvider()
                call.respondText(
                    buildJsonObject {
                        put("ok", JsonPrimitive(token != null))
                        put("hasToken", JsonPrimitive(token != null))
                    }.toString(),
                    ContentType.Application.Json,
                )
            }
            post("auth") {
                val body = call.receiveText()
                val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
                val token = (obj?.get("token") as? JsonPrimitive)?.content
                if (token.isNullOrBlank()) {
                    call.respondText(
                        buildJsonObject {
                            put("ok", JsonPrimitive(false))
                            put("error", JsonPrimitive("missing token"))
                        }.toString(),
                        ContentType.Application.Json,
                        HttpStatusCode.BadRequest,
                    )
                    return@post
                }
                onTokenCaptured(token)
                val valid = client.validateToken(token)
                call.respondText(
                    buildJsonObject {
                        put("ok", JsonPrimitive(valid))
                        put("validMinutes", JsonPrimitive(if (valid) 55 else 0))
                    }.toString(),
                    ContentType.Application.Json,
                )
            }
            get("v1/models") { call.respondModels() }
            get("models") { call.respondModels() }
            post("v1/chat/completions") { handleChat(call) }
            post("chat/completions") { handleChat(call) }
        }
    }

    private suspend fun ApplicationCall.respondModels() {
        val data = buildJsonArray {
            for (id in listOf("deepseek-chat", "deepseek-reasoner", "deepseek_chat", "deepseek_reasoner")) {
                add(buildJsonObject {
                    put("id", JsonPrimitive(id))
                    put("object", JsonPrimitive("model"))
                    put("created", JsonPrimitive(0))
                    put("owned_by", JsonPrimitive("deepseek"))
                })
            }
        }
        respondText(
            buildJsonObject {
                put("object", JsonPrimitive("list"))
                put("data", data)
            }.toString(),
            ContentType.Application.Json,
        )
    }

    private suspend fun handleChat(call: ApplicationCall) {
        val body = call.receiveText()
        val payload = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
        if (payload == null) {
            call.respondText(errorJson("invalid JSON", "invalid_request_error").toString(),
                ContentType.Application.Json, HttpStatusCode.BadRequest)
            return
        }

        val rawModel = (payload["model"] as? JsonPrimitive)?.content ?: "deepseek-chat"
        val stream = (payload["stream"] as? JsonPrimitive)?.content == "true"
        val messages = payload["messages"] as? JsonArray
        val tools = payload["tools"] as? JsonArray
        val reasoningEffort = (payload["reasoning_effort"] as? JsonPrimitive)?.content

        var thinking = rawModel == "deepseek-reasoner" || rawModel == "deepseek_reasoner"
        val model = if (thinking) "deepseek_reasoner" else "deepseek_chat"
        if (!reasoningEffort.isNullOrEmpty() && reasoningEffort != "none") thinking = true

        if (messages == null || messages.isEmpty()) {
            call.respondText(errorJson("no messages", "invalid_request_error").toString(),
                ContentType.Application.Json, HttpStatusCode.BadRequest)
            return
        }

        val token = tokenProvider()
        if (token.isNullOrBlank()) {
            call.respondText(errorJson("no token configured", "invalid_request_error").toString(),
                ContentType.Application.Json, HttpStatusCode.BadRequest)
            return
        }

        val hash = SessionManager.hashConversation(messages.toString())
        val session = sessions.getOrCreate(hash) { client.createSession(token) }
        val prompt = PromptBuilder.messagesToPrompt(messages, tools)
        val id = "chatcmpl-" + UUID.randomUUID().toString().replace("-", "").take(12)
        val created = System.currentTimeMillis() / 1000
        val agent = AutoContinueAgent(client)
        onLog("INFO", "chat hash=$hash model=$model thinking=$thinking stream=$stream msgs=${messages.size}")

        if (!stream) {
            val result = runCatching {
                agent.run(token, prompt, session.sessionId, session.parentMessageId, thinking, model, {}, null)
            }.getOrElse { e ->
                call.respondText(errorJson(e.message ?: "internal error", "internal_error").toString(),
                    ContentType.Application.Json, HttpStatusCode.InternalServerError)
                return
            }
            session.parentMessageId = result.parentMessageId

            val call2 = result.toolCall
            val message = buildJsonObject {
                put("role", JsonPrimitive("assistant"))
                if (call2 != null) {
                    put("content", JsonNull)
                    put("tool_calls", buildJsonArray {
                        add(buildJsonObject {
                            put("id", JsonPrimitive("call_" + UUID.randomUUID().toString().take(8)))
                            put("type", JsonPrimitive("function"))
                            put("function", buildJsonObject {
                                put("name", JsonPrimitive(call2.tool))
                                val argsObj = buildJsonObject {
                                    call2.args.forEach { (k, v) -> put(k, JsonPrimitive(v)) }
                                }
                                put("arguments", JsonPrimitive(argsObj.toString()))
                            })
                        })
                    })
                } else {
                    val clean = ToolCallParser.strip(result.text).ifEmpty { result.text }
                    put("content", JsonPrimitive(clean))
                    if (result.think.isNotEmpty()) put("reasoning_content", JsonPrimitive(result.think))
                }
            }
            val finish = if (call2 != null) "tool_calls" else "stop"
            val response = buildJsonObject {
                put("id", JsonPrimitive(id))
                put("object", JsonPrimitive("chat.completion"))
                put("created", JsonPrimitive(created))
                put("model", JsonPrimitive(model))
                put("choices", buildJsonArray {
                    add(buildJsonObject {
                        put("index", JsonPrimitive(0))
                        put("message", message)
                        put("finish_reason", JsonPrimitive(finish))
                    })
                })
                put("usage", buildJsonObject {
                    put("prompt_tokens", JsonPrimitive(0))
                    put("completion_tokens", JsonPrimitive(0))
                    put("total_tokens", JsonPrimitive(0))
                })
            }
            call.respondText(response.toString(), ContentType.Application.Json)
            return
        }

        call.respondTextWriter(ContentType.parse("text/event-stream")) {
            val writer: Writer = this
            fun sse(obj: JsonObject) {
                writer.write("data: $obj\n\n")
                writer.flush()
            }

            sse(buildJsonObject {
                put("id", JsonPrimitive(id))
                put("object", JsonPrimitive("chat.completion.chunk"))
                put("created", JsonPrimitive(created))
                put("model", JsonPrimitive(model))
                put("choices", buildJsonArray {
                    add(buildJsonObject {
                        put("index", JsonPrimitive(0))
                        put("delta", buildJsonObject { put("role", JsonPrimitive("assistant")) })
                        put("finish_reason", JsonNull)
                    })
                })
            })

            val contentBuffer = StringBuilder()
            val reasoningBuffer = StringBuilder()
            var inThink = false

            fun flushContent() {
                if (contentBuffer.isEmpty()) return
                sse(buildJsonObject {
                    put("id", JsonPrimitive(id))
                    put("object", JsonPrimitive("chat.completion.chunk"))
                    put("created", JsonPrimitive(created))
                    put("model", JsonPrimitive(model))
                    put("choices", buildJsonArray {
                        add(buildJsonObject {
                            put("index", JsonPrimitive(0))
                            put("delta", buildJsonObject { put("content", JsonPrimitive(contentBuffer.toString())) })
                            put("finish_reason", JsonNull)
                        })
                    })
                })
                contentBuffer.clear()
            }

            fun flushReasoning() {
                if (reasoningBuffer.isEmpty()) return
                sse(buildJsonObject {
                    put("id", JsonPrimitive(id))
                    put("object", JsonPrimitive("chat.completion.chunk"))
                    put("created", JsonPrimitive(created))
                    put("model", JsonPrimitive(model))
                    put("choices", buildJsonArray {
                        add(buildJsonObject {
                            put("index", JsonPrimitive(0))
                            put("delta", buildJsonObject { put("reasoning_content", JsonPrimitive(reasoningBuffer.toString())) })
                            put("finish_reason", JsonNull)
                        })
                    })
                })
                reasoningBuffer.clear()
            }

            val result = runCatching {
                agent.run(
                    token = token,
                    prompt = prompt,
                    sessionId = session.sessionId,
                    parentMessageId = session.parentMessageId,
                    thinking = thinking,
                    model = model,
                    onDelta = { chunk ->
                        var s = chunk
                        while (s.isNotEmpty()) {
                            if (!inThink) {
                                val idx = s.indexOf("[thinking]")
                                if (idx == -1) {
                                    contentBuffer.append(s)
                                    s = ""
                                    if (contentBuffer.length >= 500) flushContent()
                                } else {
                                    if (idx > 0) contentBuffer.append(s.substring(0, idx))
                                    flushContent()
                                    s = s.substring(idx + "[thinking]".length)
                                    inThink = true
                                }
                            } else {
                                val endIdx = s.indexOf("[/thinking]")
                                if (endIdx == -1) {
                                    reasoningBuffer.append(s)
                                    s = ""
                                } else {
                                    reasoningBuffer.append(s.substring(0, endIdx))
                                    flushReasoning()
                                    s = s.substring(endIdx + "[/thinking]".length)
                                    inThink = false
                                }
                            }
                        }
                    },
                    onReasoning = { chunk ->
                        reasoningBuffer.append(chunk)
                        if (reasoningBuffer.length >= 500) flushReasoning()
                    },
                )
            }.getOrElse { e ->
                onLog("ERROR", "ask failed: ${e.message}")
                sse(buildJsonObject {
                    put("id", JsonPrimitive(id))
                    put("object", JsonPrimitive("chat.completion.chunk"))
                    put("created", JsonPrimitive(created))
                    put("model", JsonPrimitive(model))
                    put("choices", buildJsonArray {
                        add(buildJsonObject {
                            put("index", JsonPrimitive(0))
                            put("delta", buildJsonObject { })
                            put("finish_reason", JsonPrimitive("stop"))
                        })
                    })
                })
                writer.write("data: [DONE]\n\n")
                writer.flush()
                return@respondTextWriter
            }
            session.parentMessageId = result.parentMessageId

            flushReasoning()
            flushContent()

            sse(buildJsonObject {
                put("id", JsonPrimitive(id))
                put("object", JsonPrimitive("chat.completion.chunk"))
                put("created", JsonPrimitive(created))
                put("model", JsonPrimitive(model))
                put("choices", buildJsonArray {
                    add(buildJsonObject {
                        put("index", JsonPrimitive(0))
                        put("delta", buildJsonObject { })
                        put("finish_reason", JsonPrimitive(if (result.toolCall != null) "tool_calls" else "stop"))
                    })
                })
            })
            writer.write("data: [DONE]\n\n")
            writer.flush()
        }
    }

    private fun errorJson(message: String, type: String): JsonObject = buildJsonObject {
        put("error", buildJsonObject {
            put("message", JsonPrimitive(message))
            put("type", JsonPrimitive(type))
        })
    }
}
