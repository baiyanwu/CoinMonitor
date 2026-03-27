package io.baiyanwu.coinmonitor.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import io.baiyanwu.coinmonitor.domain.model.ThemeTemplateId

@Immutable
data class ThemePalette(
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val onSecondary: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
    val error: Color,
    val onError: Color,
    val positive: Color,
    val negative: Color,
    val overlayBackground: Color,
    val overlayBorder: Color,
    val overlayText: Color,
    val overlayMutedText: Color,
    val overlayFallbackBackground: Color,
    val overlayFallbackBorder: Color,
    val fabContainer: Color,
    val fabContent: Color,
    val chipSelectedContainer: Color,
    val chipSelectedContent: Color
)

@Immutable
data class ThemePaletteSet(
    val id: ThemeTemplateId,
    val light: ThemePalette,
    val dark: ThemePalette
)

@Immutable
data class CoinMonitorColors(
    val pageBackground: Color,
    val cardBackground: Color,
    val heroBackground: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val tertiaryText: Color,
    val accent: Color,
    val accentOnColor: Color,
    val positive: Color,
    val negative: Color,
    val divider: Color,
    val fabContainer: Color,
    val fabContent: Color,
    val chipSelectedContainer: Color,
    val chipSelectedContent: Color,
    val overlayBackground: Color,
    val overlayBorder: Color,
    val overlayText: Color,
    val overlayMutedText: Color,
    val overlayFallbackBackground: Color,
    val overlayFallbackBorder: Color
)

internal fun ThemePalette.toSemanticColors(): CoinMonitorColors {
    return CoinMonitorColors(
        pageBackground = background,
        cardBackground = surface,
        heroBackground = surfaceVariant,
        primaryText = onBackground,
        secondaryText = onSurface,
        tertiaryText = onSurfaceVariant,
        accent = primary,
        accentOnColor = onPrimary,
        positive = positive,
        negative = negative,
        divider = outline,
        fabContainer = fabContainer,
        fabContent = fabContent,
        chipSelectedContainer = chipSelectedContainer,
        chipSelectedContent = chipSelectedContent,
        overlayBackground = overlayBackground,
        overlayBorder = overlayBorder,
        overlayText = overlayText,
        overlayMutedText = overlayMutedText,
        overlayFallbackBackground = overlayFallbackBackground,
        overlayFallbackBorder = overlayFallbackBorder
    )
}

internal val DefaultMdPalette = ThemePaletteSet(
    id = ThemeTemplateId.DEFAULT_MD,
    light = ThemePalette(
        primary = Color(0xFF6750A4),
        onPrimary = Color(0xFFFFFFFF),
        secondary = Color(0xFF625B71),
        onSecondary = Color(0xFFFFFFFF),
        tertiary = Color(0xFF7D5260),
        onTertiary = Color(0xFFFFFFFF),
        background = Color(0xFFFFFBFE),
        onBackground = Color(0xFF1C1B1F),
        surface = Color(0xFFF7F2FA),
        onSurface = Color(0xFF1C1B1F),
        surfaceVariant = Color(0xFFEDE7F6),
        onSurfaceVariant = Color(0xFF49454F),
        outline = Color(0xFFCAC4D0),
        outlineVariant = Color(0xFFE7E0EC),
        error = Color(0xFFB3261E),
        onError = Color(0xFFFFFFFF),
        positive = Color(0xFF16A34A),
        negative = Color(0xFFDC2626),
        overlayBackground = Color(0xB3E7E0EC),
        overlayBorder = Color(0x806750A4),
        overlayText = Color(0xFF1C1B1F),
        overlayMutedText = Color(0xFF625B71),
        overlayFallbackBackground = Color(0xFFE7E0EC),
        overlayFallbackBorder = Color(0xFFB0A7C0),
        fabContainer = Color(0xFFE9DDFF),
        fabContent = Color(0xFF381E72),
        chipSelectedContainer = Color(0xFFE9DDFF),
        chipSelectedContent = Color(0xFF381E72)
    ),
    dark = ThemePalette(
        primary = Color(0xFFD0BCFF),
        onPrimary = Color(0xFF381E72),
        secondary = Color(0xFFCCC2DC),
        onSecondary = Color(0xFF332D41),
        tertiary = Color(0xFFEFB8C8),
        onTertiary = Color(0xFF492532),
        background = Color(0xFF131318),
        onBackground = Color(0xFFE6E0E9),
        surface = Color(0xFF211F26),
        onSurface = Color(0xFFE6E0E9),
        surfaceVariant = Color(0xFF2B2930),
        onSurfaceVariant = Color(0xFFCAC4D0),
        outline = Color(0xFF938F99),
        outlineVariant = Color(0xFF49454F),
        error = Color(0xFFF2B8B5),
        onError = Color(0xFF601410),
        positive = Color(0xFF4ADE80),
        negative = Color(0xFFF87171),
        overlayBackground = Color(0xB3211F26),
        overlayBorder = Color(0x80938F99),
        overlayText = Color(0xFFE6E0E9),
        overlayMutedText = Color(0xFFCAC4D0),
        overlayFallbackBackground = Color(0x332B2930),
        overlayFallbackBorder = Color(0x80938F99),
        fabContainer = Color(0xFFD0BCFF),
        fabContent = Color(0xFF381E72),
        chipSelectedContainer = Color(0xFFD0BCFF),
        chipSelectedContent = Color(0xFF381E72)
    )
)

val LocalCoinMonitorColors = staticCompositionLocalOf<CoinMonitorColors> {
    DefaultMdPalette.dark.toSemanticColors()
}
