package it.emanuelemelini.photocal.data.gemini

/**
 * Gemini errors. The message is technical (logs); the UI shows a translated text
 * chosen from the error type (see ui/ErrorMessages.kt).
 */
sealed class GeminiException(
    message: String,
    /** true if the user has to fix something in Settings. */
    val needsSettings: Boolean = false,
    cause: Throwable? = null,
) : Exception(message, cause) {

    class MissingApiKey : GeminiException("Missing Gemini API key", needsSettings = true)

    class InvalidApiKey : GeminiException("Invalid Gemini API key or missing permissions", needsSettings = true)

    class ModelNotFound(val model: String) : GeminiException("Model not found: $model", needsSettings = true)

    class RateLimited : GeminiException("Rate limit reached")

    class Network(cause: Throwable) : GeminiException("Network error", cause = cause)

    class InvalidResponse : GeminiException("Invalid JSON response")

    class Blocked(val reason: String) : GeminiException("Request blocked: $reason")

    class Http(val code: Int, val detail: String) : GeminiException("HTTP $code: $detail")
}
