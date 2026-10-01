package it.emanuelemelini.photocal.data.ai.openai

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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Chat Completions API: OpenAI itself or, with [compatible], any service that implements it
 * at the base URL chosen by the user.
 */
class OpenAiClient(private val http: AiHttp, private val compatible: Boolean) : AiClient {

    private val provider = if (compatible) AiProvider.OPENAI_COMPATIBLE else AiProvider.OPENAI
    private val json = AiHttp.json

    override suspend fun generate(config: AiConfig, request: AiRequest): String {
        val baseUrl = baseUrl(config)
        val model = modelId(config.model)
        val body = try {
            send(chatRequest(baseUrl, config.apiKey, chatPayload(model, request, structured = true)), model)
        } catch (e: AiException.Http) {
            // Some compatible services don't know json_schema: plain JSON mode, schema in the prompt
            if (!compatible || !e.isUnsupportedResponseFormat()) throw e
            send(chatRequest(baseUrl, config.apiKey, chatPayload(model, request, structured = false)), model)
        }
        return extractText(body)
    }

    /**
     * OpenAI: reads the model metadata. Compatible services: looks for the model in the list,
     * since their ids can contain slashes.
     */
    override suspend fun testConnection(config: AiConfig): String {
        val baseUrl = baseUrl(config)
        val model = modelId(config.model)
        if (!compatible) {
            send(getRequest("$baseUrl/models/$model", config.apiKey), model)
            return model
        }
        val body = send(getRequest("$baseUrl/models", config.apiKey), model)
        val models = try {
            json.decodeFromString<ModelList>(body).data
        } catch (_: SerializationException) {
            throw AiException.InvalidBaseUrl()
        }
        if (models.none { it.id == model }) throw AiException.ModelNotFound(model)
        return model
    }

    private fun chatPayload(model: String, request: AiRequest, structured: Boolean): JsonObject = buildJsonObject {
        put("model", model)
        putJsonArray("messages") {
            addJsonObject {
                put("role", "system")
                put("content", if (structured) request.systemPrompt else request.systemPrompt + jsonModeInstruction())
            }
            addJsonObject {
                put("role", "user")
                putJsonArray("content") {
                    request.jpeg?.let { jpeg ->
                        addJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") {
                                put("url", "data:image/jpeg;base64," + Base64.encodeToString(jpeg, Base64.NO_WRAP))
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
        putJsonObject("response_format") {
            if (structured) {
                put("type", "json_schema")
                putJsonObject("json_schema") {
                    put("name", "food_analysis")
                    put("strict", true)
                    put("schema", FOOD_ANALYSIS_JSON_SCHEMA)
                }
            } else {
                put("type", "json_object")
            }
        }
    }

    private fun jsonModeInstruction(): String =
        "\n\nReply with a single JSON object that follows this JSON Schema:\n$FOOD_ANALYSIS_JSON_SCHEMA"

    private fun chatRequest(baseUrl: String, apiKey: String, payload: JsonObject): Request =
        Request.Builder()
            .url("$baseUrl/chat/completions")
            .authorization(apiKey)
            .post(payload.toString().toRequestBody(AiHttp.JSON_MEDIA_TYPE))
            .build()

    private fun getRequest(url: String, apiKey: String): Request =
        Request.Builder().url(url).authorization(apiKey).get().build()

    /** Compatible services without authentication get no header. */
    private fun Request.Builder.authorization(apiKey: String): Request.Builder =
        if (apiKey.isBlank()) this else header("Authorization", "Bearer $apiKey")

    /** Rate limits and server errors are retried; an exhausted quota isn't. */
    private suspend fun send(request: Request, model: String): String =
        http.send(
            request,
            retryable = { code, body -> (code == 429 && !isQuotaExhausted(body)) || code in RETRYABLE_SERVER_CODES },
        ) { code, body -> mapError(code, body, model) }

    private fun mapError(code: Int, body: String, model: String): AiException {
        val error = runCatching { json.decodeFromString<ErrorResponse>(body).error }.getOrNull()
        val message = error?.message.orEmpty()
        return when {
            code == 401 || code == 403 -> AiException.InvalidApiKey(provider)
            code == 429 && !isQuotaExhausted(body) -> AiException.RateLimited()
            // On a compatible service a 404 without a model error is usually a wrong base URL
            code == 404 && (!compatible || message.contains("model", ignoreCase = true)) -> AiException.ModelNotFound(model)
            code == 404 -> AiException.InvalidBaseUrl()
            else -> AiException.Http(provider, code, message)
        }
    }

    private fun isQuotaExhausted(body: String): Boolean = body.contains("insufficient_quota")

    private fun AiException.Http.isUnsupportedResponseFormat(): Boolean =
        code in 400..422 && UNSUPPORTED_FORMAT_HINTS.any { detail.contains(it, ignoreCase = true) }

    private fun extractText(body: String): String {
        val choice = try {
            json.decodeFromString<ChatCompletion>(body).choices.firstOrNull()
        } catch (_: SerializationException) {
            return ""
        } ?: return ""
        choice.message?.refusal?.takeIf { it.isNotBlank() }?.let { throw AiException.Blocked(it.take(MAX_REASON)) }
        if (choice.finishReason == "content_filter") throw AiException.Blocked("content_filter")
        return choice.message?.content.orEmpty()
    }

    private fun baseUrl(config: AiConfig): String {
        if (!compatible) return OPENAI_BASE_URL
        val url = normalizeBaseUrl(config.baseUrl)
        if (url.isEmpty()) throw AiException.InvalidBaseUrl()
        return url
    }

    private fun modelId(model: String): String {
        val id = model.trim().ifEmpty { provider.defaultModel }
        if (id.isEmpty()) throw AiException.MissingModel()
        val valid = if (compatible) id.none { it.isWhitespace() } else OPENAI_MODEL_REGEX.matches(id)
        if (!valid) throw AiException.ModelNotFound(id)
        return id
    }

    companion object {
        private const val OPENAI_BASE_URL = "https://api.openai.com/v1"
        private const val MAX_REASON = 200
        private val OPENAI_MODEL_REGEX = Regex("[A-Za-z0-9._:-]+")
        private val RETRYABLE_SERVER_CODES = setOf(500, 502, 503)
        private val UNSUPPORTED_FORMAT_HINTS = listOf("response_format", "json_schema")

        /**
         * Base URL of a compatible service without the trailing slash or a pasted
         * "/chat/completions"; empty if it isn't a valid https URL.
         */
        fun normalizeBaseUrl(value: String): String {
            val url = value.trim().removeSuffix("/").removeSuffix("/chat/completions").removeSuffix("/")
            val parsed = url.toHttpUrlOrNull() ?: return ""
            return if (parsed.isHttps) url else ""
        }
    }
}
