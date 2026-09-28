package it.emanuelemelini.photocal.data.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.min

/** Meal photos, stored in the app's internal storage (files/photos). */
class PhotoStorage(private val context: Context) {

    private val directory: File
        get() = File(context.filesDir, "photos").apply { mkdirs() }

    fun newPhotoFile(): File = File(directory, "IMG_${System.currentTimeMillis()}.jpg")

    fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun delete(path: String) {
        File(path).delete()
    }

    /**
     * Resizes the picture (long side at most [maxSide] px, JPEG quality [quality]), applies the
     * EXIF rotation and overwrites the file: the full-resolution original isn't needed, so
     * photos take little space. Returns the JPEG bytes to send to the AI.
     */
    suspend fun shrink(path: String, maxSide: Int = 1024, quality: Int = 80): ByteArray =
        withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Foto non leggibile")

            // Cheap first pass: power-of-two subsampling
            var sampleSize = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= maxSide) sampleSize *= 2
            val decoded = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sampleSize })
                ?: throw IOException("Foto non leggibile")

            val rotation = ExifInterface(path).rotationDegrees
            val scale = min(1f, maxSide.toFloat() / max(decoded.width, decoded.height))
            val matrix = Matrix().apply {
                postScale(scale, scale)
                postRotate(rotation.toFloat())
            }
            val result = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

            val bytes = ByteArrayOutputStream().use { out ->
                result.compress(Bitmap.CompressFormat.JPEG, quality, out)
                out.toByteArray()
            }
            File(path).writeBytes(bytes)
            bytes
        }

    /** Deletes photos not linked to any entry (e.g. abandoned analyses). */
    suspend fun deleteOrphans(referencedPaths: Set<String>, olderThanMillis: Long) =
        withContext(Dispatchers.IO) {
            val threshold = System.currentTimeMillis() - olderThanMillis
            directory.listFiles()
                ?.filter { it.absolutePath !in referencedPaths && it.lastModified() < threshold }
                ?.forEach { it.delete() }
        }
}
