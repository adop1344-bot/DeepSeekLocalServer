package com.rikkahub.deepseeklocal.domain.model

import kotlinx.serialization.Serializable

/** Role in the OpenAI-compatible chat protocol. */
@Serializable
enum class ChatRole { SYSTEM, USER, ASSISTANT, TOOL }

/** A tool call requested by the assistant. */
@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String,
)

/**
 * A single chat message shown in the built-in test chat.
 *
 * @property reasoningText chain-of-thought captured from the reasoner model
 * @property toolCalls tools the model asked to invoke
 * @property isStreaming true while tokens are still arriving
 */
data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val text: String = "",
    val reasoningText: String = "",
    val toolCalls: List<ToolCall> = emptyList(),
    val isStreaming: Boolean = false,
    val thinkingSeconds: Int? = null,
    val timestamp: Long = System.currentTimeMillis(),
)
