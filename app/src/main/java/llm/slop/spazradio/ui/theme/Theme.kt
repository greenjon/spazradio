package llm.slop.spazradio.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import llm.slop.spazradio.AppTheme

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

private val SolarizedDarkColorScheme = darkColorScheme(
    primary = SolarizedBlue,
    secondary = SolarizedCyan,
    tertiary = SolarizedViolet,
    background = SolarizedBase03,
    surface = SolarizedBase02,
    onPrimary = SolarizedBase3,
    onSecondary = SolarizedBase3,
    onTertiary = SolarizedBase3,
    onBackground = SolarizedBase0,
    onSurface = SolarizedBase1,
    error = SolarizedRed
)

private val SolarizedLightColorScheme = lightColorScheme(
    primary = SolarizedBlue,
    secondary = SolarizedCyan,
    tertiary = SolarizedViolet,
    background = SolarizedBase3,
    surface = SolarizedBase2,
    onPrimary = SolarizedBase03,
    onSecondary = SolarizedBase03,
    onTertiary = SolarizedBase03,
    onBackground = SolarizedBase00,
    onSurface = SolarizedBase01,
    error = SolarizedRed
)

// Neon theme is the custom "Spaz" look
private val NeonColorScheme = darkColorScheme(
    primary = NeonGreen,
    secondary = NeonCyan,
    tertiary = NeonMagenta,
    background = Color.Black,
    surface = DeepBlue,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = NeonGreen,
    onSurface = NeonYellow
)

@Composable
fun SpazRadioTheme(
    appTheme: AppTheme = AppTheme.NEON,
    dynamicColor: Boolean = false, // Disable dynamic color by default to favor custom themes
    content: @Composable () -> Unit
) {
    val darkTheme = isSystemInDarkTheme()
    
    val colorScheme = when (appTheme) {
        AppTheme.NEON -> NeonColorScheme
        AppTheme.LIGHT -> SolarizedLightColorScheme
        AppTheme.DARK -> SolarizedDarkColorScheme
        AppTheme.AUTO -> if (darkTheme) SolarizedDarkColorScheme else SolarizedLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
