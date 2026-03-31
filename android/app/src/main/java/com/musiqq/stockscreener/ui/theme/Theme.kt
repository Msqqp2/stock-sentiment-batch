package com.musiqq.stockscreener.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.musiqq.stockscreener.data.local.ThemeMode

data class StockColors(
    val up: Color,
    val down: Color,
    val cardAlt: Color,
    val border: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val badgeBuy: Color,
    val badgeSell: Color,
    val navSelected: Color,
    val navUnselected: Color,
) {
    companion object {
        val current: StockColors
            @Composable
            @ReadOnlyComposable
            get() = LocalStockColors.current
    }
}

val LocalStockColors = staticCompositionLocalOf {
    StockColors(
        up = Dark_Positive,
        down = Dark_Negative,
        cardAlt = Dark_CardAlt,
        border = Dark_Border,
        textSecondary = Dark_TextSecondary,
        textTertiary = Dark_TextSecondary,
        badgeBuy = Dark_BadgeBuy,
        badgeSell = Dark_BadgeSell,
        navSelected = Dark_TextPrimary,
        navUnselected = Dark_TextSecondary,
    )
}

private val DarkColorScheme = darkColorScheme(
    surface = Dark_Surface,
    background = Dark_Surface,
    onSurface = Dark_TextPrimary,
    onBackground = Dark_TextPrimary,
    primary = Dark_Accent,
    surfaceVariant = Dark_Card,
    onSurfaceVariant = Dark_TextSecondary,
)

private val LightColorScheme = lightColorScheme(
    surface = Light_Surface,
    background = Light_Background,
    onSurface = Light_TextPrimary,
    onBackground = Light_TextPrimary,
    primary = Light_Accent,
    surfaceVariant = Light_Surface,
    onSurfaceVariant = Light_TextSecondary,
    outline = Light_Border,
    surfaceContainerLowest = Light_Surface,
    surfaceContainer = Light_Background,
    surfaceContainerHigh = Light_Surface,
)

@Composable
fun StockScreenerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val stockColors = if (darkTheme) {
        StockColors(
            up = Dark_Positive,
            down = Dark_Negative,
            cardAlt = Dark_CardAlt,
            border = Dark_Border,
            textSecondary = Dark_TextSecondary,
            textTertiary = Dark_TextSecondary,
            badgeBuy = Dark_BadgeBuy,
            badgeSell = Dark_BadgeSell,
            navSelected = Dark_TextPrimary,
            navUnselected = Dark_TextSecondary,
        )
    } else {
        StockColors(
            up = Light_Positive,
            down = Light_Negative,
            cardAlt = Light_CardAlt,
            border = Light_Border,
            textSecondary = Light_TextSecondary,
            textTertiary = Light_TextTertiary,
            badgeBuy = Light_BadgeBuy,
            badgeSell = Light_BadgeSell,
            navSelected = Light_NavSelected,
            navUnselected = Light_NavUnselected,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalStockColors provides stockColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = StockTypography,
            content = content,
        )
    }
}
