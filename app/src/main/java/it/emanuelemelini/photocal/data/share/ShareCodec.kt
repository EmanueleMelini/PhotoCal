package it.emanuelemelini.photocal.data.share

import it.emanuelemelini.photocal.data.db.ServingUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Share link payload: base64url( HMAC-SHA256[0..16) + raw deflate(JSON) ). Not readable by eye,
 * and any edit (or a truncated link) breaks the signature. Without a server it can't stop
 * someone who extracts the key from the APK: it protects against hand edits, not forgery.
 * Decoded data comes from outside: sizes are bounded and every value is checked.
 */
object ShareCodec {

    const val VERSION = 1

    private const val MAC_BYTES = 16
    private const val MAX_PAYLOAD_CHARS = 32_000
    private const val MAX_JSON_BYTES = 64 * 1024

    private val json = Json {
        ignoreUnknownKeys = true
        // The version must always be written; hidden (null) fields are left out
        encodeDefaults = true
        explicitNulls = false
    }

    sealed interface Result {
        data class Valid(val card: SharedCard) : Result

        /** Corrupted, edited or signed with another key. */
        data object Invalid : Result

        /** Made by a newer app version. */
        data object NewerVersion : Result
    }

    fun encode(card: SharedCard, key: ByteArray): String {
        val compressed = deflate(json.encodeToString(SharedCard.serializer(), card).toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac(key, compressed) + compressed)
    }

    fun decode(payload: String, key: ByteArray): Result {
        if (payload.isEmpty() || payload.length > MAX_PAYLOAD_CHARS) return Result.Invalid
        val bytes = try {
            Base64.getUrlDecoder().decode(payload)
        } catch (_: IllegalArgumentException) {
            return Result.Invalid
        }
        if (bytes.size <= MAC_BYTES) return Result.Invalid
        val compressed = bytes.copyOfRange(MAC_BYTES, bytes.size)
        // Constant-time comparison
        if (!MessageDigest.isEqual(bytes.copyOf(MAC_BYTES), mac(key, compressed))) return Result.Invalid
        val text = inflate(compressed)?.toString(Charsets.UTF_8) ?: return Result.Invalid
        return try {
            // The version first: a newer format may not parse as this one
            val version = json.parseToJsonElement(text).jsonObject["v"]?.jsonPrimitive?.int ?: return Result.Invalid
            if (version > VERSION) return Result.NewerVersion
            val card = json.decodeFromString(SharedCard.serializer(), text)
            if (ShareValidation.isValid(card)) Result.Valid(card) else Result.Invalid
        } catch (_: IllegalArgumentException) {
            // Also SerializationException and NumberFormatException
            Result.Invalid
        }
    }

    private fun mac(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(data).copyOf(MAC_BYTES)
        }

    private fun deflate(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        try {
            deflater.setInput(data)
            deflater.finish()
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
            return out.toByteArray()
        } finally {
            deflater.end()
        }
    }

    /** null if corrupted or larger than [MAX_JSON_BYTES] (a "zip bomb"). */
    private fun inflate(data: ByteArray): ByteArray? {
        val inflater = Inflater(true)
        try {
            inflater.setInput(data)
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) return null
                out.write(buffer, 0, count)
                if (out.size() > MAX_JSON_BYTES) return null
            }
            return out.toByteArray()
        } catch (_: java.util.zip.DataFormatException) {
            return null
        } finally {
            inflater.end()
        }
    }
}

/** Limits of a valid card: generous for real data, tight enough to render safely. */
internal object ShareValidation {
    const val MAX_NAME = 40
    const val MAX_ENTRY_NAME = 100
    const val MAX_PIECE_LABEL = 30
    const val MAX_ENTRIES = 80
    const val MAX_AVATAR_CHARS = 16_000

    fun isValid(card: SharedCard): Boolean {
        val name = card.name.trim()
        if (card.version < 1 || name.isEmpty() || name.length > MAX_NAME) return false
        if ((card.avatar?.length ?: 0) > MAX_AVATAR_CHARS) return false
        if (card.days.isEmpty() || card.days.size > if (card.week) 7 else 1) return false
        if (card.glassMl != null && card.glassMl !in 50..1_000) return false
        card.goals?.let { goals ->
            if (!inRange(goals.kcal, 0..20_000) || !inRange(goals.proteinG, 0..2_000) ||
                !inRange(goals.carbsG, 0..2_000) || !inRange(goals.fatG, 0..2_000) || !inRange(goals.waterMl, 0..20_000)
            ) return false
        }
        // Consecutive days, each in a plausible range
        val first = card.days.first().epochDay
        card.days.forEachIndexed { index, day ->
            if (day.epochDay != first + index || day.epochDay !in EPOCH_DAY_RANGE) return false
            if (!inRange(day.kcal, 0.0..50_000.0) || !inRange(day.proteinG, 0.0..5_000.0) ||
                !inRange(day.carbsG, 0.0..5_000.0) || !inRange(day.fatG, 0.0..5_000.0) ||
                !inRange(day.weightKg, 20.0..400.0)
            ) return false
            if (day.waterMl != null && day.waterMl !in 0..50_000) return false
            val entries = day.entries ?: return@forEachIndexed
            if (card.week || entries.size > MAX_ENTRIES) return false
            if (entries.any { !isValid(it) }) return false
        }
        return true
    }

    private fun isValid(entry: SharedEntry): Boolean =
        entry.mealType != null &&
            entry.name.isNotBlank() && entry.name.length <= MAX_ENTRY_NAME &&
            entry.kcal in 0.0..20_000.0 &&
            inRange(entry.grams, 0.0..20_000.0) && inRange(entry.servings, 0.0..1_000.0) &&
            (entry.unit == null || entry.servingUnit.let { it != null && it != ServingUnit.PIECE }) &&
            inRange(entry.pieces, 0.0..1_000.0) && (entry.pieces == null || entry.unit == null) &&
            (entry.pieceLabel == null || entry.pieceLabel.isNotBlank() && entry.pieceLabel.length <= MAX_PIECE_LABEL)

    private fun inRange(value: Int?, range: IntRange) = value == null || value in range

    /** Also rejects NaN and infinities. */
    private fun inRange(value: Double?, range: ClosedFloatingPointRange<Double>) = value == null || value in range

    /** 2000-01-01 .. 2100-12-31. */
    private val EPOCH_DAY_RANGE = 10_957L..47_846L
}
