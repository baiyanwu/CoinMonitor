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
        assertTrue(source.contains("WalletRoute("))
        assertFalse(source.contains("WalletWatchRoute("))
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
    fun `self custody wallet scaffold should not apply system insets twice`() {
        val source = readSource(
            rootRelativePath = "app/src/main/java/io/baiyanwu/coinmonitor/ui/wallet/WalletRoute.kt",
            moduleRelativePath = "src/main/java/io/baiyanwu/coinmonitor/ui/wallet/WalletRoute.kt"
        )

        assertTrue(source.contains("contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0)"))
    }

    @Test
    fun `wallet operations use standalone full screen destinations`() {
        val navigationSource = readSource(
            rootRelativePath = "app/src/main/java/io/baiyanwu/coinmonitor/ui/navigation/CoinMonitorNavHost.kt",
            moduleRelativePath = "src/main/java/io/baiyanwu/coinmonitor/ui/navigation/CoinMonitorNavHost.kt"
        )
        val walletSource = readSource(
            rootRelativePath = "app/src/main/java/io/baiyanwu/coinmonitor/ui/wallet/WalletRoute.kt",
            moduleRelativePath = "src/main/java/io/baiyanwu/coinmonitor/ui/wallet/WalletRoute.kt"
        )

        listOf(
            "WALLET_ADD",
            "WALLET_MANAGE",
            "WALLET_RECEIVE",
            "WALLET_SEND",
            "WALLET_BACKUP",
            "WALLET_SECURITY"
        ).forEach { destination ->
            assertTrue(navigationSource.contains("walletDetailComposable(Destinations.$destination)"))
        }
        assertTrue(navigationSource.contains("const val MAIN = \"main\""))
        assertTrue(navigationSource.contains("composable(Destinations.MAIN)"))
        assertTrue(navigationSource.contains("private fun MainShell("))
        assertTrue(navigationSource.contains("private fun NavGraphBuilder.walletDetailComposable("))
        assertFalse(navigationSource.contains("animateFloatAsState("))
        assertFalse(navigationSource.contains("navigationBarAlpha"))
        assertFalse(navigationSource.contains("showMainNavigation"))
        assertTrue(navigationSource.contains("slideIntoContainer("))
        assertTrue(navigationSource.contains("slideOutOfContainer("))
        assertTrue(navigationSource.contains("rootNavController.popBackStack()"))
        assertFalse(navigationSource.contains("popBackStack(Destinations.WALLET"))
        assertTrue(walletSource.contains("WalletStandaloneRoute("))
        assertTrue(walletSource.contains("WalletStandaloneRoute(container, WalletPage.BACKUP"))
        assertTrue(walletSource.contains("retainedState"))
        assertFalse(walletSource.contains("if (state.page == WalletPage.BACKUP)"))
        assertFalse(walletSource.contains("state.page == WalletPage.MANAGE ->"))
        assertFalse(walletSource.contains("state.page == WalletPage.SEND ->"))
    }

    @Test
    fun `all wallet password fields expose visibility controls`() {
        val walletSource = readSource(
            rootRelativePath = "app/src/main/java/io/baiyanwu/coinmonitor/ui/wallet/WalletRoute.kt",
            moduleRelativePath = "src/main/java/io/baiyanwu/coinmonitor/ui/wallet/WalletRoute.kt"
        )

        assertTrue(walletSource.contains("private fun SensitiveWalletField("))
        assertTrue(walletSource.contains("Icons.Rounded.VisibilityOff"))
        assertTrue(walletSource.contains("Icons.Rounded.Visibility"))
        assertFalse(walletSource.contains("OutlinedTextField(password"))
    }

    @Test
    fun `mnemonic backup screen prevents screenshots`() {
        val walletSource = readSource(
            rootRelativePath = "app/src/main/java/io/baiyanwu/coinmonitor/ui/wallet/WalletRoute.kt",
            moduleRelativePath = "src/main/java/io/baiyanwu/coinmonitor/ui/wallet/WalletRoute.kt"
        )

        val backupStart = walletSource.indexOf("private fun BackupScreen(")
        val backupEnd = walletSource.indexOf("private enum class WalletBackupStep")

        assertTrue(backupStart >= 0)
        assertTrue(backupEnd > backupStart)
        val backupSource = walletSource.substring(backupStart, backupEnd)
        assertTrue(backupSource.contains("PreventScreenCaptureEffect()"))
        assertTrue(walletSource.contains("WindowManager.LayoutParams.FLAG_SECURE"))
        assertTrue(walletSource.contains("window?.setFlags("))
    }

    @Test
    fun `wallet unlock automatically prefers enabled biometrics`() {
        val walletSource = readSource(
            rootRelativePath = "app/src/main/java/io/baiyanwu/coinmonitor/ui/wallet/WalletRoute.kt",
            moduleRelativePath = "src/main/java/io/baiyanwu/coinmonitor/ui/wallet/WalletRoute.kt"
        )

        val unlockStart = walletSource.indexOf("private fun WalletUnlock(")
        val unlockEnd = walletSource.indexOf("private fun FirstWalletSetup(")

        assertTrue(unlockStart >= 0)
        assertTrue(unlockEnd > unlockStart)
        val unlockSource = walletSource.substring(unlockStart, unlockEnd)
        assertTrue(unlockSource.contains("LaunchedEffect(biometricAvailable)"))
        assertTrue(unlockSource.contains("automaticPromptStarted"))
        assertTrue(unlockSource.contains("onAuthenticationError = { showPasswordFallback = true }"))
        assertTrue(unlockSource.contains("if (!showPasswordFallback)"))
        assertTrue(unlockSource.contains("onClick = launchBiometric"))
    }

    @Test
    fun `wallet setup keeps mnemonic input visible above the ime and uses compact wallet tags`() {
        val walletSource = readSource(
            rootRelativePath = "app/src/main/java/io/baiyanwu/coinmonitor/ui/wallet/WalletRoute.kt",
            moduleRelativePath = "src/main/java/io/baiyanwu/coinmonitor/ui/wallet/WalletRoute.kt"
        )
        val mnemonicSource = readSource(
            rootRelativePath = "app/src/main/java/io/baiyanwu/coinmonitor/ui/wallet/components/MnemonicInput.kt",
            moduleRelativePath = "src/main/java/io/baiyanwu/coinmonitor/ui/wallet/components/MnemonicInput.kt"
        )

        val setupStart = walletSource.indexOf("private fun WalletSetupForm(")
        val setupEnd = walletSource.indexOf("private fun PasswordSetupField(")
        assertTrue(setupStart >= 0)
        assertTrue(setupEnd > setupStart)
        val setupSource = walletSource.substring(setupStart, setupEnd)

        assertTrue(setupSource.contains(".imePadding()"))
        assertTrue(setupSource.indexOf(".imePadding()") < setupSource.indexOf(".verticalScroll("))
        assertTrue(setupSource.split("CoinMonitorComponentDefaults.filterChipColors()").size - 1 >= 4)
        assertTrue(setupSource.contains("WalletPrivateKeyType.EVM"))
        assertTrue(setupSource.contains("WalletPrivateKeyType.SOLANA"))
        assertFalse(setupSource.contains("NetworkDropdown("))
        assertTrue(mnemonicSource.contains("bringIntoViewRequester.bringIntoView()"))
        assertTrue(mnemonicSource.contains("private fun MnemonicWordTag("))
        assertTrue(mnemonicSource.contains("shape = RoundedCornerShape(50)"))
        assertTrue(mnemonicSource.contains("MaterialTheme.colorScheme.primaryContainer"))
        assertTrue(mnemonicSource.contains("MaterialTheme.colorScheme.surfaceVariant"))
        assertTrue(mnemonicSource.contains("color = MaterialTheme.colorScheme.primary,"))
        assertTrue(mnemonicSource.contains("contentColor = MaterialTheme.colorScheme.onPrimary"))
        assertTrue(mnemonicSource.contains(".size(24.dp)"))
        assertTrue(mnemonicSource.contains("modifier = Modifier.size(12.dp)"))
        assertTrue(mnemonicSource.contains(".offset(x = 5.dp, y = (-5).dp)"))
        assertTrue(mnemonicSource.contains("modifier = modifier.padding(top = 4.dp, end = 4.dp)"))
        assertFalse(mnemonicSource.contains("top = if (onDelete != null)"))
        assertTrue(mnemonicSource.contains(".offset(y = 2.dp)"))
        assertTrue(mnemonicSource.contains("contentDescription = stringResource(R.string.wallet_mnemonic_insert_after)"))
        assertFalse(mnemonicSource.contains("R.string.wallet_mnemonic_selected_word"))
        assertFalse(mnemonicSource.contains("Icons.Rounded.Delete"))
        assertFalse(mnemonicSource.contains("R.string.wallet_mnemonic_cancel_selection"))
        assertTrue(mnemonicSource.contains("selection = TextRange(word.length)"))
        assertTrue(mnemonicSource.contains("LaunchedEffect(inputFocused)"))
        assertFalse(mnemonicSource.contains("LaunchedEffect(inputFocused, suggestions)"))
        assertTrue(mnemonicSource.contains(".height(36.dp)"))
        assertTrue(mnemonicSource.contains("verticalArrangement = Arrangement.spacedBy(4.dp)"))
        assertTrue(mnemonicSource.indexOf("R.string.wallet_mnemonic_word_count") < mnemonicSource.indexOf("OutlinedTextField("))
        assertTrue(mnemonicSource.indexOf("if (suggestions.isEmpty())") < mnemonicSource.indexOf("OutlinedTextField("))
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
