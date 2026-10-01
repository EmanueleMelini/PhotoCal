package it.emanuelemelini.photocal.data.ai.anthropic

import android.util.Base64
import it.emanuelemelini.photocal.data.ai.AiClient
import it.emanuelemelini.photocal.data.ai.AiConfig
import it.emanuelemelini.photocal.data.ai.AiException
import it.emanuelemelini.photocal.data.ai.AiHttp
import it.emanuelemelini.photocal.data.ai.AiProvider
import it.emanuelemelini.photocal.data.ai.AiRequest
import it.emanuelemelini.photocal.data.ai.FOOD_ANALYSIS_JSON_SCHEMA
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Direct REST calls to Anthropic's Messages API (Claude models). */
class AnthropicClient(private val http: AiHttp) : AiClient {

    private val json = AiHttp.json

    override suspend fun generate(config: AiConfig, request: AiRequest): String {
        val model = modelId(config.model)
        val fallback = model in FALLBACK_MODELS
        val payload = buildJsonObject {
            put("model", model)
            put("max_tokens", MAX_TOKENS)
            put("system", request.systemPrompt)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        request.jpeg?.let { jpeg ->
                            addJsonObject {
                                put("type", "image")
                                putJsonObject("source") {
                                    put("type", "base64")
                                    put("media_type", "image/jpeg")
                                    put("data", Base64.encodeToString(jpeg, Base64.NO_WRAP))
                                }
                            }
                        }
                        addJsonObject {
                            put("type", "text")
                            put("text", request.userPrompt)
                        }
                    }
                }
            }
            putJsonObject("output_config") {
                putJsonObject("format") {
                    put("type", "json_schema")
                    put("schema", FOOD_ANALYSIS_JSON_SCHEMA)
                }
            }
            // A request declined by the safety classifiers is re-run on a suitable model
            if (fallback) put("fallbacks", "default")
        }
        val httpRequest = Request.Builder()
            .url("$BASE_URL/messages")
            .headers(config.apiKey)
            .apply { if (fallback) header("anthropic-beta", FALLBACK_BETA) }
            .post(payload.toRequestBody())
            .build()
        return extractText(send(httpRequest, model))
    }

    /** Reads the model metadata: uses no generation quota. */
    override suspend fun testConnection(config: AiConfig): String {
        val model = modelId(config.model)
        val request = Request.Builder()
            .url("$BASE_URL/models/$model")
            .headers(config.apiKey)
            .get()
            .build()
        val body = send(request, model)
        return runCatching { json.decodeFromString<ModelInfo>(body).displayName }.getOrNull() ?: model
    }

    private fun Request.Builder.headers(apiKey: String): Request.Builder =
        header("x-api-key", apiKey).header("anthropic-version", API_VERSION)

    private fun JsonObject.toRequestBody() = toString().toRequestBody(AiHttp.JSON_MEDIA_TYPE)

    /** 429 (rate limit), 500, 503 and 529 (overloaded) are retried. */
    private suspend fun send(request: Request, model: String): String =
        http.send(request, retryable = { code, _ -> code in RETRYABLE_CODES }) { code, body -> mapError(code, body, model) }

    private fun mapError(code: Int, body: String, model: String): AiException {
        val error = runCatching { json.decodeFromString<ErrorResponse>(body).error }.getOrNull()
        return when (code) {
            401, 403 -> AiException.InvalidApiKey(AiProvider.ANTHROPIC)
            404 -> AiException.ModelNotFound(model)
            429 -> AiException.RateLimited()
            else -> AiException.Http(AiProvider.ANTHROPIC, code, error?.message.orEmpty())
        }
    }

    private fun extractText(body: String): String {
        val response = try {
            json.decodeFromString<MessageResponse>(body)
        } catch (_: SerializationException) {
            return ""
        }
        if (response.stopReason == "refusal") {
            throw AiException.Blocked(response.stopDetails?.category ?: "refusal")
        }
        return response.content.filter { it.type == "text" }.mapNotNull { it.text }.joinToString("")
    }

    private fun modelId(model: String): String {
        val id = model.trim().ifEmpty { AiProvider.ANTHROPIC.defaultModel }
        if (!MODEL_ID_REGEX.matches(id)) throw AiException.ModelNotFound(id)
        return id
    }

    private companion object {
        const val BASE_URL = "https://api.anthropic.com/v1"
        const val API_VERSION = "2023-06-01"

        /** Room for adaptive thinking: the JSON itself is short. */
        const val MAX_TOKENS = 16_000
        const val FALLBACK_BETA = "server-side-fallback-2026-07-01"

        /** Models that accept fallbacks: "default" on the Claude API. */
        val FALLBACK_MODELS = setOf("claude-fable-5-1", "claude-opus-5-5", "claude-opus-5", "claude-sonnet-5-5")
        val RETRYABLE_CODES = setOf(429, 500, 503, 529)
        val MODEL_ID_REGEX = Regex("[A-Za-z0-9._-]+")
    }
}
