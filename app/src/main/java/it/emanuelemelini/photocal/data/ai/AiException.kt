package it.emanuelemelini.photocal.data.ai

/**
 * AI service errors. The message is technical (logs); the UI shows a translated text
 * chosen from the error type (see ui/ErrorMessages.kt).
 */
sealed class AiException(
    message: String,
    /** true if the user has to fix something in Settings. */
    val needsSettings: Boolean = false,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /** The service answered with an error or no usable answer: another model may do better. */
    val anotherModelMayHelp: Boolean get() = !needsSettings && this !is Network

    class MissingApiKey(val provider: AiProvider) : AiException("Missing ${provider.name} API key", needsSettings = true)

    class InvalidApiKey(val provider: AiProvider) :
        AiException("Invalid ${provider.name} API key or missing permissions", needsSettings = true)

    /** Only the OpenAI-compatible service has no default model. */
    class MissingModel : AiException("Missing model name", needsSettings = true)

    /** Base URL of the OpenAI-compatible service missing or not https. */
    class InvalidBaseUrl : AiException("Missing or invalid base URL", needsSettings = true)

    class ModelNotFound(val model: String) : AiException("Model not found: $model", needsSettings = true)

    class RateLimited : AiException("Rate limit reached")

    class Network(cause: Throwable) : AiException("Network error", cause = cause)

    class InvalidResponse : AiException("Invalid JSON response")

    class Blocked(val reason: String) : AiException("Request blocked: $reason")

    class Http(val provider: AiProvider, val code: Int, val detail: String) :
        AiException("${provider.name} HTTP $code: $detail")
}
