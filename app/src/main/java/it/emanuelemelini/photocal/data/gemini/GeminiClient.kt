package it.emanuelemelini.photocal.data.gemini

import android.util.Base64
import android.util.Log
import it.emanuelemelini.photocal.AppLocale
import it.emanuelemelini.photocal.data.http.await
import it.emanuelemelini.photocal.data.prefs.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Direct REST calls to Gemini's generateContent endpoint. */
class GeminiClient(
    private val settingsRepository: SettingsRepository,
    baseHttpClient: OkHttpClient,
) {
    private val httpClient = baseHttpClient.newBuilder()
        // Reasoning models can take tens of seconds for a photo
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    /**
     * Recognizes the foods in the photo. [jpeg] is already resized. With [creaCatalog]
     * the AI also returns the matching CREA code for each item.
     */
    suspend fun analyzePhoto(jpeg: ByteArray, userNotes: String, creaCatalog: String?): FoodAnalysis {
        val parts = listOf(
            Part(inlineData = InlineData("image/jpeg", Base64.encodeToString(jpeg, Base64.NO_WRAP))),
            Part(text = GeminiPrompts.photoUserPrompt(userNotes)),
        )
        return generateFoodAnalysis(GeminiPrompts.photoSystem(answerLanguage()), parts, creaCatalog)
    }

    /** Estimate from a text description, e.g. "2 fette di pane integrale". */
    suspend fun estimateFromText(description: String, quantity: String?, creaCatalog: String?): FoodAnalysis =
        generateFoodAnalysis(
            GeminiPrompts.textSystem(answerLanguage()),
            listOf(Part(text = GeminiPrompts.textUserPrompt(description, quantity))),
            creaCatalog,
        )

    /**
     * Checks API key and model by reading the model metadata: uses no generation quota.
     * Returns the model's display name.
     */
    suspend fun testConnection(apiKey: String, model: String): String {
        if (apiKey.isBlank()) throw GeminiException.MissingApiKey()
        val modelId = normalizeModel(model)
        val request = Request.Builder()
            .url("$BASE_URL/models/$modelId")
            .header("x-goog-api-key", apiKey)
            .get()
            .build()
        val body = send(request, modelId)
        return runCatching {
            json.parseToJsonElement(body).jsonObject["displayName"]?.jsonPrimitive?.content
        }.getOrNull() ?: modelId
    }

    /** Food names and notes come back in the language the UI is shown in. */
    private fun answerLanguage(): String = AppLocale.language.englishName

    private suspend fun generateFoodAnalysis(
        systemPrompt: String,
        parts: List<Part>,
        creaCatalog: String?,
    ): FoodAnalysis {
        val settings = settingsRepository.settings.first()
        val apiKey = settings.geminiApiKey.trim()
        if (apiKey.isEmpty()) throw GeminiException.MissingApiKey()
        val modelId = normalizeModel(settings.geminiModel)

        val payload = GenerateContentRequest(
            contents = listOf(Content(role = "user", parts = parts)),
            systemInstruction = Content(
                parts = listOf(Part(text = systemPrompt + creaCatalog?.let(GeminiPrompts::creaSection).orEmpty())),
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                responseSchema = FOOD_ANALYSIS_SCHEMA,
                temperature = 0.2,
            ),
        )
        val request = Request.Builder()
            .url("$BASE_URL/models/$modelId:generateContent")
            .header("x-goog-api-key", apiKey)
            .post(json.encodeToString(payload).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        // Invalid JSON: one more attempt, then error
        repeat(2) {
            val text = extractText(send(request, modelId))
            parseAnalysis(text)?.let { return it }
        }
        throw GeminiException.InvalidResponse()
    }

    /**
     * Executes the request. On 429 (rate limit) and 503 (model overloaded) retries with
     * exponential backoff: 1 s, 2 s, 4 s, at most 3 retries.
     */
    private suspend fun send(request: Request, modelId: String): String {
        var attempt = 0
        while (true) {
            val (code, body) = execute(request)
            if (code in 200..299) return body

            val retryable = code == 429 || code == 503
            if (retryable && attempt < MAX_RETRIES) {
                delay(1_000L shl attempt)
                attempt++
                continue
            }
            throw mapError(code, body, modelId)
        }
    }

    private suspend fun execute(request: Request): Pair<Int, String> = withContext(Dispatchers.IO) {
        try {
            httpClient.newCall(request).await().use { response -> response.code to response.body.string() }
        } catch (e: IOException) {
            Log.w(TAG, "Gemini request failed", e)
            throw GeminiException.Network(e)
        }
    }

    private fun mapError(code: Int, body: String, modelId: String): GeminiException {
        val error = runCatching { json.decodeFromString<ErrorResponse>(body).error }.getOrNull()
        val message = error?.message.orEmpty()
        return when {
            code == 429 -> GeminiException.RateLimited()
            code == 400 && (body.contains("API_KEY_INVALID") || message.contains("API key", ignoreCase = true)) ->
                GeminiException.InvalidApiKey()
            code == 401 || code == 403 -> GeminiException.InvalidApiKey()
            code == 404 -> GeminiException.ModelNotFound(modelId)
            else -> GeminiException.Http(code, message)
        }
    }

    /** Response text, excluding any model "thoughts". */
    private fun extractText(body: String): String {
        val response = try {
            json.decodeFromString<GenerateContentResponse>(body)
        } catch (_: SerializationException) {
            return ""
        }
        response.promptFeedback?.blockReason?.let { throw GeminiException.Blocked(it) }
        val candidate = response.candidates.firstOrNull() ?: return ""
        if (candidate.finishReason in BLOCKING_FINISH_REASONS) {
            throw GeminiException.Blocked(candidate.finishReason.orEmpty())
        }
        return candidate.content?.parts
            .orEmpty()
            .filter { it.thought != true }
            .mapNotNull { it.text }
            .joinToString("")
    }

    private fun parseAnalysis(text: String): FoodAnalysis? {
        // Strip a ```json ... ``` block, just in case
        val cleaned = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        if (cleaned.isEmpty()) return null
        return try {
            json.decodeFromString<FoodAnalysis>(cleaned)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun normalizeModel(model: String): String {
        val id = model.trim().removePrefix("models/").ifEmpty { SettingsRepository.DEFAULT_GEMINI_MODEL }
        if (!MODEL_ID_REGEX.matches(id)) throw GeminiException.ModelNotFound(id)
        return id
    }

    private companion object {
        const val TAG = "Gemini"
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        const val MAX_RETRIES = 3
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val MODEL_ID_REGEX = Regex("[A-Za-z0-9._-]+")
        val BLOCKING_FINISH_REASONS = setOf("SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII")
    }
}
