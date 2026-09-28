package it.emanuelemelini.photocal

import android.app.Application
import android.content.Context
import it.emanuelemelini.photocal.data.FoodRepository
import it.emanuelemelini.photocal.data.crea.CreaTable
import it.emanuelemelini.photocal.data.db.AppDatabase
import it.emanuelemelini.photocal.data.estimate.FoodEstimator
import it.emanuelemelini.photocal.data.gemini.GeminiClient
import it.emanuelemelini.photocal.data.openfoodfacts.OpenFoodFactsClient
import it.emanuelemelini.photocal.data.photo.PhotoStorage
import it.emanuelemelini.photocal.data.prefs.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class PhotoCalApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.applicationScope.launch { container.foodRepository.deleteOrphanPhotos() }
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
    val foodRepository = FoodRepository(database.foodDao(), photoStorage)
    val settingsRepository = SettingsRepository(context)
    val geminiClient = GeminiClient(settingsRepository, httpClient)
    val foodEstimator = FoodEstimator(geminiClient, CreaTable(context), settingsRepository)
    val openFoodFactsClient = OpenFoodFactsClient(httpClient)
}
