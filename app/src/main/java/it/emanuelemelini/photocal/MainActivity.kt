package it.emanuelemelini.photocal

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.emanuelemelini.photocal.data.prefs.Settings
import it.emanuelemelini.photocal.data.prefs.ThemeMode
import it.emanuelemelini.photocal.ui.PhotoCalNavHost
import it.emanuelemelini.photocal.ui.theme.PhotoCalTheme

/** AppCompatActivity (instead of ComponentActivity) so the in-app language works before Android 13. */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // The activity is recreated on every language change, so this is always up to date
        AppLocale.current = resources.configuration.locales[0]
        val settingsRepository = (application as PhotoCalApp).container.settingsRepository
        setContent {
            val settings: Settings? by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
            // Until preferences are loaded (a few ms) the window background stays visible:
            // avoids a flash of the wrong theme
            val current = settings ?: return@setContent
            val darkTheme = when (current.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            // Status and navigation bar icons follow the chosen theme, not the system one
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { darkTheme },
                )
                onDispose {}
            }

            PhotoCalTheme(darkTheme = darkTheme, dynamicColor = current.dynamicColor) {
                PhotoCalNavHost()
            }
        }
    }

    private companion object {
        // Same default scrims as enableEdgeToEdge()
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
