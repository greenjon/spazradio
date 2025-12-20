package llm.slop.spazradio.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import llm.slop.spazradio.AppTheme

private val LunarizedDarkColorScheme = darkColorScheme(
    primary = LunarizedGreen,
    secondary = LunarizedBlue,
    tertiary = LunarizedMagenta,
    background = LunarizedBase03,
    surface = LunarizedBase02,
    onPrimary = LunarizedBase3, // Selected icon
    onSecondary = LunarizedBase3, // Unselected icon (Base 3 on/off for dark)
    onTertiary = LunarizedBase3,
    onBackground = LunarizedBase0,
    onSurface = LunarizedBase1, // Base color for text
    onSurfaceVariant = LunarizedBase0,
    error = LunarizedRed
)

private val LunarizedLightColorScheme = lightColorScheme(
    primary = LunarizedGreen,
    secondary = LunarizedBlue,
    tertiary = LunarizedMagenta,
    background = LunarizedBase3,
    surface = LunarizedBase2,
    onPrimary = LunarizedBase3, // Selected icon (Base 3 when on)
    onSecondary = LunarizedBase03, // Unselected icon (Base 03 when off)
    onTertiary = LunarizedBase03,
    onBackground = LunarizedBase00,
    onSurface = LunarizedBase01, // Base color for text
    onSurfaceVariant = LunarizedBase00,
    error = LunarizedRed
)

private val NeonColorScheme = darkColorScheme(
    primary = NeonGreen,
    secondary = NeonCyan,
    tertiary = NeonMagenta,
    background = Color.Black,
    surface = DeepBlue,
    onPrimary = Color.Black,
    onSecondary = NeonGreen, // Match old logic where unselected was green
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
        AppTheme.LIGHT -> LunarizedLightColorScheme
        AppTheme.DARK -> LunarizedDarkColorScheme
        AppTheme.AUTO -> if (darkTheme) LunarizedDarkColorScheme else LunarizedLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
