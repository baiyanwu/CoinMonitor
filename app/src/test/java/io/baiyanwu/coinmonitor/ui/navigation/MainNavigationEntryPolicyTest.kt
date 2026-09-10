package io.baiyanwu.coinmonitor.ui.navigation

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainNavigationEntryPolicyTest {
    @Test
    fun `main navigation should not expose kline as a top level entry`() {
        val source = readSource(
            rootRelativePath = "app/src/main/java/io/baiyanwu/coinmonitor/ui/navigation/CoinMonitorNavHost.kt",
            moduleRelativePath = "src/main/java/io/baiyanwu/coinmonitor/ui/navigation/CoinMonitorNavHost.kt"
        )

        assertFalse(source.contains("MainTab(Destinations.KLINE"))
        assertFalse(source.contains("R.string.tab_kline"))
        assertFalse(source.contains("Icons.Rounded.ShowChart"))
    }

    @Test
    fun `wallet is a top level entry between home and settings`() {
        val source = readSource(
            rootRelativePath = "app/src/main/java/io/baiyanwu/coinmonitor/ui/navigation/CoinMonitorNavHost.kt",
            moduleRelativePath = "src/main/java/io/baiyanwu/coinmonitor/ui/navigation/CoinMonitorNavHost.kt"
        )

        val homeIndex = source.indexOf("MainTab(Destinations.HOME")
        val walletIndex = source.indexOf("MainTab(Destinations.WALLET")
        val settingsIndex = source.indexOf("MainTab(Destinations.SETTINGS")

        assertTrue(homeIndex >= 0)
        assertTrue(walletIndex > homeIndex)
        assertTrue(settingsIndex > walletIndex)
        assertTrue(source.contains("composable(Destinations.WALLET)"))
        assertTrue(source.contains("WalletWatchRoute("))
    }

    @Test
    fun `home no longer owns a wallet watch entry`() {
        val source = readSource(
            rootRelativePath = "app/src/main/java/io/baiyanwu/coinmonitor/ui/home/HomeRoute.kt",
            moduleRelativePath = "src/main/java/io/baiyanwu/coinmonitor/ui/home/HomeRoute.kt"
        )

        assertFalse(source.contains("HomeWalletWatchBar"))
        assertFalse(source.contains("onNavigateWalletWatch"))
    }

    @Test
    fun `wallet scaffold should not apply system insets twice`() {
        val source = readSource(
            rootRelativePath = "app/src/main/java/io/baiyanwu/coinmonitor/ui/walletwatch/WalletWatchRoute.kt",
            moduleRelativePath = "src/main/java/io/baiyanwu/coinmonitor/ui/walletwatch/WalletWatchRoute.kt"
        )

        assertTrue(source.contains("contentWindowInsets = WindowInsets(0)"))
    }

    @Test
    fun `home watch item click should not open kline`() {
        val source = readSource(
            rootRelativePath = "app/src/main/java/io/baiyanwu/coinmonitor/ui/home/HomeRoute.kt",
            moduleRelativePath = "src/main/java/io/baiyanwu/coinmonitor/ui/home/HomeRoute.kt"
        )

        assertFalse(source.contains("onNavigateKline(item.id)"))
    }

    @Test
    fun `kline implementation remains in source tree`() {
        val source = readSource(
            rootRelativePath = "app/src/main/java/io/baiyanwu/coinmonitor/ui/kline/KlineRoute.kt",
            moduleRelativePath = "src/main/java/io/baiyanwu/coinmonitor/ui/kline/KlineRoute.kt"
        )

        assertTrue(source.contains("fun KlineRoute("))
    }

    private fun readSource(rootRelativePath: String, moduleRelativePath: String): String {
        val cwd = Paths.get("").toAbsolutePath()
        val path = listOf(
            cwd.resolve(rootRelativePath),
            cwd.resolve(moduleRelativePath),
            cwd.parent?.resolve(rootRelativePath)
        ).filterNotNull().firstOrNull(Files::exists)

        requireNotNull(path) {
            "Unable to locate source file from $cwd: $rootRelativePath"
        }
        return path.toFile().readText()
    }
}
