package it.emanuelemelini.photocal

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.emanuelemelini.photocal.data.prefs.Settings
import it.emanuelemelini.photocal.data.prefs.ThemeMode
import it.emanuelemelini.photocal.ui.LaunchRequest
import it.emanuelemelini.photocal.ui.PhotoCalNavHost
import it.emanuelemelini.photocal.ui.theme.PhotoCalTheme

/** AppCompatActivity (instead of ComponentActivity) so the in-app language works before Android 13. */
class MainActivity : AppCompatActivity() {

    /** Request coming from a notification, handled once by the navigation. */
    private var launchRequest by mutableStateOf<LaunchRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // The activity is recreated on every language change, so this is always up to date
        AppLocale.current = resources.configuration.locales[0]
        // Not on recreation (e.g. language change): the request was already handled
        if (savedInstanceState == null) launchRequest = LaunchRequest.from(intent)
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
                PhotoCalNavHost(
                    launchRequest = launchRequest,
                    onLaunchRequestHandled = { launchRequest = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        LaunchRequest.from(intent)?.let { launchRequest = it }
    }

    private companion object {
        // Same default scrims as enableEdgeToEdge()
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
