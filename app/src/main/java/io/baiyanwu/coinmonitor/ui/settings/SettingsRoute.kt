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
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.domain.model.AppLanguage
import io.baiyanwu.coinmonitor.domain.model.AppThemeMode
import io.baiyanwu.coinmonitor.ui.components.MainTabTopBar
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorComponentDefaults
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens
import io.baiyanwu.coinmonitor.R

@Composable
fun SettingsRoute(
    container: AppContainer,
    contentTopInset: Dp = 0.dp,
    contentBottomInset: Dp = 0.dp,
    onNavigateOverlaySettings: () -> Unit,
    onNavigateThirdPartyApiSettings: () -> Unit,
    onNavigateNetworkLog: () -> Unit
) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsScreen(
        state = state,
        contentTopInset = contentTopInset,
        contentBottomInset = contentBottomInset,
        onNavigateOverlaySettings = onNavigateOverlaySettings,
        onNavigateThirdPartyApiSettings = onNavigateThirdPartyApiSettings,
        onNavigateNetworkLog = onNavigateNetworkLog,
        onThemeModeChange = viewModel::setThemeMode,
        onLanguageChange = viewModel::setLanguage
    )
}

@Composable
private fun SettingsScreen(
    state: SettingsUiState,
    contentTopInset: Dp,
    contentBottomInset: Dp,
    onNavigateOverlaySettings: () -> Unit,
    onNavigateThirdPartyApiSettings: () -> Unit,
    onNavigateNetworkLog: () -> Unit,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit
) {
    val colors = CoinMonitorThemeTokens.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.pageBackground)
            .padding(top = contentTopInset, bottom = contentBottomInset)
    ) {
        MainTabTopBar {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 14.dp,
                    end = 14.dp,
                    top = 12.dp,
                    bottom = 12.dp
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

        item {
            ElevatedCard(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CoinMonitorComponentDefaults.elevatedCardColors()
            ) {
                SettingNavigationRow(
                    icon = { Icon(Icons.Rounded.VpnKey, contentDescription = null) },
                    title = stringResource(R.string.settings_third_party_api_title),
                    onClick = onNavigateThirdPartyApiSettings
                )
            }
        }

        item {
            ElevatedCard(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CoinMonitorComponentDefaults.elevatedCardColors()
            ) {
                SettingNavigationRow(
                    icon = { Icon(Icons.Rounded.ReceiptLong, contentDescription = null) },
                    title = stringResource(R.string.network_log_title),
                    onClick = onNavigateNetworkLog
                )
            }
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
}

@Composable
private fun SettingNavigationRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String? = null,
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
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = CoinMonitorThemeTokens.colors.secondaryText
                    )
                }
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
