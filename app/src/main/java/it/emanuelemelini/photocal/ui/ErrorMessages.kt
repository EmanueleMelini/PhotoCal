package it.emanuelemelini.photocal.ui

import it.emanuelemelini.photocal.R
import it.emanuelemelini.photocal.data.gemini.GeminiException
import it.emanuelemelini.photocal.data.openfoodfacts.ProductLookupException

/** Translated message for the user for each Gemini error. */
fun GeminiException.toUiText(): UiText = when (this) {
    is GeminiException.MissingApiKey -> uiText(R.string.error_gemini_missing_key)
    is GeminiException.InvalidApiKey -> uiText(R.string.error_gemini_invalid_key)
    is GeminiException.ModelNotFound -> uiText(R.string.error_gemini_model_not_found, model)
    is GeminiException.RateLimited -> uiText(R.string.error_gemini_rate_limited)
    is GeminiException.Network -> uiText(R.string.error_network)
    is GeminiException.InvalidResponse -> uiText(R.string.error_gemini_invalid_response)
    is GeminiException.Blocked -> uiText(R.string.error_gemini_blocked, reason)
    is GeminiException.Http ->
        if (detail.isBlank()) uiText(R.string.error_gemini_http, code)
        else uiText(R.string.error_gemini_http_detail, code, detail)
}

/** Translated message for the user for each Open Food Facts error. */
fun ProductLookupException.toUiText(): UiText = when (this) {
    is ProductLookupException.NotFound -> uiText(R.string.error_off_not_found)
    is ProductLookupException.Network -> uiText(R.string.error_network)
    is ProductLookupException.Server -> uiText(R.string.error_off_server, code)
}
