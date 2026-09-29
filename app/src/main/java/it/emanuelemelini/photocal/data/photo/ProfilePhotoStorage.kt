package it.emanuelemelini.photocal.data.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.content.Context
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Base64
import kotlin.math.max
import kotlin.math.min

/**
 * The optional profile photo: a square JPEG in files/profile, cropped from the center of the
 * chosen picture. [version] changes at every save or removal, so the UI reloads it.
 */
class ProfilePhotoStorage(context: Context) {

    private val file = File(File(context.filesDir, "profile").apply { mkdirs() }, "avatar.jpg")

    private val _version = MutableStateFlow(if (file.exists()) file.lastModified() else 0L)
    val version: StateFlow<Long> = _version.asStateFlow()

    val exists: Boolean get() = file.exists()

    /** [open] is called more than once (bounds, pixels, EXIF). */
    suspend fun save(open: () -> InputStream) = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Unreadable photo")
        var sampleSize = 1
        while (min(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= SIDE) sampleSize *= 2
        val decoded = open().use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sampleSize }) }
            ?: throw IOException("Unreadable photo")
        val rotation = try {
            open().use { ExifInterface(it).rotationDegrees }
        } catch (_: IOException) {
            0
        }

        // Center square, scaled to SIDE and rotated upright
        val side = min(decoded.width, decoded.height)
        val scale = min(1f, SIDE.toFloat() / side)
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotation.toFloat())
        }
        val square = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side, matrix, true)
        val temp = File(file.parentFile, "avatar.tmp")
        temp.outputStream().use { square.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        if (!temp.renameTo(file)) throw IOException("Can't save the photo")
        _version.value = System.currentTimeMillis()
    }

    suspend fun delete() = withContext(Dispatchers.IO) {
        file.delete()
        _version.value = System.currentTimeMillis()
    }

    suspend fun load(): Bitmap? = withContext(Dispatchers.IO) {
        if (file.exists()) BitmapFactory.decodeFile(file.path) else null
    }

    /** Small JPEG for the share links, in base64: a few KB. */
    suspend fun thumbnailBase64(): String? = withContext(Dispatchers.IO) {
        val photo = load() ?: return@withContext null
        val thumbnail = photo.scale(THUMBNAIL_SIDE, THUMBNAIL_SIDE)
        var quality = 75
        var bytes: ByteArray
        do {
            bytes = ByteArrayOutputStream().use { out ->
                thumbnail.compress(Bitmap.CompressFormat.JPEG, quality, out)
                out.toByteArray()
            }
            quality -= 15
        } while (bytes.size > MAX_THUMBNAIL_BYTES && quality > 20)
        Base64.getEncoder().encodeToString(bytes)
    }

    companion object {
        const val SIDE = 256
        const val THUMBNAIL_SIDE = 96

        /** Base64 of this stays under the limit accepted by the share links. */
        private const val MAX_THUMBNAIL_BYTES = 9_000

        /** Decodes a thumbnail received in a link, refusing anything that isn't a small image. */
        fun decodeThumbnail(base64: String): Bitmap? {
            val bytes = try {
                Base64.getDecoder().decode(base64)
            } catch (_: IllegalArgumentException) {
                return null
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth !in 1..SIDE || bounds.outHeight !in 1..SIDE) return null
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }
}
