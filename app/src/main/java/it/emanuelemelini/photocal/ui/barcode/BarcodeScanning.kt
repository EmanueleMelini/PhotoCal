package it.emanuelemelini.photocal.ui.barcode

import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Opens the Google Play services scanner (no camera permission needed) and returns the
 * scanned code, or null if the user cancels.
 */
suspend fun scanBarcode(context: Context): String? = suspendCancellableCoroutine { continuation ->
    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_EAN_13,
            Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_UPC_A,
            Barcode.FORMAT_UPC_E,
        )
        .enableAutoZoom()
        .build()
    GmsBarcodeScanning.getClient(context, options)
        .startScan()
        .addOnSuccessListener { barcode -> continuation.resume(barcode.rawValue) }
        .addOnCanceledListener { continuation.resume(null) }
        .addOnFailureListener { e -> continuation.resumeWithException(e) }
}
