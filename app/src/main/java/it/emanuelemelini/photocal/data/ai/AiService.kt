package it.emanuelemelini.photocal.data.ai

import it.emanuelemelini.photocal.AppLocale
import it.emanuelemelini.photocal.data.ai.anthropic.AnthropicClient
import it.emanuelemelini.photocal.data.ai.gemini.GeminiClient
import it.emanuelemelini.photocal.data.ai.openai.OpenAiClient
import it.emanuelemelini.photocal.data.nutrition.Bottle
import it.emanuelemelini.photocal.data.prefs.SettingsRepository
import it.emanuelemelini.photocal.data.telemetry.Telemetry
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

/**
 * Meal analysis (and water bottle recognition) with the AI chosen in Settings: same prompts
 * and same [FoodAnalysis] for every service, only the REST call changes.
 */
class AiService(
    private val settingsRepository: SettingsRepository,
    http: AiHttp,
    private val telemetry: Telemetry,
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

    /**
     * Foods of a day described in [text] (typed or dictated), each with its meal when
     * [splitMeals]; otherwise all of one meal, chosen by the user.
     */
    suspend fun describeDay(text: String, splitMeals: Boolean, creaCatalog: String?): FoodAnalysis =
        generate(AiPrompts.daySystem(answerLanguage(), splitMeals), AiPrompts.dayUserPrompt(text), null, creaCatalog)

    /**
     * Name and capacity of the water bottle in the photo; null when the AI sees none or the
     * capacity isn't a bottle's. [jpeg] is already resized.
     */
    suspend fun recognizeBottle(jpeg: ByteArray): Bottle? {
        val item = generate(AiPrompts.bottleSystem(answerLanguage()), AiPrompts.BOTTLE_USER_PROMPT, jpeg, null)
            .items.firstOrNull() ?: return null
        val ml = item.grams.roundToInt().takeIf { it in Bottle.ML_RANGE } ?: return null
        return Bottle(item.name.trim(), ml)
    }

    /** Tests the values typed in Settings, even if not saved yet. Returns the model's name. */
    suspend fun testConnection(provider: AiProvider, config: AiConfig): String =
        clients.getValue(provider).testConnection(config.checked(provider))

    /** Food names and notes come back in the language the UI is shown in. */
    private fun answerLanguage(): String = AppLocale.language.englishName

    private suspend fun generate(systemPrompt: String, userPrompt: String, jpeg: ByteArray?, creaCatalog: String?): FoodAnalysis {
        val settings = settingsRepository.settings.first()
        val provider = settings.aiProvider
        val request = AiRequest(
            systemPrompt = systemPrompt + creaCatalog?.let(AiPrompts::creaSection).orEmpty(),
            userPrompt = userPrompt,
            jpeg = jpeg,
        )
        // Errors are counted too (by type only), to see which AI fails and why
        val result = try {
            analyze(provider, settings.aiConfig(provider).checked(provider), request)
        } catch (e: AiException) {
            telemetry.logAiRequest(provider, photo = jpeg != null, error = e)
            throw e
        }
        telemetry.logAiRequest(provider, photo = jpeg != null, error = null)
        return result
    }

    private suspend fun analyze(provider: AiProvider, config: AiConfig, request: AiRequest): FoodAnalysis {
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
