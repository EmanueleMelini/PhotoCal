package it.emanuelemelini.photocal.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.emanuelemelini.photocal.R
import it.emanuelemelini.photocal.data.ai.AiConfig
import it.emanuelemelini.photocal.data.ai.AiException
import it.emanuelemelini.photocal.data.ai.AiProvider
import it.emanuelemelini.photocal.data.ai.AiService
import it.emanuelemelini.photocal.data.prefs.Settings
import it.emanuelemelini.photocal.data.prefs.SettingsRepository
import it.emanuelemelini.photocal.data.prefs.ThemeMode
import it.emanuelemelini.photocal.data.reminders.ReminderScheduler
import it.emanuelemelini.photocal.data.reminders.ReminderType
import it.emanuelemelini.photocal.data.update.AppUpdater
import it.emanuelemelini.photocal.data.update.UpdateState
import it.emanuelemelini.photocal.ui.UiText
import it.emanuelemelini.photocal.ui.toUiText
import it.emanuelemelini.photocal.ui.uiText
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime

sealed interface ConnectionTest {
    data object Idle : ConnectionTest
    data object Running : ConnectionTest
    data class Success(val message: UiText) : ConnectionTest
    data class Failure(val message: UiText) : ConnectionTest
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val aiService: AiService,
    private val reminderScheduler: ReminderScheduler,
    private val appUpdater: AppUpdater,
) : ViewModel() {

    /** AI shown in Settings; it becomes the one in use with Save. */
    var provider by mutableStateOf(AiProvider.GEMINI)
        private set

    /** Key and model of every AI, so switching back and forth loses nothing. */
    private val apiKeys = mutableStateMapOf<AiProvider, String>()
    private val models = mutableStateMapOf<AiProvider, String>()

    val apiKey: String get() = apiKeys[provider].orEmpty()
    val model: String get() = models[provider].orEmpty()

    var baseUrl by mutableStateOf("")
        private set
    var connectionTest by mutableStateOf<ConnectionTest>(ConnectionTest.Idle)
        private set

    private var testJob: Job? = null

    /** Theme, colors, CREA tables and reminders apply immediately, without the Save button. */
    val appearance: StateFlow<Settings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    init {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            provider = settings.aiProvider
            AiProvider.entries.forEach { p ->
                apiKeys[p] = settings.aiApiKey(p)
                models[p] = settings.aiModel(p)
            }
            baseUrl = settings.compatibleBaseUrl
        }
    }

    fun onProviderChange(value: AiProvider) {
        provider = value
        resetConnectionTest()
    }

    fun onApiKeyChange(value: String) {
        apiKeys[provider] = value.trim()
        resetConnectionTest()
    }

    fun onModelChange(value: String) {
        models[provider] = value.trim()
        resetConnectionTest()
    }

    fun onBaseUrlChange(value: String) {
        baseUrl = value.trim()
        resetConnectionTest()
    }

    private fun resetConnectionTest() {
        testJob?.cancel()
        connectionTest = ConnectionTest.Idle
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDynamicColor(enabled) }
    }

    fun setUseCrea(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setUseCrea(enabled) }
    }

    fun setReminderEnabled(type: ReminderType, enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setReminderEnabled(type, enabled)
            reminderScheduler.reschedule(type)
        }
    }

    fun setReminderTime(type: ReminderType, time: LocalTime) {
        viewModelScope.launch {
            settingsRepository.setReminderTime(type, time)
            reminderScheduler.reschedule(type)
        }
    }

    /** A found update opens its dialog over every screen; the other results stay here. */
    val updateState: StateFlow<UpdateState> = appUpdater.state

    fun checkForUpdates() = appUpdater.checkNow()

    override fun onCleared() {
        // "Up to date" or "check failed" would be stale the next time Settings opens
        if (updateState.value == UpdateState.UpToDate || updateState.value == UpdateState.CheckFailed) appUpdater.dismiss()
    }

    /** Tests the entered values (even if not saved yet). */
    fun testConnection() {
        testJob?.cancel()
        connectionTest = ConnectionTest.Running
        testJob = viewModelScope.launch {
            connectionTest = try {
                val name = aiService.testConnection(provider, AiConfig(apiKey, model.ifBlank { provider.defaultModel }, baseUrl))
                ConnectionTest.Success(uiText(R.string.settings_connection_ok, name))
            } catch (e: AiException) {
                ConnectionTest.Failure(e.toUiText())
            }
        }
    }

    /**
     * Saves the chosen AI with keys and models (the other settings apply immediately). A
     * default model isn't stored, so it follows the default of later versions.
     */
    suspend fun save() {
        settingsRepository.saveAi(
            provider = provider,
            apiKeys = apiKeys.toMap(),
            models = models.filter { (p, m) -> m.isNotBlank() && m != p.defaultModel },
            compatibleBaseUrl = baseUrl,
        )
    }
}
