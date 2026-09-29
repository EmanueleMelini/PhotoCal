package it.emanuelemelini.photocal.data.share

import it.emanuelemelini.photocal.data.share.ShareCodec.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import java.util.zip.Deflater
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class ShareCodecTest {

    private val key = "test-key-0123456789".toByteArray()

    private val day = SharedCard(
        name = "Mario",
        sharedAt = 1_790_000_000,
        goals = SharedGoals(kcal = 2000, proteinG = 120, waterMl = 1600),
        glassMl = 200,
        days = listOf(
            SharedDay(
                epochDay = 20_725,
                kcal = 1850.0,
                proteinG = 98.5,
                waterMl = 1400,
                weightKg = 78.4,
                entries = listOf(
                    SharedEntry(meal = "LUNCH", name = "Pasta al pomodoro", kcal = 520.0, grams = 250.0),
                    SharedEntry(meal = "DINNER", name = "Vino rosso", kcal = 250.0, grams = 300.0, unit = "WINE_GLASS", servings = 2.0),
                ),
            )
        ),
    )

    @Test
    fun roundTrip() {
        val payload = ShareCodec.encode(day, key)
        assertEquals(Result.Valid(day), ShareCodec.decode(payload, key))
        // URL-safe without padding: usable as it is after the '#'
        assertTrue(payload.all { it.isLetterOrDigit() || it == '-' || it == '_' })
    }

    @Test
    fun anyEditIsRejected() {
        val payload = ShareCodec.encode(day, key)
        for (index in listOf(0, 10, payload.length / 2, payload.length - 1)) {
            val replacement = if (payload[index] == 'A') 'B' else 'A'
            val edited = payload.substring(0, index) + replacement + payload.substring(index + 1)
            assertEquals("edit at $index", Result.Invalid, ShareCodec.decode(edited, key))
        }
        assertEquals(Result.Invalid, ShareCodec.decode(payload.dropLast(5), key))
        assertEquals(Result.Invalid, ShareCodec.decode(payload + "x", key))
    }

    @Test
    fun anotherKeyIsRejected() {
        val payload = ShareCodec.encode(day, key)
        assertEquals(Result.Invalid, ShareCodec.decode(payload, "other-key".toByteArray()))
    }

    @Test
    fun garbageIsRejected() {
        for (text in listOf("", "abc", "!!!!", "a".repeat(40_000))) {
            assertEquals(Result.Invalid, ShareCodec.decode(text, key))
        }
    }

    @Test
    fun newerVersionIsReported() {
        assertEquals(Result.NewerVersion, ShareCodec.decode(signed("""{"v":2,"future":true}"""), key))
    }

    @Test
    fun signedButImplausibleDataIsRejected() {
        val tooManyDays = day.copy(days = List(2) { day.days[0].copy(epochDay = 20_725L + it, entries = null) })
        val badMeal = day.copy(days = listOf(day.days[0].copy(entries = listOf(SharedEntry("BRUNCH", "x", 1.0)))))
        val longName = day.copy(name = "x".repeat(41))
        val gap = day.copy(week = true, days = listOf(day.days[0].copy(entries = null), day.days[0].copy(epochDay = 20_727, entries = null)))
        for (card in listOf(tooManyDays, badMeal, longName, gap)) {
            assertEquals(Result.Invalid, ShareCodec.decode(ShareCodec.encode(card, key), key))
        }
        assertEquals(Result.Invalid, ShareCodec.decode(signed("""{"v":1,"n":"x","s":1,"d":[{"t":20725,"k":NaN}]}"""), key))
    }

    @Test
    fun weekWithoutMealsIsValid() {
        val week = day.copy(week = true, days = List(7) { SharedDay(epochDay = 20_719L + it, kcal = 1900.0, waterMl = 1200) })
        assertEquals(Result.Valid(week), ShareCodec.decode(ShareCodec.encode(week, key), key))
    }

    @Test
    fun hugeInflatedPayloadIsRejected() {
        // Highly compressible JSON far above the 64 KB limit, correctly signed
        assertEquals(Result.Invalid, ShareCodec.decode(signed("""{"v":1,"n":"${"a".repeat(200_000)}"}"""), key))
    }

    /** Signs arbitrary JSON the way the codec does, to test what happens after the MAC check. */
    private fun signed(json: String): String {
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true).apply {
            setInput(json.toByteArray())
            finish()
        }
        val buffer = ByteArray(1_000_000)
        val compressed = buffer.copyOf(deflater.deflate(buffer))
        deflater.end()
        val mac = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(compressed).copyOf(16)
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac + compressed)
    }
}
