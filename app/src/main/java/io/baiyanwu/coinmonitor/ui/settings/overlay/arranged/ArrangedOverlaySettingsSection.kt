package io.baiyanwu.coinmonitor.ui.settings.overlay.arranged

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
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
import io.baiyanwu.coinmonitor.domain.model.ArrangedEdgeDisplayMode
import io.baiyanwu.coinmonitor.domain.model.ArrangedOverlaySettings
import io.baiyanwu.coinmonitor.domain.model.OverlayLeadingDisplayMode
import io.baiyanwu.coinmonitor.domain.model.OverlaySettings
import io.baiyanwu.coinmonitor.ui.settings.overlay.MAX_OVERLAY_FONT_SIZE_SP
import io.baiyanwu.coinmonitor.ui.settings.overlay.MIN_OVERLAY_FONT_SIZE_SP
import io.baiyanwu.coinmonitor.ui.settings.overlay.OverlaySettingSwitchRow
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
internal fun ArrangedOverlaySettingsSection(
    settings: ArrangedOverlaySettings,
    onOpacityChange: (Float) -> Unit,
    onMaxCountChange: (Int) -> Unit,
    onLeadingDisplayModeChange: (OverlayLeadingDisplayMode) -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onSnapToEdgeChange: (Boolean) -> Unit,
    onEdgeDisplayModeChange: (ArrangedEdgeDisplayMode) -> Unit,
    onEdgeTabOpacityChange: (Float) -> Unit,
    onEdgeAutoCollapseSecondsChange: (Int) -> Unit
) {
    val opacityProgress = overlayOpacityToProgress(
        opacity = settings.opacity,
        minimumOpacity = ArrangedOverlaySettings.MIN_OPACITY,
        maximumOpacity = ArrangedOverlaySettings.MAX_OPACITY
    )
    val opacityPercent = (opacityProgress * 100).roundToInt()
    val fontSizeSp = overlayFontScaleToSizeSp(
        fontScale = settings.fontScale,
        minimumScale = ArrangedOverlaySettings.MIN_FONT_SCALE,
        maximumScale = ArrangedOverlaySettings.MAX_FONT_SCALE
    )

    OverlaySettingsCard {
        Text(
            text = stringResource(R.string.overlay_arranged_settings),
            style = MaterialTheme.typography.titleMedium
        )
        Text(stringResource(R.string.overlay_leading_display), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = settings.leadingDisplayMode == OverlayLeadingDisplayMode.ICON,
                onClick = { onLeadingDisplayModeChange(OverlayLeadingDisplayMode.ICON) },
                label = { Text(stringResource(R.string.overlay_display_icon)) },
                colors = CoinMonitorComponentDefaults.filterChipColors()
            )
            FilterChip(
                selected = settings.leadingDisplayMode == OverlayLeadingDisplayMode.PAIR_NAME,
                onClick = { onLeadingDisplayModeChange(OverlayLeadingDisplayMode.PAIR_NAME) },
                label = { Text(stringResource(R.string.overlay_display_pair_name)) },
                colors = CoinMonitorComponentDefaults.filterChipColors()
            )
        }

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
                        minimumOpacity = ArrangedOverlaySettings.MIN_OPACITY,
                        maximumOpacity = ArrangedOverlaySettings.MAX_OPACITY
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
                        minimumScale = ArrangedOverlaySettings.MIN_FONT_SCALE,
                        maximumScale = ArrangedOverlaySettings.MAX_FONT_SCALE
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
                R.plurals.overlay_max_items,
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

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OverlaySettingSwitchRow(
                title = stringResource(R.string.overlay_snap_to_edge),
                subtitle = stringResource(R.string.overlay_snap_to_edge_hint),
                checked = settings.snapToEdge,
                horizontalPadding = 0.dp,
                verticalPadding = 0.dp,
                onCheckedChange = onSnapToEdgeChange
            )
            ArrangedEdgeModeSelector(
                selectedMode = settings.edgeDisplayMode,
                edgeTabOpacity = settings.edgeTabOpacity,
                edgeAutoCollapseSeconds = settings.edgeAutoCollapseSeconds,
                enabled = settings.snapToEdge,
                onModeSelected = onEdgeDisplayModeChange,
                onEdgeTabOpacityChange = onEdgeTabOpacityChange,
                onEdgeAutoCollapseSecondsChange = onEdgeAutoCollapseSecondsChange
            )
        }
    }
}

@Composable
private fun ArrangedEdgeModeSelector(
    selectedMode: ArrangedEdgeDisplayMode,
    edgeTabOpacity: Float,
    edgeAutoCollapseSeconds: Int,
    enabled: Boolean,
    onModeSelected: (ArrangedEdgeDisplayMode) -> Unit,
    onEdgeTabOpacityChange: (Float) -> Unit,
    onEdgeAutoCollapseSecondsChange: (Int) -> Unit
) {
    val options = listOf(
        ArrangedEdgeDisplayMode.DOCKED to stringResource(R.string.overlay_edge_display_docked),
        ArrangedEdgeDisplayMode.HIDDEN_TAB to stringResource(R.string.overlay_edge_display_hidden_tab)
    )
    val colors = CoinMonitorThemeTokens.colors
    val normalizedOpacity = edgeTabOpacity.coerceIn(
        ArrangedOverlaySettings.MIN_EDGE_TAB_OPACITY,
        ArrangedOverlaySettings.MAX_EDGE_TAB_OPACITY
    )
    val normalizedSeconds = edgeAutoCollapseSeconds.coerceIn(
        ArrangedOverlaySettings.MIN_EDGE_AUTO_COLLAPSE_SECONDS,
        ArrangedOverlaySettings.MAX_EDGE_AUTO_COLLAPSE_SECONDS
    )
    val modeHint = if (selectedMode == ArrangedEdgeDisplayMode.HIDDEN_TAB) {
        stringResource(R.string.overlay_edge_display_hidden_tab_hint, normalizedSeconds)
    } else {
        stringResource(R.string.overlay_edge_display_docked_hint)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (mode, label) ->
                SegmentedButton(
                    selected = selectedMode == mode,
                    onClick = { onModeSelected(mode) },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    modifier = Modifier.weight(1f),
                    label = { Text(label) }
                )
            }
        }
        Text(modeHint, style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
        if (selectedMode == ArrangedEdgeDisplayMode.HIDDEN_TAB) {
            Text(
                stringResource(
                    R.string.overlay_edge_tab_opacity_format,
                    (normalizedOpacity * 100).roundToInt()
                ),
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) colors.primaryText else colors.secondaryText
            )
            Slider(
                value = normalizedOpacity,
                onValueChange = onEdgeTabOpacityChange,
                enabled = enabled,
                valueRange = ArrangedOverlaySettings.MIN_EDGE_TAB_OPACITY..
                    ArrangedOverlaySettings.MAX_EDGE_TAB_OPACITY,
                colors = CoinMonitorComponentDefaults.sliderColors()
            )
            OverlaySliderEndpoints(
                stringResource(R.string.overlay_edge_tab_opacity_min),
                stringResource(R.string.overlay_edge_tab_opacity_max)
            )
            Text(
                stringResource(R.string.overlay_edge_auto_collapse_delay_format, normalizedSeconds),
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) colors.primaryText else colors.secondaryText
            )
            Slider(
                value = normalizedSeconds.toFloat(),
                onValueChange = { onEdgeAutoCollapseSecondsChange(it.roundToInt()) },
                enabled = enabled,
                valueRange = ArrangedOverlaySettings.MIN_EDGE_AUTO_COLLAPSE_SECONDS.toFloat()..
                    ArrangedOverlaySettings.MAX_EDGE_AUTO_COLLAPSE_SECONDS.toFloat(),
                steps = 3,
                colors = CoinMonitorComponentDefaults.sliderColors()
            )
            OverlaySliderEndpoints(
                stringResource(R.string.overlay_edge_auto_collapse_delay_min),
                stringResource(R.string.overlay_edge_auto_collapse_delay_max)
            )
        }
    }
}
