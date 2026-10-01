package it.emanuelemelini.photocal.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import it.emanuelemelini.photocal.data.db.MealType
import it.emanuelemelini.photocal.ui.barcode.BarcodeScreen
import it.emanuelemelini.photocal.ui.entry.EntryScreen
import it.emanuelemelini.photocal.ui.health.HealthPrivacyScreen
import it.emanuelemelini.photocal.ui.history.HistoryScreen
import it.emanuelemelini.photocal.ui.photo.PhotoReviewScreen
import it.emanuelemelini.photocal.ui.profile.ProfileScreen
import it.emanuelemelini.photocal.ui.recent.RecentFoodsScreen
import it.emanuelemelini.photocal.ui.settings.SettingsScreen
import it.emanuelemelini.photocal.ui.share.ShareScreen
import it.emanuelemelini.photocal.ui.share.SharedViewScreen
import it.emanuelemelini.photocal.ui.today.PhotoRequest
import it.emanuelemelini.photocal.ui.today.TodayScreen
import it.emanuelemelini.photocal.ui.weight.WeightScreen
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
object TodayRoute

/**
 * [entryId] = 0 means a new entry for day [dateEpochDay], optionally with a prefilled
 * name ([prefillName], e.g. from a barcode without nutrition facts), meal ([meal], the
 * [MealType] name, e.g. from a reminder) or saved food ([savedFoodId], from the recent foods).
 */
@Serializable
data class EntryRoute(
    val dateEpochDay: Long,
    val entryId: Long = 0L,
    val prefillName: String? = null,
    val meal: String? = null,
    val savedFoodId: Long = 0L,
)

/** What PhotoCal does with Health Connect data (also opened by Health Connect itself). */
@Serializable
object HealthPrivacyRoute

/** Recent and favorite foods, to add one to day [dateEpochDay]. */
@Serializable
data class RecentFoodsRoute(val dateEpochDay: Long)

/** [showAi]: opens Settings already scrolled to the AI section. */
@Serializable
data class SettingsRoute(val showAi: Boolean = false)

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

/** Share options of a day (or of the week ending on it). */
@Serializable
data class ShareRoute(val dateEpochDay: Long)

/** A day someone shared: [payload] is the part of the link after '#'. */
@Serializable
data class SharedViewRoute(val payload: String)

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
    val telemetry = appContainer().telemetry

    // Usage statistics (if allowed): which screens are opened, never their arguments
    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collect { entry -> telemetry.logScreen(screenName(entry.destination)) }
    }

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
                onOpenSettings = { navController.navigate(SettingsRoute()) },
                onOpenAiSettings = { navController.navigate(SettingsRoute(showAi = true)) },
                onPhotoTaken = { path, date, meal ->
                    navController.navigate(PhotoReviewRoute(path, date.toEpochDay(), meal?.name))
                },
                onScanBarcode = { date -> navController.navigate(BarcodeRoute(date.toEpochDay())) },
                onOpenRecent = { date -> navController.navigate(RecentFoodsRoute(date.toEpochDay())) },
                onShare = { date -> navController.navigate(ShareRoute(date.toEpochDay())) },
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
        composable<RecentFoodsRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<RecentFoodsRoute>()
            RecentFoodsScreen(
                onBack = { navController.popBackStack() },
                onPick = { food ->
                    // Replaces the list: Back from the form returns to Today
                    navController.navigate(EntryRoute(route.dateEpochDay, savedFoodId = food.id)) {
                        popUpTo<RecentFoodsRoute> { inclusive = true }
                    }
                },
            )
        }
        composable<PhotoReviewRoute> {
            PhotoReviewScreen(
                onDone = { navController.popBackStack() },
                onOpenAiSettings = { navController.navigate(SettingsRoute(showAi = true)) },
            )
        }
        composable<EntryRoute> {
            EntryScreen(
                onDone = { navController.popBackStack() },
                onOpenAiSettings = { navController.navigate(SettingsRoute(showAi = true)) },
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
        composable<SettingsRoute> { backStackEntry ->
            SettingsScreen(
                showAi = backStackEntry.toRoute<SettingsRoute>().showAi,
                onBack = { navController.popBackStack() },
                onOpenProfile = { navController.navigate(ProfileRoute) },
                onOpenHealthPrivacy = { navController.navigate(HealthPrivacyRoute) },
            )
        }
        composable<HealthPrivacyRoute> {
            HealthPrivacyScreen(onBack = { navController.popBackStack() })
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
        composable<ShareRoute> {
            ShareScreen(
                onBack = { navController.popBackStack() },
                onOpenProfile = { navController.navigate(ProfileRoute) },
            )
        }
        composable<SharedViewRoute> {
            SharedViewScreen(onClose = { navController.popBackStack() })
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
            is LaunchRequest.OpenShared -> navController.navigate(SharedViewRoute(request.payload))
            LaunchRequest.ShareToday -> navController.navigate(ShareRoute(todayEpochDay))
            LaunchRequest.HealthPrivacy -> navController.navigate(HealthPrivacyRoute)
            // The dialog is shown by MainActivity over Today
            LaunchRequest.ShowUpdate -> Unit
        }
        onLaunchRequestHandled()
    }
}

/** Fixed names for the statistics: the routes also hold dates, ids and typed names. */
private fun screenName(destination: NavDestination): String = when {
    destination.hasRoute<TodayRoute>() -> "today"
    destination.hasRoute<EntryRoute>() -> "entry"
    destination.hasRoute<HealthPrivacyRoute>() -> "health_privacy"
    destination.hasRoute<RecentFoodsRoute>() -> "recent_foods"
    destination.hasRoute<SettingsRoute>() -> "settings"
    destination.hasRoute<PhotoReviewRoute>() -> "photo_review"
    destination.hasRoute<BarcodeRoute>() -> "barcode"
    destination.hasRoute<HistoryRoute>() -> "history"
    destination.hasRoute<ProfileRoute>() -> "profile"
    destination.hasRoute<WeightRoute>() -> "weight"
    destination.hasRoute<ShareRoute>() -> "share"
    destination.hasRoute<SharedViewRoute>() -> "shared_view"
    else -> "other"
}
