package it.emanuelemelini.photocal.ui.settings

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.emanuelemelini.photocal.R
import it.emanuelemelini.photocal.data.backup.Backup
import it.emanuelemelini.photocal.data.backup.BackupCodec
import it.emanuelemelini.photocal.data.backup.BackupManager
import it.emanuelemelini.photocal.data.backup.RestoreMode
import it.emanuelemelini.photocal.ui.UiText
import it.emanuelemelini.photocal.ui.uiText
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Backup read and checked, waiting for the user to choose how to restore it. */
data class PendingRestore(
    val backup: Backup,
    val exportedOn: LocalDate,
    val entries: Int,
    val firstDay: LocalDate?,
    val lastDay: LocalDate?,
)

/** Backup, restore and CSV export of the Data section in Settings. */
class DataViewModel(private val backupManager: BackupManager) : ViewModel() {

    var busy by mutableStateOf(false)
        private set
    var pendingRestore by mutableStateOf<PendingRestore?>(null)
        private set

    /** One-off message for the snackbar, cleared once shown. */
    var message by mutableStateOf<UiText?>(null)
        private set

    fun export(uri: Uri) = run(R.string.data_export_done) { backupManager.export(uri) }

    fun exportCsv(uri: Uri) = run(R.string.data_csv_done) { backupManager.exportCsv(uri) }

    fun open(uri: Uri) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            when (val result = backupManager.read(uri)) {
                is BackupCodec.Result.Valid -> {
                    val backup = result.backup
                    val days = backup.entries.map { it.date }
                    pendingRestore = PendingRestore(
                        backup = backup,
                        exportedOn = Instant.ofEpochSecond(backup.exportedAt).atZone(ZoneId.systemDefault()).toLocalDate(),
                        entries = backup.entries.size,
                        firstDay = days.minOrNull()?.let(LocalDate::ofEpochDay),
                        lastDay = days.maxOrNull()?.let(LocalDate::ofEpochDay),
                    )
                }
                BackupCodec.Result.NewerVersion -> message = uiText(R.string.data_error_newer)
                BackupCodec.Result.Invalid -> message = uiText(R.string.data_error_invalid)
            }
            busy = false
        }
    }

    fun restore(mode: RestoreMode) {
        val pending = pendingRestore ?: return
        pendingRestore = null
        busy = true
        viewModelScope.launch {
            val result = backupManager.restore(pending.backup, mode)
            message = UiText.Plural(R.plurals.data_restore_done, result.entries, listOf(result.entries))
            busy = false
        }
    }

    fun dismissRestore() {
        pendingRestore = null
    }

    fun onMessageShown() {
        message = null
    }

    private fun run(doneRes: Int, block: suspend () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            message = try {
                block()
                uiText(doneRes)
            } catch (_: IOException) {
                uiText(R.string.data_error_write)
            } catch (_: SecurityException) {
                uiText(R.string.data_error_write)
            }
            busy = false
        }
    }
}
