package llm.slop.spazradio.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import llm.slop.spazradio.AppTheme

private val SolarizedDarkColorScheme = darkColorScheme(
    primary = SolarizedGreen,
    secondary = SolarizedBlue,
    tertiary = SolarizedMagenta,
    background = SolarizedBase03,
    surface = SolarizedBase02,
    onPrimary = SolarizedBase3,
    onSecondary = SolarizedBase3,
    onTertiary = SolarizedBase3,
    onBackground = SolarizedBase0,
    onSurface = SolarizedYellow,
    error = SolarizedRed
)

private val SolarizedLightColorScheme = lightColorScheme(
    primary = SolarizedGreen,
    secondary = SolarizedBlue,
    tertiary = SolarizedMagenta,
    background = SolarizedBase3,
    surface = SolarizedBase2,
    onPrimary = SolarizedBase03,
    onSecondary = SolarizedBase03,
    onTertiary = SolarizedBase03,
    onBackground = SolarizedBase00,
    onSurface = SolarizedYellow,
    error = SolarizedRed
)

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
