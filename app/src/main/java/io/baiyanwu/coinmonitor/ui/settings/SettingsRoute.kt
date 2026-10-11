package io.baiyanwu.coinmonitor.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.ui.components.CoinMonitorIcons
import io.baiyanwu.coinmonitor.ui.components.CoinMonitorNavigationRow
import io.baiyanwu.coinmonitor.ui.components.CoinMonitorPageHeader
import io.baiyanwu.coinmonitor.ui.components.CoinMonitorRoundedCard
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorDesignTheme
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorTheme
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
    SettingsHomeScreen(
        contentTopInset = contentTopInset,
        contentBottomInset = contentBottomInset,
        onNavigateGeneralSettings = onNavigateGeneralSettings,
        onNavigateWalletSettings = onNavigateWalletSettings,
        onNavigateOverlaySettings = onNavigateOverlaySettings,
        onNavigateThirdPartyApiSettings = onNavigateThirdPartyApiSettings,
        onNavigateAbout = onNavigateAbout
    )
}

/** 展示层可独立预览，实际导航只由 Route 提供。 */
@Composable
internal fun SettingsHomeScreen(
    contentTopInset: Dp = 0.dp,
    contentBottomInset: Dp = 0.dp,
    onNavigateGeneralSettings: () -> Unit,
    onNavigateWalletSettings: () -> Unit,
    onNavigateOverlaySettings: () -> Unit,
    onNavigateThirdPartyApiSettings: () -> Unit,
    onNavigateAbout: () -> Unit
) {
    val groups = listOf(
        SettingsGroup(
            id = "preferences",
            title = stringResource(R.string.settings_group_preferences_security),
            entries = listOf(
                SettingsEntry(
                    icon = CoinMonitorIcons.Preferences,
                    title = stringResource(R.string.general_settings_title),
                    subtitle = stringResource(R.string.general_settings_subtitle),
                    onClick = onNavigateGeneralSettings
                ),
                SettingsEntry(
                    icon = CoinMonitorIcons.Security,
                    title = stringResource(R.string.wallet_settings_title),
                    subtitle = stringResource(R.string.wallet_settings_subtitle),
                    onClick = onNavigateWalletSettings
                )
            )
        ),
        SettingsGroup(
            id = "monitoring",
            title = stringResource(R.string.settings_group_monitoring_data),
            entries = listOf(
                SettingsEntry(
                    icon = CoinMonitorIcons.Overlay,
                    title = stringResource(R.string.overlay_settings_title),
                    subtitle = stringResource(R.string.overlay_settings_subtitle),
                    onClick = onNavigateOverlaySettings
                ),
                SettingsEntry(
                    icon = CoinMonitorIcons.DataSources,
                    title = stringResource(R.string.settings_third_party_api_title),
                    subtitle = stringResource(R.string.settings_third_party_api_subtitle),
                    onClick = onNavigateThirdPartyApiSettings
                )
            )
        ),
        SettingsGroup(
            id = "application",
            title = stringResource(R.string.settings_group_application),
            entries = listOf(
                SettingsEntry(
                    icon = CoinMonitorIcons.Info,
                    title = stringResource(R.string.about_settings_title),
                    subtitle = stringResource(R.string.settings_about_subtitle),
                    onClick = onNavigateAbout
                )
            )
        )
    )
    CoinMonitorDesignTheme {
        val design = CoinMonitorThemeTokens.design
        Column(
            Modifier.fillMaxSize().background(design.colors.pageBackground)
                .padding(top = contentTopInset, bottom = contentBottomInset)
        ) {
            CoinMonitorPageHeader(stringResource(R.string.tab_settings), Modifier.fillMaxWidth())
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = design.spacing.pageHorizontal,
                    end = design.spacing.pageHorizontal,
                    top = design.spacing.inline,
                    bottom = design.spacing.pageHorizontal
                ),
                verticalArrangement = Arrangement.spacedBy(design.spacing.group)
            ) {
                groups.forEach { group ->
                    item(key = group.id) {
                        SettingsSection(group.title) {
                            CoinMonitorRoundedCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = design.spacing.inline)) {
                                group.entries.forEach { entry ->
                                    CoinMonitorNavigationRow(
                                        title = entry.title,
                                        subtitle = entry.subtitle,
                                        leadingIcon = entry.icon,
                                        onClick = entry.onClick
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    val design = CoinMonitorThemeTokens.design
    Column(verticalArrangement = Arrangement.spacedBy(design.spacing.inline)) {
        Text(
            title,
            modifier = Modifier.padding(horizontal = design.spacing.cardPadding).semantics { heading() },
            color = design.colors.secondaryText,
            style = design.typography.label
        )
        content()
    }
}

private data class SettingsGroup(val id: String, val title: String, val entries: List<SettingsEntry>)

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

@Preview(name = "设置首页 · 浅色", widthDp = 360, heightDp = 720, showBackground = true)
@Composable
private fun SettingsHomeLightPreview() = SettingsHomePreview(darkTheme = false)

@Preview(name = "设置首页 · 深色", widthDp = 360, heightDp = 720, showBackground = true)
@Composable
private fun SettingsHomeDarkPreview() = SettingsHomePreview(darkTheme = true)

@Preview(name = "设置首页 · 大字体", widthDp = 320, heightDp = 720, fontScale = 1.5f, showBackground = true)
@Composable
private fun SettingsHomeLargeFontPreview() = SettingsHomePreview(darkTheme = false)

@Composable
private fun SettingsHomePreview(darkTheme: Boolean) {
    CoinMonitorTheme(darkTheme = darkTheme) {
        SettingsHomeScreen(
            onNavigateGeneralSettings = {},
            onNavigateWalletSettings = {},
            onNavigateOverlaySettings = {},
            onNavigateThirdPartyApiSettings = {},
            onNavigateAbout = {}
        )
    }
}
