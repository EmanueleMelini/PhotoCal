package it.emanuelemelini.photocal

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.emanuelemelini.photocal.data.prefs.Settings
import it.emanuelemelini.photocal.data.prefs.ThemeMode
import it.emanuelemelini.photocal.ui.PhotoCalNavHost
import it.emanuelemelini.photocal.ui.theme.PhotoCalTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val settingsRepository = (application as PhotoCalApp).container.settingsRepository
        setContent {
            val settings: Settings? by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
            // Until preferences are loaded (a few ms) the window background stays visible:
            // avoids a flash of the wrong theme
            val current = settings ?: return@setContent
            val darkTheme = when (current.themeMode) {
                ThemeMode.CHIARO -> false
                ThemeMode.SCURO -> true
                ThemeMode.SISTEMA -> isSystemInDarkTheme()
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
