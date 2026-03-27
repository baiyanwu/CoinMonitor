package io.baiyanwu.coinmonitor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import io.baiyanwu.coinmonitor.domain.model.ThemeTemplateId

@Composable
fun CoinMonitorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeTemplate: ThemeTemplateId = ThemeTemplateId.DEFAULT_MD,
    content: @Composable () -> Unit
) {
    val paletteSet = remember(themeTemplate) {
        when (themeTemplate) {
            ThemeTemplateId.DEFAULT_MD -> DefaultMdPalette
        }
    }
    val palette = if (darkTheme) paletteSet.dark else paletteSet.light
    val semanticColors = remember(palette) { palette.toSemanticColors() }
    val colorScheme = remember(palette) {
        if (darkTheme) {
            darkColorScheme(
                primary = palette.primary,
                onPrimary = palette.onPrimary,
                secondary = palette.secondary,
                onSecondary = palette.onSecondary,
                tertiary = palette.tertiary,
                onTertiary = palette.onTertiary,
                background = palette.background,
                onBackground = palette.onBackground,
                surface = palette.surface,
                onSurface = palette.onSurface,
                surfaceVariant = palette.surfaceVariant,
                onSurfaceVariant = palette.onSurfaceVariant,
                outline = palette.outline,
                outlineVariant = palette.outlineVariant,
                error = palette.error,
                onError = palette.onError
            )
        } else {
            lightColorScheme(
                primary = palette.primary,
                onPrimary = palette.onPrimary,
                secondary = palette.secondary,
                onSecondary = palette.onSecondary,
                tertiary = palette.tertiary,
                onTertiary = palette.onTertiary,
                background = palette.background,
                onBackground = palette.onBackground,
                surface = palette.surface,
                onSurface = palette.onSurface,
                surfaceVariant = palette.surfaceVariant,
                onSurfaceVariant = palette.onSurfaceVariant,
                outline = palette.outline,
                outlineVariant = palette.outlineVariant,
                error = palette.error,
                onError = palette.onError
            )
        }
    }

    CompositionLocalProvider(LocalCoinMonitorColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CoinMonitorTypography,
            content = content
        )
    }
}

object CoinMonitorThemeTokens {
    val colors: CoinMonitorColors
        @Composable
        @ReadOnlyComposable
        get() = LocalCoinMonitorColors.current
}
