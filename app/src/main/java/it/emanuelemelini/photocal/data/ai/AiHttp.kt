package it.emanuelemelini.photocal.data.ai

import android.util.Log
import it.emanuelemelini.photocal.data.http.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** HTTP shared by the AI clients: long timeouts and retries on overload. */
class AiHttp(baseHttpClient: OkHttpClient) {

    private val httpClient = baseHttpClient.newBuilder()
        // Reasoning models can take tens of seconds for a photo
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .build()

    /**
     * Executes the request. When [retryable] says so (rate limit, overloaded model) retries
     * with exponential backoff: 1 s, 2 s, 4 s, at most 3 retries. Other errors go through
     * [mapError].
     */
    suspend fun send(
        request: Request,
        retryable: (code: Int, body: String) -> Boolean,
        mapError: (code: Int, body: String) -> AiException,
    ): String {
        var attempt = 0
        while (true) {
            val (code, body) = execute(request)
            if (code in 200..299) return body

            if (retryable(code, body) && attempt < MAX_RETRIES) {
                delay(1_000L shl attempt)
                attempt++
                continue
            }
            throw mapError(code, body)
        }
    }

    private suspend fun execute(request: Request): Pair<Int, String> = withContext(Dispatchers.IO) {
        try {
            httpClient.newCall(request).await().use { response -> response.code to response.body.string() }
        } catch (e: IOException) {
            Log.w(TAG, "AI request failed", e)
            throw AiException.Network(e)
        }
    }

    companion object {
        private const val TAG = "AiHttp"
        private const val MAX_RETRIES = 3
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
}
