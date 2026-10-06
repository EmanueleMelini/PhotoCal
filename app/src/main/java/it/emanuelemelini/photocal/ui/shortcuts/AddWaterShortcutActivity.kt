package it.emanuelemelini.photocal.ui.shortcuts

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import it.emanuelemelini.photocal.AppLocale
import it.emanuelemelini.photocal.PhotoCalApp
import it.emanuelemelini.photocal.R
import it.emanuelemelini.photocal.data.nutrition.WaterCalculator
import it.emanuelemelini.photocal.ui.formatAmount
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Target of the "+1 glass of water" and "+1 bottle" shortcuts: adds the water to today and
 * closes, without opening the app; a toast shows the new count. Shortcuts can only start activities, hence
 * this invisible one. It stays open until the toast is shown: from the background Android
 * drops the toasts of apps whose notifications are off.
 */
class AddWaterShortcutActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            finish()
            return
        }
        val container = (application as PhotoCalApp).container
        val bottle = intent.getBooleanExtra(EXTRA_BOTTLE, false)
        // On the app scope: the water is saved even if the activity goes away meanwhile
        val message = container.applicationScope.async {
            // Also redraws the widget. A pinned bottle shortcut can outlive the bottle
            val added = if (bottle) {
                container.waterWidget.addBottle()
            } else {
                container.waterWidget.addGlass()
                true
            }
            val settings = container.settingsRepository.settings.first()
            val ml = container.waterRepository.mlFor(LocalDate.now())
            val res = AppLocale.localizedContext(applicationContext).resources
            val goal = WaterCalculator.goalGlasses(settings.waterGoalMl, settings.glassMl)
            val drunk = WaterCalculator.glasses(ml, settings.glassMl).formatAmount(res.configuration.locales[0])
            val count = res.getQuantityString(R.plurals.water_glasses_of_goal, goal, drunk, goal)
            when {
                !added -> res.getString(R.string.shortcut_bottle_missing)
                bottle -> res.getString(R.string.shortcut_bottle_added, count)
                else -> res.getString(R.string.shortcut_water_added, count)
            }
        }
        lifecycleScope.launch {
            Toast.makeText(applicationContext, message.await(), Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    companion object {
        /** The whole bottle instead of a glass. */
        const val EXTRA_BOTTLE = "bottle"
    }
}
