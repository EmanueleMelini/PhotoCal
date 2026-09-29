package it.emanuelemelini.photocal.data.update

import android.util.Log
import it.emanuelemelini.photocal.BuildConfig
import it.emanuelemelini.photocal.data.http.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** A published release with its signed APK. */
data class Release(
    val version: AppVersion,
    /** Release page on GitHub, the fallback when the app can't install the APK itself. */
    val pageUrl: String,
    val apkUrl: String,
    /** Bytes, as reported by GitHub (0 when unknown). */
    val apkSize: Long,
    /** Release notes by language tag ("en", "it"), from the release body. */
    val notes: Map<String, List<String>> = emptyMap(),
) {
    /** Notes in the app language, in English when that language is missing. */
    fun notesFor(languageTag: String): List<String> = notes[languageTag] ?: notes[ENGLISH].orEmpty()

    internal companion object {
        const val ENGLISH = "en"
    }
}

/** The latest release can't be read (network, rate limit, unexpected answer). */
class UpdateCheckException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Reads the latest release from the public GitHub API, without a token: 60 requests per
 * hour per IP, far more than one check a day needs.
 */
class GitHubReleasesClient(private val httpClient: OkHttpClient) {

    /** null when there is no release with an APK yet. */
    suspend fun latestRelease(): Release? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", API_VERSION)
            // GitHub rejects requests without a User-Agent
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        val (code, body) = try {
            httpClient.newCall(request).await().use { it.code to it.body.string() }
        } catch (e: IOException) {
            Log.w(TAG, "Release check failed", e)
            throw UpdateCheckException("Network error", e)
        }
        when (code) {
            // No release published yet
            404 -> null
            in 200..299 -> parseRelease(body)
            else -> throw UpdateCheckException("GitHub error $code")
        }
    }

    companion object {
        private const val TAG = "GitHubReleases"
        private const val LATEST_RELEASE_URL = "https://api.github.com/repos/EmanueleMelini/PhotoCal/releases/latest"
        private const val API_VERSION = "2022-11-28"
        private const val USER_AGENT = "PhotoCal/${BuildConfig.VERSION_NAME} (personal Android app)"

        /** Body of GET /releases/latest; null when the tag isn't a version or there is no APK. */
        internal fun parseRelease(body: String): Release? {
            val root = try {
                Json.parseToJsonElement(body).jsonObject
            } catch (e: SerializationException) {
                throw UpdateCheckException("Unexpected answer", e)
            } catch (e: IllegalArgumentException) {
                throw UpdateCheckException("Unexpected answer", e)
            }
            val version = root.string("tag_name")?.let(AppVersion::parse) ?: return null
            val apk = (root["assets"] as? JsonArray).orEmpty()
                .mapNotNull { it as? JsonObject }
                .firstOrNull { it.string("name")?.endsWith(".apk", ignoreCase = true) == true }
                ?: return null
            val apkUrl = apk.string("browser_download_url")?.takeIf { it.startsWith("https://") } ?: return null
            return Release(
                version = version,
                pageUrl = root.string("html_url")?.takeIf { it.startsWith("https://") } ?: apkUrl,
                apkUrl = apkUrl,
                apkSize = (apk["size"] as? JsonPrimitive)?.longOrNull ?: 0,
                notes = root.string("body")?.let(::parseNotes).orEmpty(),
            )
        }

        /**
         * Bullet points of the body written by .github/scripts/release_notes.py: the visible
         * ones are English, the other languages are in hidden "<!-- photocal-notes:it ... -->"
         * blocks. A body typed by hand on GitHub works too, as English.
         */
        internal fun parseNotes(body: String): Map<String, List<String>> {
            val notes = mutableMapOf<String, List<String>>()
            HIDDEN_NOTES.findAll(body).forEach { match ->
                bullets(match.groupValues[2]).takeIf { it.isNotEmpty() }?.let { notes[match.groupValues[1]] = it }
            }
            bullets(body.replace(HTML_COMMENT, "")).takeIf { it.isNotEmpty() }?.let { notes[Release.ENGLISH] = it }
            return notes
        }

        private fun bullets(text: String): List<String> = text.lines()
            .map { it.trim() }
            .filter { it.startsWith("- ") || it.startsWith("* ") }
            .map { it.drop(2).trim() }
            .filter { it.isNotEmpty() }

        private val HIDDEN_NOTES = Regex("""<!--\s*photocal-notes:([a-z]{2,3})\s(.*?)-->""", RegexOption.DOT_MATCHES_ALL)
        private val HTML_COMMENT = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)

        private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    }
}
