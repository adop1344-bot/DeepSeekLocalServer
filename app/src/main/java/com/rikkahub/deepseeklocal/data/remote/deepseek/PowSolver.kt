package com.rikkahub.deepseeklocal.data.remote.deepseek

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64
import org.bouncycastle.crypto.digests.SHA3Digest

/**
 * Solver for the DeepSeekHashV1 proof-of-work challenge.
 *
 * Algorithm: find a nonce such that `SHA3-256(salt + "_" + expireAt + "_" + nonce)`
 * has at least `difficulty` leading zero hex characters (4 by default).
 *
 * The returned JSON is exactly what DeepSeek expects in the `x-ds-pow-response`
 * header (base64-encoded).
 */
class PowSolver {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Solve a raw PoW challenge string (the JSON returned by create_pow_challenge).
     *
     * @param challengeJson the `challenge` field from the create_pow_challenge response
     * @return base64-encoded JSON to send in the x-ds-pow-response header, or null on failure
     */
    fun solve(challengeJson: String): String? {
        return try {
            val root = json.parseToJsonElement(challengeJson).jsonObject
            val algorithm = root["algorithm"]?.jsonPrimitive?.content ?: "DeepSeekHashV1"
            val challenge = root["challenge"]?.jsonPrimitive?.content ?: return null
            val salt = root["salt"]?.jsonPrimitive?.content ?: ""
            val expireAt = root["expire_at"]?.jsonPrimitive?.content?.toLongOrNull()
                ?: root["expire_at"]?.jsonPrimitive?.content?.toDoubleOrNull()?.toLong()
                ?: return null
            val difficulty = root["difficulty"]?.jsonPrimitive?.content?.toIntOrNull()
                ?: 4

            val answer = computeAnswer(salt, expireAt, challenge, difficulty)

            val payload = buildJsonObject {
                put("algorithm", JsonPrimitive(algorithm))
                put("challenge", JsonPrimitive(challenge))
                put("salt", JsonPrimitive(salt))
                put("answer", JsonPrimitive(answer))
                put("signature", JsonPrimitive(computeSignature(salt, expireAt, answer)))
                put("target_path", JsonPrimitive("/api/v0/chat/completion"))
            }
            Base64.getEncoder().encodeToString(payload.toString().toByteArray())
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * Brute-force a nonce whose digest carries at least [difficulty] zero leading
     * hex chars. Returns the winning nonce as a string.
     */
    private fun computeAnswer(salt: String, expireAt: Long, challenge: String, difficulty: Int): Long {
        val prefix = "$salt" + "_" + expireAt + "_"
        var nonce = 0L
        while (true) {
            val digest = sha3Hex(prefix + nonce)
            if (digest.take(difficulty).all { it == '0' }) {
                return nonce
            }
            nonce++
        }
    }

    /** Computes a stable signature = SHA3-256(salt + "_" + expireAt + "_" + answer). */
    private fun computeSignature(salt: String, expireAt: Long, answer: Long): String =
        sha3Hex(salt + "_" + expireAt + "_" + answer)

    /** SHA3-256 of the UTF-8 input, hex-encoded. */
    private fun sha3Hex(input: String): String {
        val digest = SHA3Digest(256)
        val bytes = input.toByteArray(Charsets.UTF_8)
        digest.update(bytes, 0, bytes.size)
        val out = ByteArray(digest.digestSize)
        digest.doFinal(out, 0)
        return out.joinToString("") { b -> "%02x".format(b) }
    }
}
