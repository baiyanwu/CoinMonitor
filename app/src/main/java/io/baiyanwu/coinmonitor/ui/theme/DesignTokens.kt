package io.baiyanwu.coinmonitor.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 新设计的语义配色。品牌装饰色与小字号可读交互色分开。 */
@Immutable
data class CoinMonitorDesignColors(
    val brand: Color,
    val logoRing: Color,
    val accent: Color,
    val onAccent: Color,
    val pageBackground: Color,
    val surface: Color,
    val surfaceMuted: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val tertiaryText: Color,
    val selectedContainer: Color,
    val selectedContent: Color,
    val divider: Color,
    val positive: Color,
    val negative: Color,
    val positiveMuted: Color,
    val negativeMuted: Color,
    val error: Color,
    val onError: Color,
    val disabledContainer: Color,
    val disabledContent: Color,
    val sourceGmgn: Color,
    val sourcePump: Color,
    val sourceOkx: Color
)

internal val LightDesignColors = CoinMonitorDesignColors(
    brand = Color(0xFF8E88F8),
    logoRing = Color(0xFF9891BE),
    accent = Color(0xFF6863AA),
    onAccent = Color.White,
    pageBackground = Color(0xFFF5F6F9),
    surface = Color.White,
    surfaceMuted = Color(0xFFEFF0F6),
    primaryText = Color(0xFF282C3E),
    secondaryText = Color(0xFF666D81),
    tertiaryText = Color(0xFF7C8297),
    selectedContainer = Color(0xFFECEAF8),
    selectedContent = Color(0xFF6661A7),
    divider = Color(0xFFE5E7EF),
    positive = DefaultMdPalette.light.positive,
    negative = DefaultMdPalette.light.negative,
    positiveMuted = DefaultMdPalette.light.positiveMuted,
    negativeMuted = DefaultMdPalette.light.negativeMuted,
    error = Color(0xFFA83E56),
    onError = Color.White,
    disabledContainer = Color(0xFFEFF0F6),
    disabledContent = Color(0xFF9298AA),
    sourceGmgn = Color(0xFF8E88F8),
    sourcePump = Color(0xFF7297EF),
    sourceOkx = Color(0xFF4AA58F)
)

internal val DarkDesignColors = CoinMonitorDesignColors(
    brand = Color(0xFF8E88F8),
    logoRing = Color(0xFFECEAF8),
    accent = Color(0xFFB8B3FF),
    onAccent = Color(0xFF28243F),
    pageBackground = Color(0xFF171B27),
    surface = Color(0xFF242A3B),
    surfaceMuted = Color(0xFF2D3448),
    primaryText = Color(0xFFEBEDF7),
    secondaryText = Color(0xFFB2B8CD),
    tertiaryText = Color(0xFF9099B3),
    selectedContainer = Color(0xFF39354F),
    selectedContent = Color(0xFFC7C2FF),
    divider = Color(0xFF3A4259),
    positive = DefaultMdPalette.dark.positive,
    negative = DefaultMdPalette.dark.negative,
    positiveMuted = DefaultMdPalette.dark.positiveMuted,
    negativeMuted = DefaultMdPalette.dark.negativeMuted,
    error = Color(0xFFF0A8B7),
    onError = Color(0xFF4B1728),
    disabledContainer = Color(0xFF2D3448),
    disabledContent = Color(0xFF77819D),
    sourceGmgn = Color(0xFFB8B3FF),
    sourcePump = Color(0xFF91B2FF),
    sourceOkx = Color(0xFF71BAA3)
)

object CoinMonitorDesignSpacing {
    val tiny = 2.dp
    val small = 4.dp
    val inline = 8.dp
    val group = 10.dp
    val pageHorizontal = 14.dp
    val cardPadding = 12.dp
    val rowVertical = 6.dp
}

object CoinMonitorDesignSizes {
    val coinIcon = 28.dp
    val cardCornerRadius = 20.dp
    val featuredCoinIcon = 36.dp
    val brandMark = 28.dp
    val actionIcon = 18.dp
    val actionContainer = 32.dp
    val compactActionMinimumHeight = 40.dp
    val minimumTouchTarget = 48.dp
    val pillMinimumHeight = 32.dp
    val listRowMinimumHeight = 52.dp
    val settingsRowMinimumHeight = 52.dp
    val bottomTabMinimumHeight = 56.dp
    val tabIcon = 22.dp
    val tabIndicatorWidth = 56.dp
    val tabIndicatorHeight = 28.dp
    val selectionDot = 3.dp
    val pickerMaximumHeight = 360.dp
    val sparklineWidth = 52.dp
    val sparklineHeight = 24.dp
    val featuredSparklineHeight = 40.dp
    val homeModeSelectorWidth = 220.dp
    val trendingValueColumnWidth = 110.dp
    val trendingEmptyBodyMinimumHeight = 96.dp
    val walletValueColumnWidth = 110.dp
    val walletStackedValueBreakpoint = 300.dp
    val walletEmptyBodyMinimumHeight = 96.dp
    val segmentedIndicatorMinimumHeight = 40.dp
    val chainBadge = 12.dp
    val sourceDot = 5.dp
    val sourceSummaryDot = 8.dp
    val rankWidth = 22.dp
    val inlineActionIcon = 14.dp
    val selectionMark = 12.dp
    val dividerThickness = 0.5.dp
    val headerMinimumHeight = 48.dp
}

object CoinMonitorDesignShapes {
    val card = RoundedCornerShape(CoinMonitorDesignSizes.cardCornerRadius)
    val menu = RoundedCornerShape(14.dp)
    val bottomBar = RoundedCornerShape(28.dp)
    val pill = RoundedCornerShape(50)
}

object CoinMonitorDesignTypography {
    val pageTitle = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
    val rowTitle = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
    val rowSubtitle = TextStyle(fontSize = 12.sp, lineHeight = 17.sp)
    val value = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
    val featuredValue = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
    val caption = TextStyle(fontSize = 11.sp, lineHeight = 16.sp)
    val label = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
    val tabLabel = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
    val compactRowTitle = TextStyle(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)
    val compactRowSubtitle = TextStyle(fontSize = 11.sp, lineHeight = 14.sp)
    val balance = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold)
}

object CoinMonitorDesignMotion {
    const val selectionDurationMillis = 160
    const val pressDurationMillis = 120
    const val pageSwitchDurationMillis = 280
}

@Immutable
data class CoinMonitorDesignTokens(
    val colors: CoinMonitorDesignColors,
    val darkTheme: Boolean
) {
    val spacing get() = CoinMonitorDesignSpacing
    val sizes get() = CoinMonitorDesignSizes
    val shapes get() = CoinMonitorDesignShapes
    val typography get() = CoinMonitorDesignTypography
    val motion get() = CoinMonitorDesignMotion
}

internal fun designTokens(darkTheme: Boolean) = CoinMonitorDesignTokens(
    colors = if (darkTheme) DarkDesignColors else LightDesignColors,
    darkTheme = darkTheme
)

internal val LocalCoinMonitorDesign = staticCompositionLocalOf { designTokens(darkTheme = false) }
