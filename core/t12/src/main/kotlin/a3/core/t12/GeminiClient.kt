// SPDX-License-Identifier: Apache-2.0
// Part of the A3 universe. See LICENSE.
package a3.core.t12

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

data class GeminiCall(
    val model: String,
    val modelVersion: String,
    val promptTokens: Int,
    val candidatesTokens: Int,
    val totalTokens: Int,
    val latencyMs: Long,
    val attempts: Int,
    val text: String
)

object GeminiClient {
    private val mapper = ObjectMapper()
    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .build()

    fun apiKey(): String {
        val key = System.getenv(T12_API_ENV)?.trim().orEmpty()
        if (key.isEmpty()) throw T12Reject("$T12_API_ENV is not set")
        return key
    }

    fun generate(system: String, user: String, schemaJson: String): GeminiCall {
        val key = apiKey()
        val body = requestBody(system, user, schemaJson)
        var lastNetwork: T12NetworkFault? = null
        for (attempt in 0..2) {
            val started = System.nanoTime()
            try {
                val call = once(key, body, attempt + 1, started)
                return call
            } catch (e: T12NetworkFault) {
                lastNetwork = e
                if (attempt == 2) {
                    throw T12Reject("network failed after 2 retries: ${redact(e.message, key)}")
                }
                Thread.sleep(400L * (attempt + 1))
            } catch (e: T12Reject) {
                throw T12Reject(redact(e.message, key) ?: "Gemini rejected")
            }
        }
        throw T12Reject("network failed after 2 retries: ${redact(lastNetwork?.message, key)}")
    }

    private fun once(key: String, body: String, attempt: Int, started: Long): GeminiCall {
        val uri = URI.create(
            "https://generativelanguage.googleapis.com/v1beta/models/$T12_MODEL:generateContent"
        )
        val request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(60))
            .header("Content-Type", "application/json")
            .header("x-goog-api-key", key)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = try {
            http.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: Exception) {
            throw T12NetworkFault(e.javaClass.simpleName)
        }
        val latencyMs = (System.nanoTime() - started) / 1_000_000L
        val raw = redact(response.body(), key) ?: ""
        when (response.statusCode()) {
            429, 500, 502, 503, 504 -> throw T12NetworkFault("http ${response.statusCode()}")
            in 200..299 -> Unit
            else -> throw T12Reject("Gemini http ${response.statusCode()} ${raw.take(240)}")
        }
        val root = mapper.readTree(raw)
        val text = extractText(root)
        val usage = root.path("usageMetadata")
        val version = root.path("modelVersion").asText("").ifBlank {
            root.path("model").asText(T12_MODEL)
        }
        return GeminiCall(
            model = T12_MODEL,
            modelVersion = version,
            promptTokens = usage.path("promptTokenCount").asInt(0),
            candidatesTokens = usage.path("candidatesTokenCount").asInt(0),
            totalTokens = usage.path("totalTokenCount").asInt(0),
            latencyMs = latencyMs,
            attempts = attempt,
            text = text
        )
    }

    private fun extractText(root: JsonNode): String {
        val candidates = root.path("candidates")
        if (!candidates.isArray || candidates.size() == 0) {
            throw T12Reject("Gemini returned no candidates")
        }
        val finish = candidates[0].path("finishReason").asText("")
        if (finish.isNotBlank() && finish != "STOP") {
            throw T12Reject("Gemini finishReason=$finish")
        }
        val text = candidates[0].path("content").path("parts").path(0).path("text").asText("")
        if (text.isBlank()) throw T12Reject("Gemini returned empty text")
        return text
    }

    private fun requestBody(system: String, user: String, schemaJson: String): String {
        val schema = mapper.readTree(schemaJson)
        val root = mapper.createObjectNode()
        val sys = root.putObject("systemInstruction")
        sys.putArray("parts").addObject().put("text", system)
        val contents = root.putArray("contents")
        contents.addObject().putArray("parts").addObject().put("text", user)
        val gen = root.putObject("generationConfig")
        gen.put("temperature", 0)
        gen.put("responseMimeType", "application/json")
        gen.set<JsonNode>("responseSchema", schema)
        return mapper.writeValueAsString(root)
    }

    fun redact(text: String?, key: String): String? {
        if (text == null) return null
        if (key.isEmpty()) return text
        return text.replace(key, "***")
    }
}
