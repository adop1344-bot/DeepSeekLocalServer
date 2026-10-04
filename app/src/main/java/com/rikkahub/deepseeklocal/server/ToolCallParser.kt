package com.rikkahub.deepseeklocal.server

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** A parsed tool invocation requested by the model. */
data class ParsedToolCall(val tool: String, val args: Map<String, String>)

/**
 * Parses the `{"tool": ..., "args": {...}}` block out of model output.
 * Ports the reference `parseToolCall` / `stripToolBlock` / `normalizeToolCall` helpers.
 */
object ToolCallParser {

    private val json = Json { ignoreUnknownKeys = true }
    private val fencedJson = Regex("```json\\s*\\n([\\s\\S]*?)\\n```")
    private val fencedAny = Regex("```\\s*\\n([\\s\\S]*?)\\n```")
    private val looseObject = Regex("\\{[\\s\\S]*?\"tool\"\\s*:\\s*\"[^\"]+\"[\\s\\S]*?\\}")

    /** Extracts a tool call from raw text, or null if none is present. */
    fun parse(text: String): ParsedToolCall? {
        if (text.isBlank()) return null

        fencedJson.find(text)?.let { m ->
            normalize(m.groupValues[1].trim())?.let { return it }
        }
        fencedAny.find(text)?.let { m ->
            normalize(m.groupValues[1].trim())?.let { return it }
        }
        for (line in text.split('\n')) {
            val t = line.trim()
            if (t.startsWith("{\"tool\"") || t.startsWith("{ \"tool\"")) {
                normalize(t)?.let { return it }
            }
        }
        looseObject.find(text)?.let { m ->
            normalize(m.value)?.let { return it }
        }
        return null
    }

    /** Removes any fenced tool-call block from the visible answer text. */
    fun strip(text: String): String {
        var out = text.replace(fencedJson, "")
        out = out.replace(fencedAny, "")
        return out.trim()
    }

    private fun normalize(raw: String): ParsedToolCall? {
        return try {
            val obj = json.parseToJsonElement(raw).jsonObject
            val tool = (obj["tool"] as? JsonPrimitive)?.content ?: return null
            val argsNode = obj["args"]
            val args = LinkedHashMap<String, String>()
            if (argsNode is JsonObject && argsNode.isNotEmpty()) {
                argsNode.forEach { (k, v) -> args[k] = (v as? JsonPrimitive)?.content ?: v.toString() }
            } else {
                obj.forEach { (k, v) ->
                    if (k != "tool" && k != "name") {
                        args[k] = (v as? JsonPrimitive)?.content ?: v.toString()
                    }
                }
            }
            ParsedToolCall(tool, args)
        } catch (t: Throwable) {
            null
        }
    }
}
