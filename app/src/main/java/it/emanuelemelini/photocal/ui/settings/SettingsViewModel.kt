package it.emanuelemelini.photocal.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.emanuelemelini.photocal.R
import it.emanuelemelini.photocal.data.gemini.GeminiClient
import it.emanuelemelini.photocal.data.gemini.GeminiException
import it.emanuelemelini.photocal.data.prefs.Settings
import it.emanuelemelini.photocal.data.prefs.SettingsRepository
import it.emanuelemelini.photocal.data.prefs.ThemeMode
import it.emanuelemelini.photocal.data.reminders.ReminderScheduler
import it.emanuelemelini.photocal.data.reminders.ReminderType
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
    private val geminiClient: GeminiClient,
    private val reminderScheduler: ReminderScheduler,
) : ViewModel() {

    var kcalGoal by mutableStateOf("")
        private set
    var apiKey by mutableStateOf("")
        private set
    var model by mutableStateOf("")
        private set
    var showErrors by mutableStateOf(false)
        private set
    var connectionTest by mutableStateOf<ConnectionTest>(ConnectionTest.Idle)
        private set

    private var testJob: Job? = null

    /** Theme, colors, CREA tables and reminders apply immediately, without the Save button. */
    val appearance: StateFlow<Settings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    val kcalGoalValid get() = kcalGoal.toIntOrNull()?.let { it in KCAL_GOAL_RANGE } == true

    init {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            kcalGoal = settings.dailyKcalGoal.toString()
            apiKey = settings.geminiApiKey
            model = settings.geminiModel
        }
    }

    fun onKcalGoalChange(value: String) {
        kcalGoal = value.filter(Char::isDigit).take(5)
    }

    fun onApiKeyChange(value: String) {
        apiKey = value.trim()
        connectionTest = ConnectionTest.Idle
    }

    fun onModelChange(value: String) {
        model = value.trim()
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

    /** Tests the entered values (even if not saved yet). */
    fun testConnection() {
        testJob?.cancel()
        connectionTest = ConnectionTest.Running
        testJob = viewModelScope.launch {
            connectionTest = try {
                val name = geminiClient.testConnection(apiKey, model.ifBlank { SettingsRepository.DEFAULT_GEMINI_MODEL })
                ConnectionTest.Success(uiText(R.string.settings_connection_ok, name))
            } catch (e: GeminiException) {
                ConnectionTest.Failure(e.toUiText())
            }
        }
    }

    /** Returns true if the values were valid and have been saved. */
    suspend fun save(): Boolean {
        if (!kcalGoalValid) {
            showErrors = true
            return false
        }
        showErrors = false
        settingsRepository.save(
            kcalGoal = kcalGoal.toInt(),
            geminiApiKey = apiKey,
            geminiModel = model.ifBlank { SettingsRepository.DEFAULT_GEMINI_MODEL },
        )
        return true
    }

    companion object {
        val KCAL_GOAL_RANGE = 500..10_000
    }
}
