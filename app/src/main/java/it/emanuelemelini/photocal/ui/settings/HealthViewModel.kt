package it.emanuelemelini.photocal.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.emanuelemelini.photocal.data.health.HealthAvailability
import it.emanuelemelini.photocal.data.health.HealthConnect
import it.emanuelemelini.photocal.data.prefs.Settings
import it.emanuelemelini.photocal.data.prefs.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Health Connect section of Settings. */
class HealthViewModel(
    val healthConnect: HealthConnect,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<Settings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    var availability by mutableStateOf(healthConnect.availability())
        private set

    /** Permissions granted in Health Connect, read again when coming back to the screen. */
    var granted by mutableStateOf<Set<String>>(emptySet())
        private set

    fun refresh() {
        availability = healthConnect.availability()
        if (availability != HealthAvailability.AVAILABLE) return
        viewModelScope.launch { granted = healthConnect.grantedPermissions() }
    }

    /** Result of the Health Connect permission screen: connected if at least one was granted. */
    fun onPermissionsResult(result: Set<String>) {
        granted = result
        viewModelScope.launch { settingsRepository.setHealthConnected(result.isNotEmpty()) }
    }

    fun disconnect() {
        viewModelScope.launch {
            healthConnect.revokeAll()
            settingsRepository.setHealthConnected(false)
            granted = emptySet()
        }
    }

    fun setWrite(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setHealthWrite(enabled) }
    }

    fun setAddBurned(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setHealthAddBurned(enabled) }
    }
}
