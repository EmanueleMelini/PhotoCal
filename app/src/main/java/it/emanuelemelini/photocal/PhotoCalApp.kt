package it.emanuelemelini.photocal

import android.app.Application
import android.content.Context
import it.emanuelemelini.photocal.data.FoodRepository
import it.emanuelemelini.photocal.data.WeightRepository
import it.emanuelemelini.photocal.data.crea.CreaTable
import it.emanuelemelini.photocal.data.db.AppDatabase
import it.emanuelemelini.photocal.data.estimate.FoodEstimator
import it.emanuelemelini.photocal.data.gemini.GeminiClient
import it.emanuelemelini.photocal.data.openfoodfacts.OpenFoodFactsClient
import it.emanuelemelini.photocal.data.photo.PhotoStorage
import it.emanuelemelini.photocal.data.prefs.SettingsRepository
import it.emanuelemelini.photocal.data.reminders.ReminderNotifier
import it.emanuelemelini.photocal.data.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    val settingsRepository = SettingsRepository(context)
    val weightRepository = WeightRepository(database.weightDao())
    val foodRepository: FoodRepository = FoodRepository(database.foodDao(), photoStorage) { date, meal ->
        // A meal logged today makes its pending reminder pointless
        if (date == LocalDate.now()) reminderNotifier.cancelMeal(meal)
    }
    val reminderNotifier: ReminderNotifier = ReminderNotifier(context, foodRepository, settingsRepository)
    val reminderScheduler = ReminderScheduler(context, settingsRepository)
    val geminiClient = GeminiClient(settingsRepository, httpClient)
    val foodEstimator = FoodEstimator(geminiClient, CreaTable(context), settingsRepository)
    val openFoodFactsClient = OpenFoodFactsClient(httpClient)
}
