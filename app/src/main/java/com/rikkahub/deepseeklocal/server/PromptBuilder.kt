package com.rikkahub.deepseeklocal.server

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Flattens OpenAI-style messages (including tools) into a single DeepSeek prompt.
 * Ports the reference `buildToolsPrompt` + `messagesToPrompt`.
 */
object PromptBuilder {

    /** Renders the tool catalogue section of the system prompt. */
    fun buildToolsPrompt(tools: JsonArray?): String {
        if (tools == null || tools.isEmpty()) return ""
        val lines = mutableListOf<String>()
        lines += "TOOLS: You have access to the following tools. To call one, emit EXACTLY:"
        lines += ""
        lines += "```json"
        lines += "{\"tool\": \"TOOL_NAME\", \"args\": {\"key\": \"value\"}}"
        lines += "```"
        lines += ""
        lines += "Rules:"
        lines += "- One tool call per message. Nothing else in that message."
        lines += "- After emitting the block, STOP. The runtime will send [TOOL OUTPUT]."
        lines += "- Do NOT repeat the same tool call twice."
        lines += "- Do NOT say \"tool does not exist\". All tools below are available."
        lines += ""
        lines += "Available tools:"
        for (el in tools) {
            val t = el as? JsonObject ?: continue
            val type = (t["type"] as? JsonPrimitive)?.content
            if (type != "function") continue
            val fn = t["function"] as? JsonObject ?: continue
            val name = (fn["name"] as? JsonPrimitive)?.content ?: "unknown"
            val desc = (fn["description"] as? JsonPrimitive)?.content ?: ""
            val params = fn["parameters"] as? JsonObject
            val props = params?.get("properties") as? JsonObject
            val paramStr = props?.keys?.joinToString(", ") { k ->
                val p = props[k] as? JsonObject
                val pt = (p?.get("type") as? JsonPrimitive)?.content
                if (pt != null) "$k:$pt" else k
            } ?: ""
            lines += "- $name ($paramStr) \u2014 ${desc.take(200)}"
        }
        lines += ""
        lines += "If no tool is needed, answer normally without a block."
        return lines.joinToString("\n")
    }

    /** Converts a full OpenAI messages array to one DeepSeek prompt string. */
    fun messagesToPrompt(messages: JsonArray?, tools: JsonArray?): String {
        if (messages == null || messages.isEmpty()) return ""
        val parts = mutableListOf<String>()
        val toolsText = buildToolsPrompt(tools)
        if (toolsText.isNotBlank()) parts += "[SYSTEM]\n$toolsText"
        for (el in messages) {
            val m = el as? JsonObject ?: continue
            val role = (m["role"] as? JsonPrimitive)?.content ?: "user"
            val content = contentToString(m["content"])
            when (role) {
                "system" -> if (content.isNotBlank()) parts += "[SYSTEM] $content"
                "tool" -> parts += "[TOOL OUTPUT]\n$content"
                "assistant" -> {
                    if (m["tool_calls"] is JsonArray) continue
                    if (content.isNotBlank()) parts += "[ASSISTANT] $content"
                }
                else -> if (content.isNotBlank()) parts += content
            }
        }
        return parts.joinToString("\n\n")
    }

    private fun contentToString(node: kotlinx.serialization.json.JsonElement?): String {
        if (node == null) return ""
        if (node is JsonPrimitive) return node.content
        if (node is JsonArray) {
            return node.joinToString("") { c ->
                when (c) {
                    is JsonPrimitive -> c.content
                    is JsonObject -> {
                        val type = (c["type"] as? JsonPrimitive)?.content
                        if (type == "image_url" || type == "image") ""
                        else (c["text"] as? JsonPrimitive)?.content
                            ?: (c["content"] as? JsonPrimitive)?.content ?: ""
                    }
                    else -> ""
                }
            }
        }
        return node.toString()
    }
}
