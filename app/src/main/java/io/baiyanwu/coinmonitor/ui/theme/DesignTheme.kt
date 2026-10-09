package io.baiyanwu.coinmonitor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

/** 只包裹已迁移的区域，退出后自动恢复父级主题；沿用 App 的外观模式。 */
@Composable
fun CoinMonitorDesignTheme(content: @Composable () -> Unit) {
    val design = CoinMonitorThemeTokens.design
    val colors = design.colors
    val colorScheme = remember(design) {
        (if (design.darkTheme) darkColorScheme() else lightColorScheme()).copy(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            primaryContainer = colors.selectedContainer,
            onPrimaryContainer = colors.selectedContent,
            inversePrimary = if (design.darkTheme) LightDesignColors.accent else DarkDesignColors.accent,
            secondary = colors.accent,
            onSecondary = colors.onAccent,
            secondaryContainer = colors.selectedContainer,
            onSecondaryContainer = colors.selectedContent,
            tertiary = colors.positive,
            onTertiary = colors.surface,
            tertiaryContainer = colors.surfaceMuted,
            onTertiaryContainer = colors.primaryText,
            background = colors.pageBackground,
            onBackground = colors.primaryText,
            surface = colors.surface,
            onSurface = colors.primaryText,
            surfaceVariant = colors.surfaceMuted,
            onSurfaceVariant = colors.secondaryText,
            surfaceTint = colors.accent,
            inverseSurface = if (design.darkTheme) LightDesignColors.surface else DarkDesignColors.surface,
            inverseOnSurface = if (design.darkTheme) LightDesignColors.primaryText else DarkDesignColors.primaryText,
            error = colors.error,
            onError = colors.onError,
            errorContainer = colors.surfaceMuted,
            onErrorContainer = colors.error,
            outline = colors.divider,
            outlineVariant = colors.divider,
            scrim = Color.Black,
            surfaceBright = colors.surface,
            surfaceDim = colors.pageBackground,
            surfaceContainer = colors.surface,
            surfaceContainerHigh = colors.surfaceMuted,
            surfaceContainerHighest = colors.surfaceMuted,
            surfaceContainerLow = colors.surface,
            surfaceContainerLowest = colors.pageBackground
        )
    }
    val semanticColors = remember(colors) { colors.toSemanticColors() }
    val typography = CoinMonitorTypography.copy(
        headlineMedium = design.typography.pageTitle,
        titleLarge = design.typography.pageTitle,
        titleMedium = design.typography.rowTitle,
        titleSmall = design.typography.rowTitle,
        bodyLarge = design.typography.rowTitle.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
        bodyMedium = design.typography.rowTitle.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
        bodySmall = design.typography.rowSubtitle,
        labelLarge = design.typography.label,
        labelMedium = design.typography.caption,
        labelSmall = design.typography.tabLabel
    )
    CompositionLocalProvider(LocalCoinMonitorColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = Shapes(
                small = design.shapes.pill,
                medium = design.shapes.card,
                large = design.shapes.card,
                extraLarge = design.shapes.bottomBar
            ),
            content = content
        )
    }
}

/** Material 与既有语义入口使用同一份新配色，转换仅在局部主题内生效。 */
internal fun CoinMonitorDesignColors.toSemanticColors() = CoinMonitorColors(
    pageBackground = pageBackground,
    cardBackground = surface,
    heroBackground = surfaceMuted,
    primaryText = primaryText,
    secondaryText = secondaryText,
    tertiaryText = tertiaryText,
    accent = accent,
    accentOnColor = onAccent,
    positive = positive,
    negative = negative,
    positiveMuted = positive.copy(alpha = 0.55f),
    negativeMuted = negative.copy(alpha = 0.55f),
    divider = divider,
    fabContainer = selectedContainer,
    fabContent = selectedContent,
    chipSelectedContainer = selectedContainer,
    chipSelectedContent = selectedContent,
    overlayBackground = surface,
    overlayBorder = divider,
    overlayText = primaryText,
    overlayMutedText = secondaryText,
    overlayFallbackBackground = surfaceMuted,
    overlayFallbackBorder = divider
)
