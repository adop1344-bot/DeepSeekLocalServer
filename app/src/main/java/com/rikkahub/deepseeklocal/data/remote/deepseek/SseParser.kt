package com.rikkahub.deepseeklocal.data.remote.deepseek

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Accumulated result of one streamed completion. */
 data class StreamResult(
    val text: String,
    val think: String,
    val responseMessageId: String?,
    val finished: Boolean,
)

/**
 * DeepSeek web SSE parser. Ports the reference `stream.js` logic 1:1:
 * event-scoped patches, fragment snapshots, [thinking] fallback, status-word filtering.
 */
class SseParser(private val onDelta: (String) -> Unit, private val onReasoning: ((String) -> Unit)?) {

    private val json = Json { ignoreUnknownKeys = true }
    private val collected = StringBuilder()
    private val thinkText = StringBuilder()

    private var currentEvent = ""
    private var wrote = false
    private var inThink = false
    private val fragmentEmitted = HashMap<String, Int>()
    private val fragmentType = HashMap<String, String>()
    private var lastFragmentId: String? = null
    private var currentMessageId: String? = null
    private var responseMessageId: String? = null
    private var finished = false

    /** Feeds one physical SSE line into the parser. */
    fun processLine(line: String) {
        if (line.isEmpty()) {
            currentEvent = ""
            return
        }
        if (line.startsWith("event: ")) {
            currentEvent = line.substring(7).trim()
            return
        }
        if (!line.startsWith("data: ")) return

        val ev = currentEvent
        currentEvent = ""

        val payload = line.substring(6).trim()
        if (payload.isEmpty() || payload == "[DONE]") return

        val obj: JsonObject = try {
            json.parseToJsonElement(payload).jsonObject
        } catch (t: Throwable) {
            return
        }

        if (ev == "ready") {
            (obj["response_message_id"] as? JsonPrimitive)?.contentOrNull()?.let {
                responseMessageId = it
            }
            return
        }
        if (ev == "title" || ev == "update_session" || ev == "close" || ev == "error") return

        // Snapshot with fragments.
        val v = obj["v"]
        if (v is JsonObject && v["response"] != null) {
            val resp = v["response"]!!.jsonObject
            val mid = (resp["message_id"] as? JsonPrimitive)?.contentOrNull()
            if (mid != null && mid != currentMessageId) {
                currentMessageId = mid
                fragmentEmitted.clear()
                fragmentType.clear()
                lastFragmentId = null
            }
            if (mid != null) responseMessageId = mid
            handleFragments(resp["fragments"] as? JsonArray)
            return
        }

        val p = (obj["p"] as? JsonPrimitive)?.contentOrNull()
        val value = obj["v"]

        if (p == "response/fragments" && value is JsonArray) {
            handleFragments(value)
            return
        }

        if (p != null && value is JsonPrimitive && value.isString) {
            if (isMetaPath(p)) return
            val text = value.content
            if (isStatusWord(text)) return
            if (p.contains("/content")) {
                val fid = resolveFragmentId(p)
                if (fid != null) {
                    emitFragment(fid, text, fragmentType[fid] ?: "RESPONSE", isDelta = true)
                }
            }
            return
        }

        if (p != null && value is JsonArray) return

        // Simple chunk: {"v":"!"}
        if (value is JsonPrimitive && p == null) {
            val text = value.content
            if (isStatusWord(text)) return
            val fid = lastFragmentId
            if (fid != null) {
                emitFragment(fid, text, fragmentType[fid] ?: "RESPONSE", isDelta = true)
            } else {
                writeRaw(text)
            }
        }
    }

    private fun JsonPrimitive.contentOrNull(): String? = content.takeIf { it.isNotEmpty() && it != "null" }

    private fun handleFragments(arr: JsonArray?) {
        if (arr == null) return
        for (el in arr) {
            val f = el as? JsonObject ?: continue
            val content = (f["content"] as? JsonPrimitive)?.contentOrNull() ?: continue
            val id = (f["id"] as? JsonPrimitive)?.contentOrNull() ?: "?"
            val type = (f["type"] as? JsonPrimitive)?.contentOrNull() ?: "RESPONSE"
            emitFragment(id, content, type, isDelta = false)
        }
    }

    private fun emitFragment(id: String, text: String, type: String, isDelta: Boolean) {
        if (text.isEmpty()) return
        if (isStatusWord(text)) return
        fragmentType[id] = type
        lastFragmentId = id

        val delta: String
        if (isDelta) {
            fragmentEmitted[id] = (fragmentEmitted[id] ?: 0) + text.length
            delta = text
        } else {
            val prev = fragmentEmitted[id] ?: 0
            if (text.length <= prev) return
            delta = text.substring(prev)
            fragmentEmitted[id] = text.length
        }

        if (type == "THINK") {
            thinkText.append(delta)
            openThink()
            if (onReasoning != null) onReasoning.invoke(delta) else writeRaw(delta)
        } else {
            closeThink()
            writeRaw(delta)
        }
    }

    private fun writeRaw(s: String) {
        if (s.isEmpty()) return
        collected.append(s)
        onDelta(s)
        wrote = true
    }

    private fun openThink() {
        if (inThink) return
        if (onReasoning == null) writeRaw("\n[thinking] ")
        inThink = true
    }

    private fun closeThink() {
        if (!inThink) return
        if (onReasoning == null) writeRaw("\n[/thinking]\n\n")
        inThink = false
    }

    private fun resolveFragmentId(path: String): String? {
        val m = Regex("fragments/(-?\\d+)/content").find(path) ?: return null
        val raw = m.groupValues[1]
        return if (raw == "-1") lastFragmentId else raw
    }

    private fun isMetaPath(path: String): Boolean {
        if (path.contains("/elapsed_secs")) return true
        if (path == "response/status") return true
        if (path == "response") return true
        if (path.contains("/status")) return true
        if (path.contains("/accumulated_token_usage")) return true
        if (path.contains("/quasi_status")) return true
        return false
    }

    private fun isStatusWord(s: String): Boolean = s in STATUS_WORDS

    /** Marks the stream as finished (call when the reader completes). */
    fun finalize(): StreamResult {
        closeThink()
        return StreamResult(
            text = collected.toString(),
            think = thinkText.toString(),
            responseMessageId = responseMessageId,
            finished = finished,
        )
    }

    /** Marks the stream completion flag. */
    fun markFinished() { finished = true }

    /** True if any token was written. */
    fun wroteAnything(): Boolean = wrote

    companion object {
        /** Status words that must be filtered from the token stream. */
        val STATUS_WORDS = listOf("FINISHED", "WIP", "IN_PROGRESS", "PENDING")
    }
}
