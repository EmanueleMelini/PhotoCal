package it.emanuelemelini.photocal.data.ai.gemini

import android.util.Base64
import it.emanuelemelini.photocal.data.ai.AiClient
import it.emanuelemelini.photocal.data.ai.AiConfig
import it.emanuelemelini.photocal.data.ai.AiException
import it.emanuelemelini.photocal.data.ai.AiHttp
import it.emanuelemelini.photocal.data.ai.AiProvider
import it.emanuelemelini.photocal.data.ai.AiRequest
import it.emanuelemelini.photocal.data.ai.GEMINI_FOOD_ANALYSIS_SCHEMA
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Direct REST calls to Gemini's generateContent endpoint. */
class GeminiClient(private val http: AiHttp) : AiClient {

    private val json = AiHttp.json

    override suspend fun generate(config: AiConfig, request: AiRequest): String {
        val modelId = normalizeModel(config.model)
        val parts = buildList {
            request.jpeg?.let { add(Part(inlineData = InlineData("image/jpeg", Base64.encodeToString(it, Base64.NO_WRAP)))) }
            add(Part(text = request.userPrompt))
        }
        val payload = GenerateContentRequest(
            contents = listOf(Content(role = "user", parts = parts)),
            systemInstruction = Content(parts = listOf(Part(text = request.systemPrompt))),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                responseSchema = GEMINI_FOOD_ANALYSIS_SCHEMA,
                temperature = 0.2,
            ),
        )
        val httpRequest = Request.Builder()
            .url("$BASE_URL/models/$modelId:generateContent")
            .header("x-goog-api-key", config.apiKey)
            .post(json.encodeToString(payload).toRequestBody(AiHttp.JSON_MEDIA_TYPE))
            .build()
        return extractText(send(httpRequest, modelId))
    }

    /** Reads the model metadata: uses no generation quota. */
    override suspend fun testConnection(config: AiConfig): String {
        val modelId = normalizeModel(config.model)
        val request = Request.Builder()
            .url("$BASE_URL/models/$modelId")
            .header("x-goog-api-key", config.apiKey)
            .get()
            .build()
        val body = send(request, modelId)
        return runCatching {
            json.parseToJsonElement(body).jsonObject["displayName"]?.jsonPrimitive?.content
        }.getOrNull() ?: modelId
    }

    /** 429 (rate limit) and 503 (model overloaded) are retried. */
    private suspend fun send(request: Request, modelId: String): String =
        http.send(request, retryable = { code, _ -> code == 429 || code == 503 }) { code, body -> mapError(code, body, modelId) }

    private fun mapError(code: Int, body: String, modelId: String): AiException {
        val error = runCatching { json.decodeFromString<ErrorResponse>(body).error }.getOrNull()
        val message = error?.message.orEmpty()
        return when {
            code == 429 -> AiException.RateLimited()
            code == 400 && (body.contains("API_KEY_INVALID") || message.contains("API key", ignoreCase = true)) ->
                AiException.InvalidApiKey(AiProvider.GEMINI)
            code == 401 || code == 403 -> AiException.InvalidApiKey(AiProvider.GEMINI)
            code == 404 -> AiException.ModelNotFound(modelId)
            else -> AiException.Http(AiProvider.GEMINI, code, message)
        }
    }

    /** Response text, excluding any model "thoughts". */
    private fun extractText(body: String): String {
        val response = try {
            json.decodeFromString<GenerateContentResponse>(body)
        } catch (_: SerializationException) {
            return ""
        }
        response.promptFeedback?.blockReason?.let { throw AiException.Blocked(it) }
        val candidate = response.candidates.firstOrNull() ?: return ""
        if (candidate.finishReason in BLOCKING_FINISH_REASONS) {
            throw AiException.Blocked(candidate.finishReason.orEmpty())
        }
        return candidate.content?.parts
            .orEmpty()
            .filter { it.thought != true }
            .mapNotNull { it.text }
            .joinToString("")
    }

    private fun normalizeModel(model: String): String {
        val id = model.trim().removePrefix("models/").ifEmpty { AiProvider.GEMINI.defaultModel }
        if (!MODEL_ID_REGEX.matches(id)) throw AiException.ModelNotFound(id)
        return id
    }

    private companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        val MODEL_ID_REGEX = Regex("[A-Za-z0-9._-]+")
        val BLOCKING_FINISH_REASONS = setOf("SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII")
    }
}
