package it.emanuelemelini.photocal.data.ai

import androidx.annotation.StringRes
import it.emanuelemelini.photocal.R

/** The constant names are stored in the preferences and in backups: don't rename them. */
enum class AiProvider(
    @StringRes val labelRes: Int,
    /** Prefix of the preference keys: "gemini" keeps the keys saved before 1.5.0. */
    val prefKey: String,
    /** Empty when the user always has to type the model. */
    val defaultModel: String,
    /** Suggestions shown in Settings. */
    val suggestedModels: List<String>,
    /** Page where the user creates a key; null when it depends on the service. */
    val keyUrl: String?,
    @StringRes val keyLinkRes: Int?,
) {
    /** Latest stable Flash model with a free tier (checked on ai.google.dev, October 2026). */
    GEMINI(
        R.string.ai_provider_gemini, "gemini", "gemini-3.8-flash",
        listOf("gemini-3.8-flash", "gemini-3.6-flash", "gemini-3.5-flash-lite"),
        "https://aistudio.google.com/apikey", R.string.settings_get_api_key_gemini,
    ),

    /** GPT-6 models with image input (checked on developers.openai.com, October 2026). */
    OPENAI(
        R.string.ai_provider_openai, "openai", "gpt-6-luna",
        listOf("gpt-6-luna", "gpt-6.1-sol"),
        "https://platform.openai.com/api-keys", R.string.settings_get_api_key_openai,
    ),

    /** Claude models with image input and structured outputs (October 2026). */
    ANTHROPIC(
        R.string.ai_provider_anthropic, "anthropic", "claude-sonnet-5-5",
        listOf("claude-sonnet-5-5", "claude-opus-5-5", "claude-haiku-4-5"),
        "https://platform.claude.com/settings/keys", R.string.settings_get_api_key_anthropic,
    ),

    /** Any service with the OpenAI Chat Completions API: OpenRouter, Mistral, Groq... */
    OPENAI_COMPATIBLE(
        R.string.ai_provider_compatible, "openai_compatible", "",
        emptyList(),
        null, null,
    );

    /** Some compatible services (e.g. a local proxy) need no key. */
    val requiresApiKey: Boolean get() = this != OPENAI_COMPATIBLE

    val needsBaseUrl: Boolean get() = this == OPENAI_COMPATIBLE
}

/** What a client needs to call its service. */
data class AiConfig(
    val apiKey: String,
    val model: String,
    /** Only for [AiProvider.OPENAI_COMPATIBLE]. */
    val baseUrl: String = "",
)

/** A request for a [FoodAnalysis]: [jpeg] (already resized) comes before [userPrompt]. */
class AiRequest(
    val systemPrompt: String,
    val userPrompt: String,
    val jpeg: ByteArray? = null,
)

/** REST client of one AI service. */
interface AiClient {
    /**
     * Text of the answer, constrained to the [FoodAnalysis] JSON when the service supports
     * structured outputs. Throws [AiException].
     */
    suspend fun generate(config: AiConfig, request: AiRequest): String

    /** Checks key and model without using generation quota. Returns the model's display name. */
    suspend fun testConnection(config: AiConfig): String
}
