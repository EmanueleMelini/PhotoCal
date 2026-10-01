package it.emanuelemelini.photocal.ui

import it.emanuelemelini.photocal.R
import it.emanuelemelini.photocal.data.ai.AiException
import it.emanuelemelini.photocal.data.openfoodfacts.ProductLookupException

/** Translated message for the user for each AI error. */
fun AiException.toUiText(): UiText = when (this) {
    is AiException.MissingApiKey -> uiText(R.string.error_ai_missing_key, uiText(provider.labelRes))
    is AiException.InvalidApiKey -> uiText(R.string.error_ai_invalid_key, uiText(provider.labelRes))
    is AiException.MissingModel -> uiText(R.string.error_ai_missing_model)
    is AiException.InvalidBaseUrl -> uiText(R.string.error_ai_invalid_base_url)
    is AiException.ModelNotFound -> uiText(R.string.error_ai_model_not_found, model)
    is AiException.RateLimited -> uiText(R.string.error_ai_rate_limited)
    is AiException.Network -> uiText(R.string.error_network)
    is AiException.InvalidResponse -> uiText(R.string.error_ai_invalid_response)
    is AiException.Blocked -> uiText(R.string.error_ai_blocked, reason)
    is AiException.Http ->
        if (detail.isBlank()) uiText(R.string.error_ai_http, uiText(provider.labelRes), code)
        else uiText(R.string.error_ai_http_detail, uiText(provider.labelRes), code, detail)
}

/** Translated message for the user for each Open Food Facts error. */
fun ProductLookupException.toUiText(): UiText = when (this) {
    is ProductLookupException.NotFound -> uiText(R.string.error_off_not_found)
    is ProductLookupException.Network -> uiText(R.string.error_network)
    is ProductLookupException.Server -> uiText(R.string.error_off_server, code)
}
