package io.baiyanwu.coinmonitor.ui.settings

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSectionNavigationPolicyTest {
    @Test
    fun `general preferences live on the general settings detail page`() {
        val settingsSource = readSource(
            "app/src/main/java/io/baiyanwu/coinmonitor/ui/settings/SettingsRoute.kt",
            "src/main/java/io/baiyanwu/coinmonitor/ui/settings/SettingsRoute.kt"
        )
        val detailsSource = readSource(
            "app/src/main/java/io/baiyanwu/coinmonitor/ui/settings/SettingsDetailRoutes.kt",
            "src/main/java/io/baiyanwu/coinmonitor/ui/settings/SettingsDetailRoutes.kt"
        )

        assertTrue(settingsSource.contains("onNavigateGeneralSettings"))
        assertTrue(settingsSource.contains("R.string.general_settings_title"))
        assertFalse(settingsSource.contains("R.string.show_onchain_market_cap"))
        assertFalse(settingsSource.contains("R.string.appearance_title"))
        assertFalse(settingsSource.contains("R.string.language_title"))
        assertFalse(settingsSource.contains("R.string.network_log_title"))
        assertFalse(settingsSource.contains("R.string.clipboard_settings"))
        assertTrue(detailsSource.contains("fun GeneralSettingsRoute("))
        assertTrue(detailsSource.contains("R.string.show_onchain_market_cap"))
        assertTrue(detailsSource.contains("R.string.appearance_title"))
        assertTrue(detailsSource.contains("R.string.language_title"))
        assertTrue(detailsSource.contains("R.string.network_log_title"))
    }

    @Test
    fun `settings index orders overlay below wallet and clipboard is first in overlay settings`() {
        val settingsSource = readSource(
            "app/src/main/java/io/baiyanwu/coinmonitor/ui/settings/SettingsRoute.kt",
            "src/main/java/io/baiyanwu/coinmonitor/ui/settings/SettingsRoute.kt"
        )
        val overlaySource = readSource(
            "app/src/main/java/io/baiyanwu/coinmonitor/ui/settings/OverlaySettingsRoute.kt",
            "src/main/java/io/baiyanwu/coinmonitor/ui/settings/OverlaySettingsRoute.kt"
        )

        val generalIndex = settingsSource.indexOf("R.string.general_settings_title")
        val walletIndex = settingsSource.indexOf("R.string.wallet_settings_title")
        val overlayIndex = settingsSource.indexOf("R.string.overlay_settings_title")
        assertTrue(generalIndex >= 0)
        assertTrue(walletIndex > generalIndex)
        assertTrue(overlayIndex > walletIndex)
        assertTrue(settingsSource.contains("R.string.overlay_settings_subtitle"))
        assertTrue(settingsSource.contains("R.string.settings_third_party_api_subtitle"))

        val clipboardIndex = overlaySource.indexOf("ClipboardSettingsNavigationCard(onClick")
        val pairsIndex = overlaySource.indexOf("OverlayItemsNavigationCard(onClick")
        assertTrue(clipboardIndex >= 0)
        assertTrue(pairsIndex > clipboardIndex)
        assertTrue(overlaySource.contains("R.string.clipboard_settings_description"))
        assertTrue(overlaySource.contains("private fun OverlayNavigationCard("))
    }

    @Test
    fun `wallet settings reuse wallet operations and backup requires a wallet choice`() {
        val settingsSource = readSource(
            "app/src/main/java/io/baiyanwu/coinmonitor/ui/settings/SettingsDetailRoutes.kt",
            "src/main/java/io/baiyanwu/coinmonitor/ui/settings/SettingsDetailRoutes.kt"
        )
        val navigationSource = readSource(
            "app/src/main/java/io/baiyanwu/coinmonitor/ui/navigation/CoinMonitorNavHost.kt",
            "src/main/java/io/baiyanwu/coinmonitor/ui/navigation/CoinMonitorNavHost.kt"
        )
        val walletSource = readSource(
            "app/src/main/java/io/baiyanwu/coinmonitor/ui/wallet/WalletRoute.kt",
            "src/main/java/io/baiyanwu/coinmonitor/ui/wallet/WalletRoute.kt"
        )

        assertTrue(settingsSource.contains("fun WalletSettingsRoute("))
        assertTrue(settingsSource.contains("R.string.wallet_manage"))
        assertTrue(settingsSource.contains("R.string.wallet_backup"))
        assertTrue(settingsSource.contains("R.string.wallet_security"))
        assertTrue(navigationSource.contains("SETTINGS_WALLET_BACKUP_SELECT"))
        assertTrue(navigationSource.contains("WalletBackupSelectionRoute("))
        assertTrue(walletSource.contains("fun WalletBackupSelectionRoute("))
        assertTrue(walletSource.contains("viewModel::selectWalletForBackup"))

        val moreMenuStart = walletSource.indexOf("DropdownMenu(moreMenu")
        val moreMenuEnd = walletSource.indexOf("R.string.wallet_watch_entry", moreMenuStart)
        assertTrue(moreMenuStart >= 0)
        assertTrue(moreMenuEnd > moreMenuStart)
        val moreMenu = walletSource.substring(moreMenuStart, moreMenuEnd)
        assertTrue(moreMenu.contains("WalletPage.MANAGE"))
        assertTrue(moreMenu.contains("WalletPage.BACKUP"))
        assertTrue(moreMenu.contains("WalletPage.SECURITY"))
    }

    private fun readSource(rootRelativePath: String, moduleRelativePath: String): String {
        val cwd = Paths.get("").toAbsolutePath()
        val path = listOf(
            cwd.resolve(rootRelativePath),
            cwd.resolve(moduleRelativePath),
            cwd.parent?.resolve(rootRelativePath)
        ).filterNotNull().firstOrNull(Files::exists)

        requireNotNull(path) { "Unable to locate source file from $cwd: $rootRelativePath" }
        return path.toFile().readText()
    }
}
