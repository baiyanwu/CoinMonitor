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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorComponentDefaults
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens

@Composable
fun SettingsRoute(
    contentTopInset: Dp = 0.dp,
    contentBottomInset: Dp = 0.dp,
    onNavigateGeneralSettings: () -> Unit,
    onNavigateWalletSettings: () -> Unit,
    onNavigateOverlaySettings: () -> Unit,
    onNavigateThirdPartyApiSettings: () -> Unit,
    onNavigateAbout: () -> Unit
) {
    val entries = listOf(
        SettingsEntry(
            icon = Icons.Rounded.Settings,
            title = stringResource(R.string.general_settings_title),
            subtitle = stringResource(R.string.general_settings_subtitle),
            onClick = onNavigateGeneralSettings
        ),
        SettingsEntry(
            icon = Icons.Rounded.AccountBalanceWallet,
            title = stringResource(R.string.wallet_settings_title),
            subtitle = stringResource(R.string.wallet_settings_subtitle),
            onClick = onNavigateWalletSettings
        ),
        SettingsEntry(
            icon = Icons.Rounded.Tune,
            title = stringResource(R.string.overlay_settings_title),
            subtitle = stringResource(R.string.overlay_settings_subtitle),
            onClick = onNavigateOverlaySettings
        ),
        SettingsEntry(
            icon = Icons.Rounded.VpnKey,
            title = stringResource(R.string.settings_third_party_api_title),
            subtitle = stringResource(R.string.settings_third_party_api_subtitle),
            onClick = onNavigateThirdPartyApiSettings
        ),
        SettingsEntry(
            icon = Icons.Rounded.Info,
            title = stringResource(R.string.about_settings_title),
            onClick = onNavigateAbout
        )
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CoinMonitorThemeTokens.colors.pageBackground)
            .padding(top = contentTopInset, bottom = contentBottomInset)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(entries.size) { index ->
            val entry = entries[index]
            ElevatedCard(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CoinMonitorComponentDefaults.elevatedCardColors()
            ) {
                SettingsNavigationRow(
                    icon = { Icon(entry.icon, contentDescription = null) },
                    title = entry.title,
                    subtitle = entry.subtitle,
                    onClick = entry.onClick
                )
            }
        }
    }
}

private data class SettingsEntry(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val subtitle: String? = null,
    val onClick: () -> Unit
)

@Composable
internal fun SettingsNavigationRow(
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
