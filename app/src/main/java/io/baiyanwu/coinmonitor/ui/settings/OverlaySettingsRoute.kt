package io.baiyanwu.coinmonitor.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.domain.model.AppPreferences
import io.baiyanwu.coinmonitor.domain.model.OverlayLeadingDisplayMode
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorComponentDefaults
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens
import io.baiyanwu.coinmonitor.R
import kotlinx.coroutines.launch
import kotlin.collections.forEach
import kotlin.math.roundToInt

@Composable
fun OverlaySettingsRoute(
    container: AppContainer,
    overlayPermissionGranted: Boolean,
    notificationPermissionGranted: Boolean,
    onBack: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit
) {
    val viewModel: OverlaySettingsViewModel = viewModel(factory = OverlaySettingsViewModel.factory(container))
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    OverlaySettingsScreen(
        state = state,
        overlayPermissionGranted = overlayPermissionGranted,
        notificationPermissionGranted = notificationPermissionGranted,
        onBack = onBack,
        onRequestOverlayPermission = onRequestOverlayPermission,
        onRequestNotificationPermission = onRequestNotificationPermission,
        onEnabledChange = { enabled ->
            scope.launch {
                viewModel.setEnabled(enabled)
                if (enabled) {
                    if (!notificationPermissionGranted) {
                        onRequestNotificationPermission()
                    }
                    if (!overlayPermissionGranted) {
                        onRequestOverlayPermission()
                    } else {
                        onStartOverlay()
                    }
                } else {
                    onStopOverlay()
                }
            }
        },
        onLockedChange = viewModel::setLocked,
        onOpacityChange = viewModel::setOpacity,
        onMaxCountChange = viewModel::setMaxCount,
        onRefreshIntervalChange = viewModel::setRefreshIntervalSeconds,
        onLeadingDisplayModeChange = viewModel::setLeadingDisplayMode,
        onToggleItem = viewModel::toggleItem
    )
}

@Composable
private fun OverlaySettingsScreen(
    state: OverlaySettingsUiState,
    overlayPermissionGranted: Boolean,
    notificationPermissionGranted: Boolean,
    onBack: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onLockedChange: (Boolean) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onMaxCountChange: (Int) -> Unit,
    onRefreshIntervalChange: (Int) -> Unit,
    onLeadingDisplayModeChange: (OverlayLeadingDisplayMode) -> Unit,
    onToggleItem: (String) -> Unit
) {
    val opacityProgress = opacityToProgress(state.settings.opacity)
    val opacityPercent = (opacityProgress * 100).roundToInt()
    var refreshIntervalSliderValue by remember(state.refreshIntervalSeconds) {
        mutableFloatStateOf(state.refreshIntervalSeconds.toFloat())
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = stringResource(R.string.overlay_settings_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 34.dp)
                )
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .offset(x = (-10).dp)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        item {
            ElevatedCard(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CoinMonitorComponentDefaults.elevatedCardColors()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        SettingSwitchRow(
                            title = stringResource(R.string.overlay_enable),
                            checked = state.settings.enabled,
                            horizontalPadding = 0.dp,
                            verticalPadding = 0.dp,
                            onCheckedChange = onEnabledChange
                        )
                        SettingSwitchRow(
                            title = stringResource(R.string.overlay_lock_drag),
                            checked = state.settings.locked,
                            horizontalPadding = 0.dp,
                            verticalPadding = 0.dp,
                            onCheckedChange = onLockedChange
                        )
                    }

                    Text(stringResource(R.string.overlay_leading_display), style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.settings.leadingDisplayMode == OverlayLeadingDisplayMode.ICON,
                            onClick = { onLeadingDisplayModeChange(OverlayLeadingDisplayMode.ICON) },
                            label = { Text(stringResource(R.string.overlay_display_icon)) },
                            colors = CoinMonitorComponentDefaults.filterChipColors()
                        )
                        FilterChip(
                            selected = state.settings.leadingDisplayMode == OverlayLeadingDisplayMode.PAIR_NAME,
                            onClick = { onLeadingDisplayModeChange(OverlayLeadingDisplayMode.PAIR_NAME) },
                            label = { Text(stringResource(R.string.overlay_display_pair_name)) },
                            colors = CoinMonitorComponentDefaults.filterChipColors()
                        )
                    }

                    Text(
                        text = stringResource(R.string.overlay_opacity_format, opacityPercent),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Slider(
                        value = opacityProgress,
                        onValueChange = { onOpacityChange(progressToOpacity(it)) },
                        valueRange = 0f..1f,
                        colors = CoinMonitorComponentDefaults.sliderColors()
                    )

                    Text(
                        text = stringResource(R.string.overlay_max_items_format, state.settings.maxItems),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Slider(
                        value = state.settings.maxItems.toFloat(),
                        onValueChange = { onMaxCountChange(it.roundToInt()) },
                        valueRange = 1f..10f,
                        steps = 8,
                        colors = CoinMonitorComponentDefaults.sliderColors()
                    )
                    SliderEndpoints(
                        startLabel = stringResource(R.string.common_min_count),
                        endLabel = stringResource(R.string.common_max_count)
                    )

                    Text(
                        text = stringResource(
                            R.string.settings_refresh_interval_value,
                            refreshIntervalSliderValue.roundToInt()
                        ),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Slider(
                        value = refreshIntervalSliderValue,
                        onValueChange = { value ->
                            refreshIntervalSliderValue = value.roundToInt().toFloat()
                        },
                        onValueChangeFinished = {
                            onRefreshIntervalChange(refreshIntervalSliderValue.roundToInt())
                        },
                        valueRange = AppPreferences.MIN_REFRESH_INTERVAL_SECONDS.toFloat()..
                            AppPreferences.MAX_REFRESH_INTERVAL_SECONDS.toFloat(),
                        steps = AppPreferences.MAX_REFRESH_INTERVAL_SECONDS -
                            AppPreferences.MIN_REFRESH_INTERVAL_SECONDS - 1,
                        colors = CoinMonitorComponentDefaults.sliderColors()
                    )
                    SliderEndpoints(
                        startLabel = stringResource(
                            R.string.settings_refresh_interval_min_label,
                            AppPreferences.MIN_REFRESH_INTERVAL_SECONDS
                        ),
                        endLabel = stringResource(
                            R.string.settings_refresh_interval_max_label,
                            AppPreferences.MAX_REFRESH_INTERVAL_SECONDS
                        )
                    )
                }
            }
        }

        if (!overlayPermissionGranted) {
            item {
                MessageCard(
                    message = stringResource(R.string.overlay_permission_message),
                    buttonLabel = stringResource(R.string.overlay_permission_action),
                    onClick = onRequestOverlayPermission
                )
            }
        }

        if (!notificationPermissionGranted) {
            item {
                MessageCard(
                    message = stringResource(R.string.notification_permission_message),
                    buttonLabel = stringResource(R.string.notification_permission_action),
                    onClick = onRequestNotificationPermission
                )
            }
        }

        item {
            ElevatedCard(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CoinMonitorComponentDefaults.elevatedCardColors()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.overlay_select_items),
                        style = MaterialTheme.typography.titleSmall
                    )

                    if (state.items.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 104.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.overlay_empty_state),
                                style = MaterialTheme.typography.bodyMedium,
                                color = CoinMonitorThemeTokens.colors.secondaryText
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            state.items.forEach { item ->
                                SettingSwitchRow(
                                    title = item.symbol,
                                    horizontalPadding = 0.dp,
                                    checked = item.overlaySelected,
                                    onCheckedChange = { onToggleItem(item.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageCard(
    message: String,
    buttonLabel: String,
    onClick: () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CoinMonitorComponentDefaults.elevatedCardColors()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(message)
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                colors = CoinMonitorComponentDefaults.primaryButtonColors()
            ) {
                Text(buttonLabel)
            }
        }
    }
}

@Composable
private fun SliderEndpoints(
    startLabel: String,
    endLabel: String
) {
    val colors = CoinMonitorThemeTokens.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = startLabel, style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
        Text(text = endLabel, style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
    }
}

@Composable
private fun SettingSwitchRow(
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

private fun opacityToProgress(opacity: Float): Float {
    val minOpacity = 0.16f
    val maxOpacity = 0.72f
    return ((opacity - minOpacity) / (maxOpacity - minOpacity)).coerceIn(0f, 1f)
}

private fun progressToOpacity(progress: Float): Float {
    val minOpacity = 0.16f
    val maxOpacity = 0.72f
    return minOpacity + (maxOpacity - minOpacity) * progress.coerceIn(0f, 1f)
}
