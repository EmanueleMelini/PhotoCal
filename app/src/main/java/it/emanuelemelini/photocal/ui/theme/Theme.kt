package it.emanuelemelini.photocal.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import it.emanuelemelini.photocal.data.prefs.ThemeMode

// Full green palette (Material Theme Builder style), used when dynamic colors are off or
// unavailable (Android < 12): without every role the purple defaults would remain.
private val LightColors = lightColorScheme(
    primary = Color(0xFF386A20),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB7F397),
    onPrimaryContainer = Color(0xFF042100),
    secondary = Color(0xFF55624C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD9E7CB),
    onSecondaryContainer = Color(0xFF131F0D),
    tertiary = Color(0xFF386666),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBBEBEB),
    onTertiaryContainer = Color(0xFF002020),
    background = Color(0xFFFDFDF6),
    onBackground = Color(0xFF1A1C18),
    surface = Color(0xFFFDFDF6),
    onSurface = Color(0xFF1A1C18),
    surfaceVariant = Color(0xFFDFE4D7),
    onSurfaceVariant = Color(0xFF43483F),
    outline = Color(0xFF73796E),
    outlineVariant = Color(0xFFC3C8BB),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F7F0),
    surfaceContainer = Color(0xFFF1F1EA),
    surfaceContainerHigh = Color(0xFFEBECE4),
    surfaceContainerHighest = Color(0xFFE6E6DF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CD67D),
    onPrimary = Color(0xFF0C3900),
    primaryContainer = Color(0xFF205107),
    onPrimaryContainer = Color(0xFFB7F397),
    secondary = Color(0xFFBDCBB0),
    onSecondary = Color(0xFF283420),
    secondaryContainer = Color(0xFF3E4A35),
    onSecondaryContainer = Color(0xFFD9E7CB),
    tertiary = Color(0xFFA0CFCF),
    onTertiary = Color(0xFF003737),
    tertiaryContainer = Color(0xFF1E4E4E),
    onTertiaryContainer = Color(0xFFBBEBEB),
    background = Color(0xFF1A1C18),
    onBackground = Color(0xFFE3E3DC),
    surface = Color(0xFF1A1C18),
    onSurface = Color(0xFFE3E3DC),
    surfaceVariant = Color(0xFF43483F),
    onSurfaceVariant = Color(0xFFC3C8BB),
    outline = Color(0xFF8D9286),
    outlineVariant = Color(0xFF43483F),
    surfaceContainerLowest = Color(0xFF0F110D),
    surfaceContainerLow = Color(0xFF1A1C18),
    surfaceContainer = Color(0xFF1E201C),
    surfaceContainerHigh = Color(0xFF282B26),
    surfaceContainerHighest = Color(0xFF333630),
)

// Purple themes: purple surfaces with light text, pink accents (buttons, progress, selection).
// PURPLE is a bright medium purple, PURPLE_DARK a deep one; both are chosen directly, not by the
// system mode. Every Material role is set, so nothing falls back to the library defaults.
private val PurpleColors = darkColorScheme(
    primary = Color(0xFFFFB3DA),
    onPrimary = Color(0xFF5C0B3C),
    primaryContainer = Color(0xFFB8327A),
    onPrimaryContainer = Color(0xFFFFE3F0),
    secondary = Color(0xFFF0CCFF),
    onSecondary = Color(0xFF4A1768),
    secondaryContainer = Color(0xFFB07ED6),
    onSecondaryContainer = Color(0xFF2E0845),
    tertiary = Color(0xFFFFD1E3),
    onTertiary = Color(0xFF5C1133),
    tertiaryContainer = Color(0xFFA8407A),
    onTertiaryContainer = Color(0xFFFFE8F1),
    background = Color(0xFF6A2E94),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF6A2E94),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF7D42A8),
    onSurfaceVariant = Color(0xFFEBD6F7),
    outline = Color(0xFFD6B8EA),
    outlineVariant = Color(0xFF9A64C2),
    inverseSurface = Color(0xFFF7ECFF),
    inverseOnSurface = Color(0xFF4A1768),
    inversePrimary = Color(0xFF9C2E6E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    surfaceContainerLowest = Color(0xFF5E2585),
    surfaceContainerLow = Color(0xFF733599),
    surfaceContainer = Color(0xFF7A3BA1),
    surfaceContainerHigh = Color(0xFF8243AA),
    surfaceContainerHighest = Color(0xFF8A4BB3),
)

private val DeepPurpleColors = darkColorScheme(
    primary = Color(0xFFFF9ACD),
    onPrimary = Color(0xFF5C0B3C),
    primaryContainer = Color(0xFF8A2360),
    onPrimaryContainer = Color(0xFFFFD9EA),
    secondary = Color(0xFFD9B8FF),
    onSecondary = Color(0xFF3E1260),
    secondaryContainer = Color(0xFF5A2A80),
    onSecondaryContainer = Color(0xFFF1DBFF),
    tertiary = Color(0xFFFFB0CF),
    onTertiary = Color(0xFF5C1133),
    tertiaryContainer = Color(0xFF7D2A55),
    onTertiaryContainer = Color(0xFFFFD9E6),
    background = Color(0xFF1E0B2B),
    onBackground = Color(0xFFF6EAFF),
    surface = Color(0xFF1E0B2B),
    onSurface = Color(0xFFF6EAFF),
    surfaceVariant = Color(0xFF3A1D52),
    onSurfaceVariant = Color(0xFFD9C2EA),
    outline = Color(0xFFA98BBF),
    outlineVariant = Color(0xFF4F3266),
    inverseSurface = Color(0xFFF6EAFF),
    inverseOnSurface = Color(0xFF3E1260),
    inversePrimary = Color(0xFFA8407A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    surfaceContainerLowest = Color(0xFF170722),
    surfaceContainerLow = Color(0xFF28113A),
    surfaceContainer = Color(0xFF2F1644),
    surfaceContainerHigh = Color(0xFF381C4F),
    surfaceContainerHighest = Color(0xFF42235B),
)

/**
 * Chart column colors: fixed (not dynamic) and checked for color blindness, one pair per
 * palette and brightness. The theme's green/red can't be told apart with deuteranopia.
 */
@Immutable
data class ChartColors(val withinGoal: Color, val overGoal: Color)

private val GreenLightChart = ChartColors(withinGoal = Color(0xFF00897B), overGoal = Color(0xFFD84315))
private val GreenDarkChart = ChartColors(withinGoal = Color(0xFF26A69A), overGoal = Color(0xFFF4511E))
// Purple bars would vanish on purple surfaces: sky blue / orange, checked on both backgrounds
private val PurpleChart = ChartColors(withinGoal = Color(0xFF1E9BD7), overGoal = Color(0xFFF4511E))

val LocalChartColors = staticCompositionLocalOf { GreenLightChart }

@Composable
fun PhotoCalTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic colors (Material You) from Android 12 on; never with the purple themes
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val purple = themeMode.isPurple
    val colorScheme = when {
        themeMode == ThemeMode.PURPLE -> PurpleColors
        themeMode == ThemeMode.PURPLE_DARK -> DeepPurpleColors
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    val chartColors = when {
        purple -> PurpleChart
        else -> if (darkTheme) GreenDarkChart else GreenLightChart
    }
    CompositionLocalProvider(LocalChartColors provides chartColors) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
