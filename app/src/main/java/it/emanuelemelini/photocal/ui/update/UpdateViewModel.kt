package it.emanuelemelini.photocal.ui.update

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.emanuelemelini.photocal.data.update.AppUpdater
import it.emanuelemelini.photocal.data.update.ChangelogEntry
import it.emanuelemelini.photocal.data.update.Release
import it.emanuelemelini.photocal.data.update.UpdateState
import it.emanuelemelini.photocal.data.update.WhatsNew
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Activity-wide: the changelog after an update and the update dialogs. */
class UpdateViewModel(private val appUpdater: AppUpdater, private val whatsNew: WhatsNew) : ViewModel() {

    val state: StateFlow<UpdateState> = appUpdater.state

    /** Notes of the versions installed since the last start; empty when there is nothing new. */
    var changelog by mutableStateOf<List<ChangelogEntry>>(emptyList())
        private set

    init {
        viewModelScope.launch { changelog = whatsNew.pending() }
    }

    fun dismissChangelog() {
        changelog = emptyList()
        viewModelScope.launch { whatsNew.markSeen() }
    }

    fun install(release: Release) = appUpdater.install(release)
    fun onPermissionRequested(release: Release) = appUpdater.onPermissionRequested(release)
    fun onResume() = appUpdater.onResume()
    fun cancelDownload() = appUpdater.cancelDownload()
    fun onConfirmLaunched() = appUpdater.onConfirmLaunched()
    fun dismiss() = appUpdater.dismiss()
}
