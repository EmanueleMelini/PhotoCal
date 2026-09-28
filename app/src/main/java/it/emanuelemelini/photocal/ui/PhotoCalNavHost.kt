package it.emanuelemelini.photocal.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import it.emanuelemelini.photocal.ui.barcode.BarcodeScreen
import it.emanuelemelini.photocal.ui.entry.EntryScreen
import it.emanuelemelini.photocal.ui.history.HistoryScreen
import it.emanuelemelini.photocal.ui.photo.PhotoReviewScreen
import it.emanuelemelini.photocal.ui.settings.SettingsScreen
import it.emanuelemelini.photocal.ui.today.TodayScreen
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
object TodayRoute

/**
 * [entryId] = 0 means a new entry for day [dateEpochDay], optionally with a prefilled
 * name ([prefillName], e.g. from a barcode without nutrition facts).
 */
@Serializable
data class EntryRoute(val dateEpochDay: Long, val entryId: Long = 0L, val prefillName: String? = null)

@Serializable
object SettingsRoute

@Serializable
data class PhotoReviewRoute(val photoPath: String, val dateEpochDay: Long)

@Serializable
data class BarcodeRoute(val dateEpochDay: Long)

@Serializable
object HistoryRoute

/** Key the History screen uses to ask the Today screen to open a day. */
private const val KEY_OPEN_DAY = "open_day"

@Composable
fun PhotoCalNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = TodayRoute) {
        composable<TodayRoute> { backStackEntry ->
            val requestedDay by backStackEntry.savedStateHandle
                .getStateFlow<Long?>(KEY_OPEN_DAY, null)
                .collectAsStateWithLifecycle()
            TodayScreen(
                requestedDay = requestedDay?.let(LocalDate::ofEpochDay),
                onRequestedDayHandled = { backStackEntry.savedStateHandle[KEY_OPEN_DAY] = null },
                onOpenHistory = { navController.navigate(HistoryRoute) },
                onAddManual = { date -> navController.navigate(EntryRoute(date.toEpochDay())) },
                onEditEntry = { entry ->
                    navController.navigate(EntryRoute(entry.date.toEpochDay(), entry.id))
                },
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onPhotoTaken = { path, date ->
                    navController.navigate(PhotoReviewRoute(path, date.toEpochDay()))
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
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
