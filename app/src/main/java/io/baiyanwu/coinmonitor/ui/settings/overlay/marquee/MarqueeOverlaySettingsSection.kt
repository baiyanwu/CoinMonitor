package io.baiyanwu.coinmonitor.ui.settings.overlay.marquee

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.domain.model.MarqueeOverlaySettings
import io.baiyanwu.coinmonitor.domain.model.MarqueeSpeed
import io.baiyanwu.coinmonitor.domain.model.OverlaySettings
import io.baiyanwu.coinmonitor.ui.settings.overlay.MAX_OVERLAY_FONT_SIZE_SP
import io.baiyanwu.coinmonitor.ui.settings.overlay.MIN_OVERLAY_FONT_SIZE_SP
import io.baiyanwu.coinmonitor.ui.settings.overlay.OverlaySettingsCard
import io.baiyanwu.coinmonitor.ui.settings.overlay.OverlaySliderEndpoints
import io.baiyanwu.coinmonitor.ui.settings.overlay.formatOverlayFontSize
import io.baiyanwu.coinmonitor.ui.settings.overlay.overlayFontScaleToSizeSp
import io.baiyanwu.coinmonitor.ui.settings.overlay.overlayFontSizeSpToScale
import io.baiyanwu.coinmonitor.ui.settings.overlay.overlayOpacityToProgress
import io.baiyanwu.coinmonitor.ui.settings.overlay.overlayProgressToOpacity
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorComponentDefaults
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens
import kotlin.math.roundToInt

@Composable
internal fun MarqueeOverlaySettingsSection(
    settings: MarqueeOverlaySettings,
    onOpacityChange: (Float) -> Unit,
    onMaxCountChange: (Int) -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onSpeedChange: (MarqueeSpeed) -> Unit
) {
    val opacityProgress = overlayOpacityToProgress(
        opacity = settings.opacity,
        minimumOpacity = MarqueeOverlaySettings.MIN_OPACITY,
        maximumOpacity = MarqueeOverlaySettings.MAX_OPACITY
    )
    val opacityPercent = (opacityProgress * 100).roundToInt()
    val fontSizeSp = overlayFontScaleToSizeSp(
        fontScale = settings.fontScale,
        minimumScale = MarqueeOverlaySettings.MIN_FONT_SCALE,
        maximumScale = MarqueeOverlaySettings.MAX_FONT_SCALE
    )
    val colors = CoinMonitorThemeTokens.colors

    OverlaySettingsCard {
        Text(
            text = stringResource(R.string.overlay_marquee_settings),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(R.string.overlay_marquee_settings_hint),
            style = MaterialTheme.typography.bodySmall,
            color = colors.secondaryText
        )

        Text(
            stringResource(R.string.overlay_opacity_format, opacityPercent),
            style = MaterialTheme.typography.titleSmall
        )
        Slider(
            value = opacityProgress,
            onValueChange = {
                onOpacityChange(
                    overlayProgressToOpacity(
                        progress = it,
                        minimumOpacity = MarqueeOverlaySettings.MIN_OPACITY,
                        maximumOpacity = MarqueeOverlaySettings.MAX_OPACITY
                    )
                )
            },
            valueRange = 0f..1f,
            colors = CoinMonitorComponentDefaults.sliderColors()
        )

        Text(
            stringResource(
                R.string.overlay_font_size_format,
                formatOverlayFontSize(fontSizeSp)
            ),
            style = MaterialTheme.typography.titleSmall
        )
        Slider(
            value = fontSizeSp,
            onValueChange = {
                onFontScaleChange(
                    overlayFontSizeSpToScale(
                        fontSizeSp = it,
                        minimumScale = MarqueeOverlaySettings.MIN_FONT_SCALE,
                        maximumScale = MarqueeOverlaySettings.MAX_FONT_SCALE
                    )
                )
            },
            valueRange = MIN_OVERLAY_FONT_SIZE_SP..MAX_OVERLAY_FONT_SIZE_SP,
            steps = 9,
            colors = CoinMonitorComponentDefaults.sliderColors()
        )
        OverlaySliderEndpoints(
            startLabel = stringResource(
                R.string.overlay_font_size_endpoint,
                formatOverlayFontSize(MIN_OVERLAY_FONT_SIZE_SP)
            ),
            endLabel = stringResource(
                R.string.overlay_font_size_endpoint,
                formatOverlayFontSize(MAX_OVERLAY_FONT_SIZE_SP)
            )
        )

        Text(
            pluralStringResource(
                R.plurals.overlay_marquee_max_items,
                settings.maxItems,
                settings.maxItems
            ),
            style = MaterialTheme.typography.titleSmall
        )
        Slider(
            value = settings.maxItems.toFloat(),
            onValueChange = { onMaxCountChange(it.roundToInt()) },
            valueRange = 1f..OverlaySettings.MAX_SELECTABLE_ITEMS.toFloat(),
            steps = OverlaySettings.MAX_SELECTABLE_ITEMS - 2,
            colors = CoinMonitorComponentDefaults.sliderColors()
        )
        OverlaySliderEndpoints(
            startLabel = stringResource(R.string.common_min_count),
            endLabel = stringResource(R.string.common_max_count)
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.overlay_marquee_speed),
                style = MaterialTheme.typography.titleSmall
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                MarqueeSpeed.entries.forEachIndexed { index, speed ->
                    val label = when (speed) {
                        MarqueeSpeed.SLOW -> stringResource(R.string.overlay_marquee_speed_slow)
                        MarqueeSpeed.NORMAL -> stringResource(R.string.overlay_marquee_speed_normal)
                        MarqueeSpeed.FAST -> stringResource(R.string.overlay_marquee_speed_fast)
                    }
                    SegmentedButton(
                        selected = settings.speed == speed,
                        onClick = { onSpeedChange(speed) },
                        shape = SegmentedButtonDefaults.itemShape(index, MarqueeSpeed.entries.size),
                        modifier = Modifier.weight(1f),
                        label = { Text(label) }
                    )
                }
            }
        }
    }
}
