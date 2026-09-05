package com.esa.moneytracker.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightScheme = lightColorScheme(
    primary = Emerald600,
    onPrimary = Color.White,
    primaryContainer = Emerald100,
    onPrimaryContainer = Emerald900,
    secondary = Gold600,
    onSecondary = Color.White,
    secondaryContainer = Gold100,
    onSecondaryContainer = Color(0xFF3A2600),
    tertiary = Sky500,
    onTertiary = Color.White,
    tertiaryContainer = Sky100,
    onTertiaryContainer = Color(0xFF0B2C46),
    background = Ink50,
    onBackground = Ink900,
    surface = Color.White,
    onSurface = Ink900,
    surfaceVariant = Ink100,
    onSurfaceVariant = Ink500,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerLow = Ink50,
    outline = Ink200,
    outlineVariant = Ink100,
    error = Coral600,
    onError = Color.White,
    errorContainer = Coral100,
    onErrorContainer = Color(0xFF54130D),
)

private val DarkScheme = darkColorScheme(
    primary = Emerald300,
    onPrimary = Emerald900,
    primaryContainer = Emerald700,
    onPrimaryContainer = Emerald100,
    secondary = Gold300,
    onSecondary = Color(0xFF2E1D00),
    secondaryContainer = Gold600,
    onSecondaryContainer = Color(0xFF2E1D00),
    tertiary = Sky300,
    onTertiary = Color(0xFF0B2C46),
    tertiaryContainer = Color(0xFF14405F),
    onTertiaryContainer = Sky100,
    background = Ink900,
    onBackground = Ink50,
    surface = Ink800,
    onSurface = Ink50,
    surfaceVariant = Ink700,
    onSurfaceVariant = Ink200,
    surfaceContainer = Ink800,
    surfaceContainerHigh = Ink700,
    surfaceContainerLow = Ink900,
    outline = Ink500,
    outlineVariant = Color(0xFF2A3733),
    error = Coral300,
    onError = Color(0xFF54130D),
    errorContainer = Color(0xFF4A1A15),
    onErrorContainer = Coral300,
)

@Composable
fun MoneyTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    val moneyColors = if (darkTheme) DarkMoneyColors else LightMoneyColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalMoneyColors provides moneyColors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = MoneyTypography,
            shapes = MoneyShapes,
            content = content,
        )
    }
}

/** Shorthand for the semantic palette: `MoneyTheme.colors.income`. */
object MoneyTheme {
    val colors: MoneyColors
        @Composable get() = LocalMoneyColors.current
}
