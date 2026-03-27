package io.baiyanwu.coinmonitor.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.domain.model.AppLanguage
import io.baiyanwu.coinmonitor.domain.model.AppPreferences
import io.baiyanwu.coinmonitor.domain.model.AppThemeMode
import io.baiyanwu.coinmonitor.domain.model.RefreshIntervalMode
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorComponentDefaults
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens
import io.baiyanwu.coinmonitor.R
import kotlin.math.roundToInt

@Composable
fun SettingsRoute(
    container: AppContainer,
    contentBottomInset: Dp = 0.dp,
    onNavigateOverlaySettings: () -> Unit
) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsScreen(
        state = state,
        contentBottomInset = contentBottomInset,
        onNavigateOverlaySettings = onNavigateOverlaySettings,
        onThemeModeChange = viewModel::setThemeMode,
        onLanguageChange = viewModel::setLanguage,
        onRefreshIntervalModeChange = viewModel::setRefreshIntervalMode,
        onRefreshIntervalChange = viewModel::setRefreshIntervalSeconds
    )
}

@Composable
private fun SettingsScreen(
    state: SettingsUiState,
    contentBottomInset: Dp,
    onNavigateOverlaySettings: () -> Unit,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onRefreshIntervalModeChange: (RefreshIntervalMode) -> Unit,
    onRefreshIntervalChange: (Int) -> Unit
) {
    val colors = CoinMonitorThemeTokens.colors
    val refreshIntervalMode = state.preferences.refreshIntervalMode
    val customModeSelected = refreshIntervalMode == RefreshIntervalMode.CUSTOM
    val thirtySecondsModeSelected = refreshIntervalMode == RefreshIntervalMode.THIRTY_SECONDS
    val oneMinuteModeSelected = refreshIntervalMode == RefreshIntervalMode.ONE_MINUTE
    var refreshIntervalSliderValue by remember(state.preferences.customRefreshIntervalSeconds) {
        mutableFloatStateOf(state.preferences.customRefreshIntervalSeconds.toFloat())
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.pageBackground)
            .padding(start = 14.dp, top = 12.dp, end = 14.dp, bottom = 12.dp + contentBottomInset),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall
            )
        }

        item {
            ElevatedCard(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CoinMonitorComponentDefaults.elevatedCardColors()
            ) {
                SettingNavigationRow(
                    icon = { Icon(Icons.Rounded.Tune, contentDescription = null) },
                    title = stringResource(R.string.overlay_settings_title),
                    onClick = onNavigateOverlaySettings
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
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Timer, contentDescription = null)
                        Text(
                            text = stringResource(R.string.settings_refresh_mode_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = customModeSelected,
                            onClick = { onRefreshIntervalModeChange(RefreshIntervalMode.CUSTOM) },
                            label = { Text(stringResource(R.string.settings_refresh_mode_custom)) },
                            colors = CoinMonitorComponentDefaults.filterChipColors()
                        )
                        FilterChip(
                            selected = thirtySecondsModeSelected,
                            onClick = { onRefreshIntervalModeChange(RefreshIntervalMode.THIRTY_SECONDS) },
                            label = { Text(stringResource(R.string.settings_refresh_mode_thirty_seconds)) },
                            colors = CoinMonitorComponentDefaults.filterChipColors()
                        )
                        FilterChip(
                            selected = oneMinuteModeSelected,
                            onClick = { onRefreshIntervalModeChange(RefreshIntervalMode.ONE_MINUTE) },
                            label = { Text(stringResource(R.string.settings_refresh_mode_one_minute)) },
                            colors = CoinMonitorComponentDefaults.filterChipColors()
                        )
                    }

                    RefreshModeSection(active = customModeSelected) {
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
                                onRefreshIntervalModeChange(RefreshIntervalMode.CUSTOM)
                                onRefreshIntervalChange(refreshIntervalSliderValue.roundToInt())
                            },
                            valueRange = AppPreferences.MIN_CUSTOM_REFRESH_INTERVAL_SECONDS.toFloat()..
                                AppPreferences.MAX_CUSTOM_REFRESH_INTERVAL_SECONDS.toFloat(),
                            steps = AppPreferences.MAX_CUSTOM_REFRESH_INTERVAL_SECONDS -
                                AppPreferences.MIN_CUSTOM_REFRESH_INTERVAL_SECONDS - 1,
                            enabled = customModeSelected,
                            colors = CoinMonitorComponentDefaults.sliderColors()
                        )
                        SliderEndpoints(
                            startLabel = stringResource(
                                R.string.settings_refresh_interval_min_label,
                                AppPreferences.MIN_CUSTOM_REFRESH_INTERVAL_SECONDS
                            ),
                            endLabel = stringResource(
                                R.string.settings_refresh_interval_max_label,
                                AppPreferences.MAX_CUSTOM_REFRESH_INTERVAL_SECONDS
                            ),
                            enabled = customModeSelected
                        )
                    }

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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.DarkMode, contentDescription = null)
                        Text(
                            text = stringResource(R.string.appearance_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.preferences.themeMode == AppThemeMode.SYSTEM,
                            onClick = { onThemeModeChange(AppThemeMode.SYSTEM) },
                            label = { Text(stringResource(R.string.theme_system)) },
                            colors = CoinMonitorComponentDefaults.filterChipColors()
                        )
                        FilterChip(
                            selected = state.preferences.themeMode == AppThemeMode.LIGHT,
                            onClick = { onThemeModeChange(AppThemeMode.LIGHT) },
                            label = { Text(stringResource(R.string.theme_light)) },
                            colors = CoinMonitorComponentDefaults.filterChipColors()
                        )
                        FilterChip(
                            selected = state.preferences.themeMode == AppThemeMode.DARK,
                            onClick = { onThemeModeChange(AppThemeMode.DARK) },
                            label = { Text(stringResource(R.string.theme_dark)) },
                            colors = CoinMonitorComponentDefaults.filterChipColors()
                        )
                    }
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Language, contentDescription = null)
                        Text(
                            text = stringResource(R.string.language_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.preferences.language == AppLanguage.SYSTEM,
                            onClick = { onLanguageChange(AppLanguage.SYSTEM) },
                            label = { Text(stringResource(R.string.language_system)) },
                            colors = CoinMonitorComponentDefaults.filterChipColors()
                        )
                        FilterChip(
                            selected = state.preferences.language == AppLanguage.CHINESE_SIMPLIFIED,
                            onClick = { onLanguageChange(AppLanguage.CHINESE_SIMPLIFIED) },
                            label = { Text(stringResource(R.string.language_chinese)) },
                            colors = CoinMonitorComponentDefaults.filterChipColors()
                        )
                        FilterChip(
                            selected = state.preferences.language == AppLanguage.ENGLISH,
                            onClick = { onLanguageChange(AppLanguage.ENGLISH) },
                            label = { Text(stringResource(R.string.language_english)) },
                            colors = CoinMonitorComponentDefaults.filterChipColors()
                        )
                    }
                }
            }
        }

    }
}

@Composable
private fun SettingNavigationRow(
    icon: @Composable () -> Unit,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
        }
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
    }
}

@Composable
private fun SliderEndpoints(
    startLabel: String,
    endLabel: String,
    enabled: Boolean = true
) {
    val colors = CoinMonitorThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.38f),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = startLabel, style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
        Text(text = endLabel, style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
    }
}

@Composable
private fun RefreshModeSection(
    active: Boolean,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.alpha(if (active) 1f else 0.38f),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        content()
    }
}
