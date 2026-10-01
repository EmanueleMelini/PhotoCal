package it.emanuelemelini.photocal.data.update

import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import it.emanuelemelini.photocal.BuildConfig
import it.emanuelemelini.photocal.data.prefs.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalDate

/** Why an update couldn't be installed; the UI picks the text. */
enum class UpdateError {
    /** Network or storage error while downloading. */
    DOWNLOAD,

    /** The APK is signed with another key (e.g. over a development build). */
    SIGNATURE,

    /** Not enough space to install. */
    STORAGE,

    /** Any other installer error. */
    INSTALL,
}

sealed interface UpdateState {
    data object Idle : UpdateState

    /** Manual check only: the automatic one runs silently. */
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data object CheckFailed : UpdateState

    data class Available(val release: Release) : UpdateState

    /** "Install unknown apps" is off: [requested] while the system settings are open. */
    data class NeedsPermission(val release: Release, val requested: Boolean = false, val denied: Boolean = false) : UpdateState

    /** [progress] 0..1, null when the size is unknown. */
    data class Downloading(val release: Release, val progress: Float?) : UpdateState

    /** The system confirmation to launch from the activity (a background start may be blocked). */
    data class ConfirmInstall(val release: Release, val intent: Intent) : UpdateState

    data class Failed(val release: Release, val error: UpdateError) : UpdateState
}

/**
 * Checks GitHub Releases for a newer version and installs it. The state lives here (not in a
 * ViewModel) because the installer answers through a receiver.
 */
class AppUpdater(
    private val releasesClient: GitHubReleasesClient,
    private val installer: UpdateInstaller,
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val currentVersion = AppVersion.parse(BuildConfig.VERSION_NAME)
    private var checkJob: Job? = null
    private var downloadJob: Job? = null

    /** Release being installed, for the error shown when the installer answers later. */
    private var installing: Release? = null

    /**
     * At most once a day and only in release builds: a debug build is signed with another
     * key and couldn't install the release APK anyway. Errors are silent, "Later" means tomorrow.
     * [ignoreDailyLimit] when the user opened the "new version" notification.
     */
    fun checkAutomatically(ignoreDailyLimit: Boolean = false) {
        if (BuildConfig.DEBUG || checkJob?.isActive == true) return
        checkJob = scope.launch {
            val today = LocalDate.now().toEpochDay()
            if (!ignoreDailyLimit && settingsRepository.lastUpdateCheckDay() == today) return@launch
            val release = try {
                releasesClient.latestRelease()
            } catch (e: UpdateCheckException) {
                Log.i(TAG, "Automatic check failed: ${e.message}")
                return@launch
            }
            settingsRepository.setLastUpdateCheckDay(today)
            if (release != null && release.isNewer() && _state.value == UpdateState.Idle) {
                _state.value = UpdateState.Available(release)
            }
        }
    }

    /** From Settings, in every build. */
    fun checkNow() {
        if (checkJob?.isActive == true || _state.value.isBusy()) return
        _state.value = UpdateState.Checking
        checkJob = scope.launch {
            _state.value = try {
                val release = releasesClient.latestRelease()
                settingsRepository.setLastUpdateCheckDay(LocalDate.now().toEpochDay())
                if (release != null && release.isNewer()) UpdateState.Available(release) else UpdateState.UpToDate
            } catch (e: UpdateCheckException) {
                Log.i(TAG, "Check failed: ${e.message}")
                UpdateState.CheckFailed
            }
        }
    }

    /** "Later", "Close", or the result of a manual check no longer on screen. */
    fun dismiss() {
        if (_state.value !is UpdateState.Downloading) _state.value = UpdateState.Idle
    }

    /** "Update": straight to the download when PhotoCal may install apps. */
    fun install(release: Release) {
        if (installer.canInstall()) startDownload(release) else _state.value = UpdateState.NeedsPermission(release)
    }

    /** The system settings for "Install unknown apps" are being opened. */
    fun onPermissionRequested(release: Release) {
        _state.value = UpdateState.NeedsPermission(release, requested = true)
    }

    /** Back from the system settings: download if the permission was granted. */
    fun onResume() {
        val current = _state.value
        if (current is UpdateState.NeedsPermission && current.requested) {
            if (installer.canInstall()) {
                startDownload(current.release)
            } else {
                _state.value = UpdateState.NeedsPermission(current.release, denied = true)
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        _state.value = UpdateState.Idle
    }

    /** The confirmation was shown to the user: its answer comes through [onInstallStatus]. */
    fun onConfirmLaunched() {
        if (_state.value is UpdateState.ConfirmInstall) _state.value = UpdateState.Idle
    }

    /** From [UpdateInstallReceiver]. On success Android replaces the app and ends the process. */
    fun onInstallStatus(status: Int, confirmIntent: Intent?) {
        val release = installing ?: return
        _state.value = when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION ->
                confirmIntent?.let { UpdateState.ConfirmInstall(release, it) } ?: UpdateState.Failed(release, UpdateError.INSTALL)
            PackageInstaller.STATUS_SUCCESS, PackageInstaller.STATUS_FAILURE_ABORTED -> UpdateState.Idle
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                UpdateState.Failed(release, UpdateError.SIGNATURE)
            PackageInstaller.STATUS_FAILURE_STORAGE -> UpdateState.Failed(release, UpdateError.STORAGE)
            else -> UpdateState.Failed(release, UpdateError.INSTALL)
        }
    }

    private fun startDownload(release: Release) {
        installing = release
        downloadJob?.cancel()
        // Before the job starts, so its progress and errors aren't overwritten
        _state.value = UpdateState.Downloading(release, progress = null)
        downloadJob = scope.launch {
            try {
                installer.downloadAndInstall(release) { progress ->
                    _state.update { if (it is UpdateState.Downloading) it.copy(progress = progress) else it }
                }
            } catch (e: IOException) {
                Log.w(TAG, "Update download failed", e)
                _state.value = UpdateState.Failed(release, UpdateError.DOWNLOAD)
            } catch (e: SecurityException) {
                // Permission revoked in the meantime
                Log.w(TAG, "Update install refused", e)
                _state.value = UpdateState.NeedsPermission(release, denied = true)
            }
        }
    }

    private fun Release.isNewer(): Boolean = currentVersion != null && version > currentVersion

    private fun UpdateState.isBusy(): Boolean = this is UpdateState.Downloading || this is UpdateState.ConfirmInstall

    private companion object {
        const val TAG = "AppUpdater"
    }
}
