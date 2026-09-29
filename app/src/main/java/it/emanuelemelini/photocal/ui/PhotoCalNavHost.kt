package it.emanuelemelini.photocal.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import it.emanuelemelini.photocal.data.db.MealType
import it.emanuelemelini.photocal.ui.barcode.BarcodeScreen
import it.emanuelemelini.photocal.ui.entry.EntryScreen
import it.emanuelemelini.photocal.ui.history.HistoryScreen
import it.emanuelemelini.photocal.ui.photo.PhotoReviewScreen
import it.emanuelemelini.photocal.ui.profile.ProfileScreen
import it.emanuelemelini.photocal.ui.settings.SettingsScreen
import it.emanuelemelini.photocal.ui.today.PhotoRequest
import it.emanuelemelini.photocal.ui.today.TodayScreen
import it.emanuelemelini.photocal.ui.weight.WeightScreen
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
object TodayRoute

/**
 * [entryId] = 0 means a new entry for day [dateEpochDay], optionally with a prefilled
 * name ([prefillName], e.g. from a barcode without nutrition facts) and meal ([meal], the
 * [MealType] name, e.g. from a reminder).
 */
@Serializable
data class EntryRoute(
    val dateEpochDay: Long,
    val entryId: Long = 0L,
    val prefillName: String? = null,
    val meal: String? = null,
)

@Serializable
object SettingsRoute

/** [meal]: [MealType] name to preselect, e.g. when the photo comes from a reminder. */
@Serializable
data class PhotoReviewRoute(val photoPath: String, val dateEpochDay: Long, val meal: String? = null)

@Serializable
data class BarcodeRoute(val dateEpochDay: Long)

@Serializable
object HistoryRoute

@Serializable
object ProfileRoute

@Serializable
object WeightRoute

/** Key the History screen uses to ask the Today screen to open a day. */
private const val KEY_OPEN_DAY = "open_day"

/** Key a reminder uses to ask the Today screen to take a photo (value: meal name or ""). */
private const val KEY_TAKE_PHOTO = "take_photo"

@Composable
fun PhotoCalNavHost(
    launchRequest: LaunchRequest?,
    onLaunchRequestHandled: () -> Unit,
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = TodayRoute) {
        composable<TodayRoute> { backStackEntry ->
            val requestedDay by backStackEntry.savedStateHandle
                .getStateFlow<Long?>(KEY_OPEN_DAY, null)
                .collectAsStateWithLifecycle()
            val requestedPhoto by backStackEntry.savedStateHandle
                .getStateFlow<String?>(KEY_TAKE_PHOTO, null)
                .collectAsStateWithLifecycle()
            TodayScreen(
                requestedDay = requestedDay?.let(LocalDate::ofEpochDay),
                onRequestedDayHandled = { backStackEntry.savedStateHandle[KEY_OPEN_DAY] = null },
                requestedPhotoMeal = requestedPhoto?.let { name -> PhotoRequest(MealType.entries.find { it.name == name }) },
                onRequestedPhotoHandled = { backStackEntry.savedStateHandle[KEY_TAKE_PHOTO] = null },
                onOpenHistory = { navController.navigate(HistoryRoute) },
                onOpenGoals = { navController.navigate(ProfileRoute) },
                onAddManual = { date -> navController.navigate(EntryRoute(date.toEpochDay())) },
                onEditEntry = { entry ->
                    navController.navigate(EntryRoute(entry.date.toEpochDay(), entry.id))
                },
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onPhotoTaken = { path, date, meal ->
                    navController.navigate(PhotoReviewRoute(path, date.toEpochDay(), meal?.name))
                },
                onScanBarcode = { date -> navController.navigate(BarcodeRoute(date.toEpochDay())) },
            )
        }
        composable<BarcodeRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<BarcodeRoute>()
            BarcodeScreen(
                onDone = { navController.popBackStack() },
                onManualEntry = { name ->
                    // Replaces the barcode screen: Back returns to Today
                    navController.navigate(EntryRoute(route.dateEpochDay, prefillName = name)) {
                        popUpTo<BarcodeRoute> { inclusive = true }
                    }
                },
            )
        }
        composable<PhotoReviewRoute> {
            PhotoReviewScreen(
                onDone = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(SettingsRoute) },
            )
        }
        composable<EntryRoute> {
            EntryScreen(
                onDone = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(SettingsRoute) },
            )
        }
        composable<HistoryRoute> {
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onOpenDay = { date ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(KEY_OPEN_DAY, date.toEpochDay())
                    navController.popBackStack()
                },
                onOpenWeight = { navController.navigate(WeightRoute) },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenProfile = { navController.navigate(ProfileRoute) },
            )
        }
        composable<ProfileRoute> {
            ProfileScreen(
                onBack = { navController.popBackStack() },
                onOpenWeightLog = { navController.navigate(WeightRoute) },
            )
        }
        composable<WeightRoute> {
            WeightScreen(onBack = { navController.popBackStack() })
        }
    }

    // Requests from notifications: always start from Today, on the current day
    LaunchedEffect(launchRequest) {
        val request = launchRequest ?: return@LaunchedEffect
        navController.popBackStack<TodayRoute>(inclusive = false)
        val today = navController.getBackStackEntry<TodayRoute>().savedStateHandle
        val todayEpochDay = LocalDate.now().toEpochDay()
        today[KEY_OPEN_DAY] = todayEpochDay
        when (request) {
            LaunchRequest.OpenToday -> Unit
            LaunchRequest.OpenHistory -> navController.navigate(HistoryRoute)
            is LaunchRequest.AddManual -> navController.navigate(EntryRoute(todayEpochDay, meal = request.meal?.name))
            is LaunchRequest.AddPhoto -> today[KEY_TAKE_PHOTO] = request.meal?.name.orEmpty()
        }
        onLaunchRequestHandled()
    }
}
