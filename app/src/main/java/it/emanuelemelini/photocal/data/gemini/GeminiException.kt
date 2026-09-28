package it.emanuelemelini.photocal.data.gemini

/** Gemini errors with a message ready to show to the user. */
sealed class GeminiException(
    message: String,
    /** true if the user has to fix something in Settings. */
    val needsSettings: Boolean = false,
    cause: Throwable? = null,
) : Exception(message, cause) {

    class MissingApiKey : GeminiException(
        "API key Gemini mancante: inseriscila nelle Impostazioni.",
        needsSettings = true,
    )

    class InvalidApiKey : GeminiException(
        "API key non valida o senza permessi: controllala nelle Impostazioni.",
        needsSettings = true,
    )

    class ModelNotFound(model: String) : GeminiException(
        "Modello \"$model\" non disponibile: controlla il nome nelle Impostazioni.",
        needsSettings = true,
    )

    class RateLimited : GeminiException("Limite richieste raggiunto, riprova tra poco.")

    class Network(cause: Throwable) : GeminiException(
        "Nessuna connessione a Internet: controlla la rete e riprova.",
        cause = cause,
    )

    class InvalidResponse : GeminiException(
        "Risposta dell'AI non valida. Riprova oppure inserisci gli alimenti a mano.",
    )

    class Blocked(reason: String) : GeminiException("L'AI ha rifiutato la richiesta ($reason).")

    class Http(code: Int, detail: String) : GeminiException(
        "Errore del servizio Gemini ($code)" + if (detail.isNotBlank()) ": $detail" else ".",
    )
}
