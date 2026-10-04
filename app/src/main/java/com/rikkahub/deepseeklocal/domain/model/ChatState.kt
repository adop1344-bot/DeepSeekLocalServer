package com.rikkahub.deepseeklocal.domain.model

/** High-level state of the in-app chat screen. */
sealed interface ChatState {
    data object Idle : ChatState
    data class Generating(val assistantId: String) : ChatState
    data class Error(val message: String) : ChatState
}
