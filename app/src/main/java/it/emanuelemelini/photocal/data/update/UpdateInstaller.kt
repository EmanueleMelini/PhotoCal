package it.emanuelemelini.photocal.data.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import androidx.core.content.IntentCompat
import it.emanuelemelini.photocal.PhotoCalApp
import it.emanuelemelini.photocal.data.http.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Downloads the APK of a release straight into a PackageInstaller session and commits it.
 * Android then asks the user to confirm (the intent arrives in [UpdateInstallReceiver]) and
 * rejects an APK that isn't PhotoCal or is signed with another key.
 */
class UpdateInstaller(private val context: Context, private val httpClient: OkHttpClient) {

    private val packageInstaller: PackageInstaller get() = context.packageManager.packageInstaller

    /** "Install unknown apps" granted to PhotoCal in the system settings. */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /**
     * Downloads and commits the APK; [onProgress] gets 0..1, or null when the size is unknown.
     * Throws IOException when the download or the session fails.
     */
    suspend fun downloadAndInstall(release: Release, onProgress: (Float?) -> Unit) = withContext(Dispatchers.IO) {
        // Sessions of an earlier attempt (cancelled or never confirmed) would stay around
        packageInstaller.mySessions.forEach { abandonQuietly(it.sessionId) }

        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            // An APK with another package name is rejected
            setAppPackageName(context.packageName)
            if (release.apkSize > 0) setSize(release.apkSize)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                setPackageSource(PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE)
            }
        }
        val sessionId = packageInstaller.createSession(params)
        try {
            val request = Request.Builder().url(release.apkUrl).get().build()
            httpClient.newCall(request).await().use { response ->
                if (!response.isSuccessful) throw IOException("Download failed: HTTP ${response.code}")
                val total = response.body.contentLength().takeIf { it > 0 } ?: release.apkSize.takeIf { it > 0 }
                packageInstaller.openSession(sessionId).use { session ->
                    session.openWrite(APK_NAME, 0, total ?: -1).use { output ->
                        val input = response.body.byteStream()
                        val buffer = ByteArray(BUFFER_SIZE)
                        var written = 0L
                        onProgress(total?.let { 0f })
                        while (true) {
                            ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            written += read
                            onProgress(total?.let { (written.toFloat() / it).coerceAtMost(1f) })
                        }
                        if (total != null && written != total) throw IOException("Incomplete download: $written of $total bytes")
                        session.fsync(output)
                    }
                    // Mutable: the installer adds the status and the confirmation intent
                    val statusIntent = Intent(context, UpdateInstallReceiver::class.java)
                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        sessionId,
                        statusIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                    )
                    session.commit(pendingIntent.intentSender)
                }
            }
        } catch (e: Throwable) {
            // Also on cancellation: a half-written session is useless
            abandonQuietly(sessionId)
            throw e
        }
    }

    private fun abandonQuietly(sessionId: Int) {
        try {
            packageInstaller.abandonSession(sessionId)
        } catch (e: SecurityException) {
            Log.w(TAG, "Can't abandon session $sessionId", e)
        }
    }

    private companion object {
        const val TAG = "UpdateInstaller"
        const val APK_NAME = "base.apk"
        const val BUFFER_SIZE = 64 * 1024
    }
}

/** Result of the install session: confirmation to ask, success, cancellation or error. */
class UpdateInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val confirmIntent = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
        if (status != PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Log.i(TAG, "Install status $status: ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
        }
        (context.applicationContext as PhotoCalApp).container.appUpdater.onInstallStatus(status, confirmIntent)
    }

    private companion object {
        const val TAG = "UpdateInstall"
    }
}
