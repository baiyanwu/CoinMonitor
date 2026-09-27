package io.baiyanwu.coinmonitor.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.domain.model.AppLanguage
import io.baiyanwu.coinmonitor.domain.model.AppThemeMode
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorComponentDefaults
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens
import io.baiyanwu.coinmonitor.ui.wallet.WalletPage
import io.baiyanwu.coinmonitor.ui.wallet.activityWalletViewModel

@Composable
fun GeneralSettingsRoute(
    container: AppContainer,
    onBack: () -> Unit,
    onNavigateNetworkLog: () -> Unit
) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsDetailScaffold(title = stringResource(R.string.general_settings_title), onBack = onBack) {
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CoinMonitorComponentDefaults.elevatedCardColors()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = state.preferences.showOnchainMarketCap,
                            role = Role.Switch,
                            onValueChange = viewModel::setShowOnchainMarketCap
                        )
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Settings, contentDescription = null)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.show_onchain_market_cap), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.show_onchain_market_cap_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = CoinMonitorThemeTokens.colors.secondaryText
                        )
                    }
                    Switch(checked = state.preferences.showOnchainMarketCap, onCheckedChange = null)
                }
            }
        }
        item {
            ChoiceSettingsCard(
                icon = { Icon(Icons.Rounded.DarkMode, contentDescription = null) },
                title = stringResource(R.string.appearance_title)
            ) {
                FilterChip(
                    selected = state.preferences.themeMode == AppThemeMode.SYSTEM,
                    onClick = { viewModel.setThemeMode(AppThemeMode.SYSTEM) },
                    label = { Text(stringResource(R.string.theme_system)) },
                    colors = CoinMonitorComponentDefaults.filterChipColors()
                )
                FilterChip(
                    selected = state.preferences.themeMode == AppThemeMode.LIGHT,
                    onClick = { viewModel.setThemeMode(AppThemeMode.LIGHT) },
                    label = { Text(stringResource(R.string.theme_light)) },
                    colors = CoinMonitorComponentDefaults.filterChipColors()
                )
                FilterChip(
                    selected = state.preferences.themeMode == AppThemeMode.DARK,
                    onClick = { viewModel.setThemeMode(AppThemeMode.DARK) },
                    label = { Text(stringResource(R.string.theme_dark)) },
                    colors = CoinMonitorComponentDefaults.filterChipColors()
                )
            }
        }
        item {
            ChoiceSettingsCard(
                icon = { Icon(Icons.Rounded.Language, contentDescription = null) },
                title = stringResource(R.string.language_title)
            ) {
                FilterChip(
                    selected = state.preferences.language == AppLanguage.SYSTEM,
                    onClick = { viewModel.setLanguage(AppLanguage.SYSTEM) },
                    label = { Text(stringResource(R.string.language_system)) },
                    colors = CoinMonitorComponentDefaults.filterChipColors()
                )
                FilterChip(
                    selected = state.preferences.language == AppLanguage.CHINESE_SIMPLIFIED,
                    onClick = { viewModel.setLanguage(AppLanguage.CHINESE_SIMPLIFIED) },
                    label = { Text(stringResource(R.string.language_chinese)) },
                    colors = CoinMonitorComponentDefaults.filterChipColors()
                )
                FilterChip(
                    selected = state.preferences.language == AppLanguage.ENGLISH,
                    onClick = { viewModel.setLanguage(AppLanguage.ENGLISH) },
                    label = { Text(stringResource(R.string.language_english)) },
                    colors = CoinMonitorComponentDefaults.filterChipColors()
                )
            }
        }
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CoinMonitorComponentDefaults.elevatedCardColors()
            ) {
                SettingsNavigationRow(
                    icon = {
                        Icon(Icons.AutoMirrored.Rounded.ReceiptLong, contentDescription = null)
                    },
                    title = stringResource(R.string.network_log_title),
                    subtitle = stringResource(R.string.network_log_settings_subtitle),
                    onClick = onNavigateNetworkLog
                )
            }
        }
    }
}

@Composable
fun WalletSettingsRoute(
    container: AppContainer,
    onBack: () -> Unit,
    onManageWallets: () -> Unit,
    onChooseBackupWallet: () -> Unit,
    onSecuritySettings: () -> Unit
) {
    val walletViewModel = activityWalletViewModel(container)
    SettingsDetailScaffold(title = stringResource(R.string.wallet_settings_title), onBack = onBack) {
        item {
            WalletSettingsCard(
                icon = { Icon(Icons.Rounded.AccountBalanceWallet, contentDescription = null) },
                title = stringResource(R.string.wallet_manage),
                onClick = {
                    walletViewModel.setPage(WalletPage.MANAGE)
                    onManageWallets()
                }
            )
        }
        item {
            WalletSettingsCard(
                icon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                title = stringResource(R.string.wallet_backup),
                onClick = onChooseBackupWallet
            )
        }
        item {
            WalletSettingsCard(
                icon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                title = stringResource(R.string.wallet_security),
                onClick = {
                    walletViewModel.setPage(WalletPage.SECURITY)
                    onSecuritySettings()
                }
            )
        }
    }
}

@Composable
private fun WalletSettingsCard(icon: @Composable () -> Unit, title: String, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CoinMonitorComponentDefaults.elevatedCardColors()
    ) {
        SettingsNavigationRow(icon = icon, title = title, onClick = onClick)
    }
}

@Composable
private fun ChoiceSettingsCard(
    icon: @Composable () -> Unit,
    title: String,
    choices: @Composable RowScope.() -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CoinMonitorComponentDefaults.elevatedCardColors()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                icon()
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = choices)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDetailScaffold(
    title: String,
    onBack: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    Scaffold(
        containerColor = CoinMonitorThemeTokens.colors.pageBackground,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = CoinMonitorThemeTokens.colors.pageBackground
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(CoinMonitorThemeTokens.colors.pageBackground)
                .padding(innerPadding),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}
