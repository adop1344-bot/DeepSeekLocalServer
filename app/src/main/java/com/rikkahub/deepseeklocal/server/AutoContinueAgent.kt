package com.rikkahub.deepseeklocal.server

import com.rikkahub.deepseeklocal.data.remote.deepseek.DeepSeekClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Combined output of a multi-iteration agent run. */
data class AgentResult(
    val text: String,
    val think: String,
    val toolCall: ParsedToolCall?,
    val parentMessageId: String?,
)

/**
 * Runs a prompt against DeepSeek, auto-continuing up to [maxContinue] times when the
 * answer is detected as cut. Ports the reference `runAgent` including overlap-stripping
 * and full-restart detection.
 *
 * @param client low-level DeepSeek HTTP client
 * @param maxContinue maximum number of continuation passes (reference: 4)
 */
class AutoContinueAgent(
    private val client: DeepSeekClient,
    private val maxContinue: Int = 4,
) {

    /**
     * Runs the full agent loop for one request.
     *
     * @param token DeepSeek web token
     * @param prompt flattened prompt
     * @param sessionId chat session id
     * @param parentMessageId parent message id (nullable for a new session)
     * @param thinking whether to enable the reasoner
     * @param model model id
     * @param onDelta content token callback
     * @param onReasoning reasoning token callback (nullable)
     * @param onRetry invoked with the iteration index when a cut is detected and a continue is about to fire
     */
    suspend fun run(
        token: String,
        prompt: String,
        sessionId: String,
        parentMessageId: String?,
        thinking: Boolean,
        model: String,
        onDelta: (String) -> Unit,
        onReasoning: ((String) -> Unit)?,
        onRetry: (Int) -> Unit = {},
    ): AgentResult = withContext(Dispatchers.IO) {
        var accumulated = ""
        var lastThink = ""
        var lastTool: ParsedToolCall? = null
        var parent = parentMessageId

        for (iter in 0 until maxContinue) {
            val firstIter = iter == 0
            val currentPrompt = if (firstIter) {
                prompt
            } else {
                val tail = accumulated.takeLast(150)
                "Продолжи СТРОГО с того места, где оборвалось. НЕ повторяй предыдущий текст. " +
                    "Твой предыдущий ответ закончился так: ...\"$tail\". Продолжи с этой точки."
            }

            val iterText = StringBuilder()
            val wrappedDelta: (String) -> Unit = { chunk ->
                iterText.append(chunk)
                onDelta(chunk)
            }
            val wrappedReasoning: ((String) -> Unit)? = if (onReasoning != null) {
                { chunk -> onReasoning(chunk) }
            } else null

            val result = client.ask(
                token = token,
                prompt = currentPrompt,
                sessionId = sessionId,
                parentMessageId = parent,
                thinking = thinking,
                model = model,
                onDelta = wrappedDelta,
                onReasoning = wrappedReasoning,
            )

            result.parentMessageId?.let { parent = it }
            if (result.think.isNotEmpty()) lastThink += result.think

            var piece = iterText.toString()

            if (!firstIter && accumulated.isNotEmpty() && piece.isNotEmpty()) {
                // Case 1: piece starts with the tail of accumulated (model rewrote the boundary).
                val maxCheck = minOf(accumulated.length, piece.length, 300)
                var stripped = false
                var i = maxCheck
                while (i > 20) {
                    if (accumulated.endsWith(piece.substring(0, i))) {
                        piece = piece.substring(i)
                        stripped = true
                        break
                    }
                    i--
                }
                // Case 2: full restart — model began from the top again.
                if (!stripped) {
                    val headLen = minOf(100, accumulated.length)
                    val pieceHeadLen = minOf(100, piece.length)
                    if (headLen == pieceHeadLen && piece.length > 200 &&
                        accumulated.substring(0, headLen) == piece.substring(0, pieceHeadLen)
                    ) {
                        return@withContext AgentResult(accumulated, lastThink, lastTool, parent)
                    }
                }
            }

            accumulated += piece

            if (!firstIter && iterText.toString().trim().isEmpty()) break

            val call = ToolCallParser.parse(accumulated)
            if (call != null) {
                lastTool = call
                break
            }

            if (!CutHeuristics.isCut(accumulated)) break
            if (iter == maxContinue - 1) break
            onRetry(iter)
        }

        AgentResult(
            text = accumulated,
            think = lastThink,
            toolCall = lastTool,
            parentMessageId = parent,
        )
    }
}
