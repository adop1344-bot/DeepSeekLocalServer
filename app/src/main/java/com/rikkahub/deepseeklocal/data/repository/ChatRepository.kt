package com.rikkahub.deepseeklocal.data.repository

import com.rikkahub.deepseeklocal.data.remote.deepseek.DeepSeekClient
import com.rikkahub.deepseeklocal.data.remote.deepseek.PromptBuilder as RemotePromptBuilder
import javax.inject.Inject
import javax.inject.Singleton

/** Handles token validation against the DeepSeek backend. */
@Singleton
class ChatRepository @Inject constructor(private val client: DeepSeekClient) {
    /** Returns true if the token can create a session. */
    suspend fun validateToken(token: String): Boolean = client.validateToken(token)
}
