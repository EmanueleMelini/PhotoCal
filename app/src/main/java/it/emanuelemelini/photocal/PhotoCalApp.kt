package it.emanuelemelini.photocal

import android.app.Application
import android.content.Context
import it.emanuelemelini.photocal.data.FoodRepository
import it.emanuelemelini.photocal.data.WaterRepository
import it.emanuelemelini.photocal.data.WeightRepository
import it.emanuelemelini.photocal.data.crea.CreaTable
import it.emanuelemelini.photocal.data.db.AppDatabase
import it.emanuelemelini.photocal.data.estimate.FoodEstimator
import it.emanuelemelini.photocal.data.gemini.GeminiClient
import it.emanuelemelini.photocal.data.openfoodfacts.OpenFoodFactsClient
import it.emanuelemelini.photocal.data.photo.PhotoStorage
import it.emanuelemelini.photocal.data.photo.ProfilePhotoStorage
import it.emanuelemelini.photocal.data.prefs.SettingsRepository
import it.emanuelemelini.photocal.data.reminders.ReminderNotifier
import it.emanuelemelini.photocal.data.reminders.ReminderScheduler
import it.emanuelemelini.photocal.data.share.ShareBuilder
import it.emanuelemelini.photocal.data.update.AppUpdater
import it.emanuelemelini.photocal.data.update.GitHubReleasesClient
import it.emanuelemelini.photocal.data.update.UpdateInstaller
import it.emanuelemelini.photocal.data.update.WhatsNew
import it.emanuelemelini.photocal.ui.shortcuts.AppShortcuts
import it.emanuelemelini.photocal.ui.widget.WaterWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class PhotoCalApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.reminderNotifier.createChannels()
        container.applicationScope.launch {
            container.foodRepository.deleteOrphanPhotos()
            // Cheap and robust: alarms are rebuilt from the settings at every start
            container.reminderScheduler.rescheduleAll()
        }
        container.applicationScope.launch {
            // Widget in line with the water goal and glass size; the first value also redraws
            // it at every start (e.g. a new day)
            container.settingsRepository.settings
                .map { it.waterGoalMl to it.glassMl }
                .distinctUntilChanged()
                .collect { container.waterWidget.update() }
        }
        container.applicationScope.launch {
            // "Share today" appears only with a name. drop(1): the start is handled by
            // MainActivity, and a start in the background (widget, alarm) must not publish
            container.settingsRepository.settings
                .map { it.profile.name.isNotBlank() }
                .distinctUntilChanged()
                .drop(1)
                .collect { AppShortcuts.publish(this@PhotoCalApp) }
        }
    }
}

/** Manual DI: the app's shared dependencies, created once. */
class AppContainer(context: Context) {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val database = AppDatabase.build(context)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val photoStorage = PhotoStorage(context)
    val profilePhotoStorage = ProfilePhotoStorage(context)
    val settingsRepository = SettingsRepository(context)
    val weightRepository = WeightRepository(database.weightDao())
    val waterRepository = WaterRepository(database.waterDao()) {
        waterWidget.update()
        reminderNotifier.refreshWater()
    }
    val waterWidget: WaterWidget = WaterWidget(context, waterRepository, settingsRepository)
    val foodRepository: FoodRepository = FoodRepository(database.foodDao(), photoStorage) { date, meal ->
        // A meal logged today makes its pending reminder pointless
        if (date == LocalDate.now()) reminderNotifier.cancelMeal(meal)
    }
    val reminderNotifier: ReminderNotifier = ReminderNotifier(context, foodRepository, waterRepository, settingsRepository)
    val reminderScheduler = ReminderScheduler(context, settingsRepository)
    val geminiClient = GeminiClient(settingsRepository, httpClient)
    val foodEstimator = FoodEstimator(geminiClient, CreaTable(context), settingsRepository)
    val openFoodFactsClient = OpenFoodFactsClient(httpClient)
    val shareBuilder = ShareBuilder(foodRepository, waterRepository, weightRepository, settingsRepository, profilePhotoStorage)
    val appUpdater = AppUpdater(
        GitHubReleasesClient(httpClient),
        UpdateInstaller(context, httpClient),
        settingsRepository,
        applicationScope,
    )
    val whatsNew = WhatsNew(context, settingsRepository)
}
