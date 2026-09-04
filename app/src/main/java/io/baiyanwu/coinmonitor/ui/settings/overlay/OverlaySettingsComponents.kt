package io.baiyanwu.coinmonitor.ui.settings.overlay

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorComponentDefaults
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens
import java.util.Locale

@Composable
internal fun OverlaySettingsCard(content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CoinMonitorComponentDefaults.elevatedCardColors()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
internal fun OverlaySettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    horizontalPadding: Dp = 14.dp,
    verticalPadding: Dp = 12.dp
) {
    val colors = CoinMonitorThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondaryText
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CoinMonitorComponentDefaults.switchColors()
        )
    }
}

@Composable
internal fun OverlaySliderEndpoints(startLabel: String, endLabel: String) {
    val colors = CoinMonitorThemeTokens.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(startLabel, style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
        Text(endLabel, style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
    }
}

internal fun overlayOpacityToProgress(
    opacity: Float,
    minimumOpacity: Float,
    maximumOpacity: Float
): Float {
    return ((opacity - minimumOpacity) / (maximumOpacity - minimumOpacity))
        .coerceIn(0f, 1f)
}

internal fun overlayProgressToOpacity(
    progress: Float,
    minimumOpacity: Float,
    maximumOpacity: Float
): Float {
    return minimumOpacity +
        (maximumOpacity - minimumOpacity) *
        progress.coerceIn(0f, 1f)
}

internal fun overlayFontScaleToSizeSp(
    fontScale: Float,
    minimumScale: Float,
    maximumScale: Float
): Float {
    return (DEFAULT_OVERLAY_FONT_SIZE_SP * fontScale).coerceIn(
        DEFAULT_OVERLAY_FONT_SIZE_SP * minimumScale,
        DEFAULT_OVERLAY_FONT_SIZE_SP * maximumScale
    )
}

internal fun overlayFontSizeSpToScale(
    fontSizeSp: Float,
    minimumScale: Float,
    maximumScale: Float
): Float {
    return (fontSizeSp / DEFAULT_OVERLAY_FONT_SIZE_SP).coerceIn(
        minimumScale,
        maximumScale
    )
}

internal fun formatOverlayFontSize(fontSizeSp: Float): String {
    return String.format(Locale.US, "%.1f", fontSizeSp)
}

internal const val MIN_OVERLAY_FONT_SIZE_SP = 8.5f
internal const val MAX_OVERLAY_FONT_SIZE_SP = 13.5f
private const val DEFAULT_OVERLAY_FONT_SIZE_SP = 10f
