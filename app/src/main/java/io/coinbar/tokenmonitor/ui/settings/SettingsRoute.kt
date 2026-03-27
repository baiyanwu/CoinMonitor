package io.coinbar.tokenmonitor.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.coinbar.tokenmonitor.R
import io.coinbar.tokenmonitor.data.AppContainer
import io.coinbar.tokenmonitor.domain.model.AppLanguage
import io.coinbar.tokenmonitor.domain.model.AppThemeMode
import io.coinbar.tokenmonitor.ui.theme.TokenMonitorComponentDefaults

@Composable
fun SettingsRoute(
    container: AppContainer,
    onNavigateOverlaySettings: () -> Unit
) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
    val state by viewModel.uiState.collectAsState()

    SettingsScreen(
        state = state,
        onNavigateOverlaySettings = onNavigateOverlaySettings,
        onThemeModeChange = viewModel::setThemeMode,
        onLanguageChange = viewModel::setLanguage
    )
}

@Composable
private fun SettingsScreen(
    state: SettingsUiState,
    onNavigateOverlaySettings: () -> Unit,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 12.dp),
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
                colors = TokenMonitorComponentDefaults.elevatedCardColors()
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
                colors = TokenMonitorComponentDefaults.elevatedCardColors()
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
                            colors = TokenMonitorComponentDefaults.filterChipColors()
                        )
                        FilterChip(
                            selected = state.preferences.themeMode == AppThemeMode.LIGHT,
                            onClick = { onThemeModeChange(AppThemeMode.LIGHT) },
                            label = { Text(stringResource(R.string.theme_light)) },
                            colors = TokenMonitorComponentDefaults.filterChipColors()
                        )
                        FilterChip(
                            selected = state.preferences.themeMode == AppThemeMode.DARK,
                            onClick = { onThemeModeChange(AppThemeMode.DARK) },
                            label = { Text(stringResource(R.string.theme_dark)) },
                            colors = TokenMonitorComponentDefaults.filterChipColors()
                        )
                    }
                }
            }
        }

        item {
            ElevatedCard(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = TokenMonitorComponentDefaults.elevatedCardColors()
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
                            colors = TokenMonitorComponentDefaults.filterChipColors()
                        )
                        FilterChip(
                            selected = state.preferences.language == AppLanguage.CHINESE_SIMPLIFIED,
                            onClick = { onLanguageChange(AppLanguage.CHINESE_SIMPLIFIED) },
                            label = { Text(stringResource(R.string.language_chinese)) },
                            colors = TokenMonitorComponentDefaults.filterChipColors()
                        )
                        FilterChip(
                            selected = state.preferences.language == AppLanguage.ENGLISH,
                            onClick = { onLanguageChange(AppLanguage.ENGLISH) },
                            label = { Text(stringResource(R.string.language_english)) },
                            colors = TokenMonitorComponentDefaults.filterChipColors()
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
