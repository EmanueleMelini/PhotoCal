package it.emanuelemelini.photocal.data.ai

import it.emanuelemelini.photocal.AppLocale
import it.emanuelemelini.photocal.data.ai.anthropic.AnthropicClient
import it.emanuelemelini.photocal.data.ai.gemini.GeminiClient
import it.emanuelemelini.photocal.data.ai.openai.OpenAiClient
import it.emanuelemelini.photocal.data.prefs.SettingsRepository
import kotlinx.coroutines.flow.first

/**
 * Meal analysis with the AI chosen in Settings: same prompts and same [FoodAnalysis] for
 * every service, only the REST call changes.
 */
class AiService(
    private val settingsRepository: SettingsRepository,
    http: AiHttp,
) {
    private val clients: Map<AiProvider, AiClient> = mapOf(
        AiProvider.GEMINI to GeminiClient(http),
        AiProvider.OPENAI to OpenAiClient(http, compatible = false),
        AiProvider.ANTHROPIC to AnthropicClient(http),
        AiProvider.OPENAI_COMPATIBLE to OpenAiClient(http, compatible = true),
    )

    /**
     * Recognizes the foods in the photo. [jpeg] is already resized. With [creaCatalog]
     * the AI also returns the matching CREA code for each item.
     */
    suspend fun analyzePhoto(jpeg: ByteArray, userNotes: String, creaCatalog: String?): FoodAnalysis =
        generate(AiPrompts.photoSystem(answerLanguage()), AiPrompts.photoUserPrompt(userNotes), jpeg, creaCatalog)

    /** Estimate from a text description, e.g. "2 fette di pane integrale". */
    suspend fun estimateFromText(description: String, quantity: String?, creaCatalog: String?): FoodAnalysis =
        generate(AiPrompts.textSystem(answerLanguage()), AiPrompts.textUserPrompt(description, quantity), null, creaCatalog)

    /** Tests the values typed in Settings, even if not saved yet. Returns the model's name. */
    suspend fun testConnection(provider: AiProvider, config: AiConfig): String =
        clients.getValue(provider).testConnection(config.checked(provider))

    /** Food names and notes come back in the language the UI is shown in. */
    private fun answerLanguage(): String = AppLocale.language.englishName

    private suspend fun generate(systemPrompt: String, userPrompt: String, jpeg: ByteArray?, creaCatalog: String?): FoodAnalysis {
        val settings = settingsRepository.settings.first()
        val provider = settings.aiProvider
        val config = settings.aiConfig(provider).checked(provider)
        val request = AiRequest(
            systemPrompt = systemPrompt + creaCatalog?.let(AiPrompts::creaSection).orEmpty(),
            userPrompt = userPrompt,
            jpeg = jpeg,
        )
        // Invalid JSON: one more attempt, then error
        repeat(2) {
            parseFoodAnalysis(clients.getValue(provider).generate(config, request))?.let { return it }
        }
        throw AiException.InvalidResponse()
    }

    private fun AiConfig.checked(provider: AiProvider): AiConfig {
        val key = apiKey.trim()
        if (provider.requiresApiKey && key.isEmpty()) throw AiException.MissingApiKey(provider)
        return copy(apiKey = key)
    }
}
