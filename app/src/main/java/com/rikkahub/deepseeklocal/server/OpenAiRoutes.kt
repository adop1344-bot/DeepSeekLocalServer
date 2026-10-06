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
    private val onTokenAction: (String, Int, String?) -> Boolean = { _, _, _ -> false },
    private val onTokenFailed: () -> Unit = {},
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
            get("tokens") { call.respondTokensUi() }
            post("token") { call.handleTokenApi() }
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
                onTokenFailed()
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
    private suspend fun ApplicationCall.respondTokensUi() {
        respondText(TOKENS_UI_HTML, ContentType.Text.Html)
    }

    private suspend fun ApplicationCall.handleTokenApi() {
        val body = receiveText()
        val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
        if (obj == null) {
            respondText(buildJsonObject { put("ok", JsonPrimitive(false)); put("error", JsonPrimitive("bad json")) }.toString(), ContentType.Application.Json, HttpStatusCode.BadRequest)
            return
        }
        val action = (obj["action"] as? JsonPrimitive)?.content ?: ""
        val id = (obj["id"] as? JsonPrimitive)?.content?.toIntOrNull() ?: -1
        val token = (obj["token"] as? JsonPrimitive)?.content
        val ok = onTokenAction(action, id, token)
        respondText(buildJsonObject { put("ok", JsonPrimitive(ok)) }.toString(), ContentType.Application.Json)
    }

    companion object {
        private const val TOKENS_UI_HTML = """<!doctype html><html><head><meta charset="utf-8"><title>DeepSeek Tokens</title><meta name="viewport" content="width=device-width,initial-scale=1"><style>body{margin:0;padding:16px;font-family:system-ui,sans-serif;background:#0e0e10;color:#eee}h1{font-size:20px}textarea{width:100%;height:80px;background:#1c1c1f;color:#eee;border:0;border-radius:8px;padding:8px;font-family:monospace}button{padding:8px 12px;margin:4px 4px 4px 0;border:0;border-radius:8px;background:#2196f3;color:#fff;font-weight:600}.d{background:#5a2a2a;color:#f88}.c{background:#1c1c1f;border-radius:10px;padding:10px;margin:8px 0}.a{color:#4caf50}</style></head><body><h1>Токены DeepSeek</h1><div id="list"></div><h3>Добавить токен</h3><textarea id="t" placeholder="Bearer ..."></textarea><br><button onclick="add()">Добавить</button><script>function post(b){return fetch('/token',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(b)}).then(r=>r.json())}function render(l){document.getElementById('list').innerHTML=l.map(t=>'<div class="c">'+(t.active?'<span class="a">● </span>':'○ ')+t.name+' · len '+t.tokenLen+(t.failCount?' fail:'+t.failCount:'')+'<br><button onclick="sel('+t.id+')">Выбр</button><button class="d" onclick="del('+t.id+')">Удалить</button></div>').join('')}function load(){fetch('/tokens').then(r=>r.json()).then(j=>render(j.tokens||[]))}function add(){var t=document.getElementById('t').value.trim();if(!t)return;post({action:'add',token:t}).then(load)}function del(id){if(confirm('Удалить?'))post({action:'remove',id:id}).then(load)}function sel(id){post({action:'setActive',id:id}).then(load)}load()</script></body></html>"""
    }


}
