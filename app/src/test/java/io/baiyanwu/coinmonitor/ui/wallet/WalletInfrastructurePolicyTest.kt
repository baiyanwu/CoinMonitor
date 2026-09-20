package io.baiyanwu.coinmonitor.ui.wallet

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletInfrastructurePolicyTest {
    @Test
    fun `wallet repositories require the shared http client`() {
        val portfolioRepository = readSource(
            "app/src/main/java/io/baiyanwu/coinmonitor/data/repository/DefaultSelfCustodyWalletRepository.kt",
            "src/main/java/io/baiyanwu/coinmonitor/data/repository/DefaultSelfCustodyWalletRepository.kt"
        )
        val settingsRepository = readSource(
            "app/src/main/java/io/baiyanwu/coinmonitor/data/repository/DefaultWalletNetworkSettingsRepository.kt",
            "src/main/java/io/baiyanwu/coinmonitor/data/repository/DefaultWalletNetworkSettingsRepository.kt"
        )
        val container = readSource(
            "app/src/main/java/io/baiyanwu/coinmonitor/data/AppContainer.kt",
            "src/main/java/io/baiyanwu/coinmonitor/data/AppContainer.kt"
        )

        assertFalse(portfolioRepository.contains("httpClient: OkHttpClient ="))
        assertFalse(settingsRepository.contains("httpClient: OkHttpClient ="))
        val construction = container.substringAfter("val selfCustodyWalletRepository")
            .substringBefore("val walletSessionLockObserver")
        assertTrue(construction.contains("httpClient = networkFactory.okHttpClient"))
    }

    private fun readSource(rootRelativePath: String, moduleRelativePath: String): String {
        val cwd = Paths.get("").toAbsolutePath()
        val path = listOf(
            cwd.resolve(rootRelativePath),
            cwd.resolve(moduleRelativePath),
            cwd.parent?.resolve(rootRelativePath)
        ).filterNotNull().firstOrNull(Files::exists)
        return requireNotNull(path) { "Unable to locate $rootRelativePath from $cwd" }.toFile().readText()
    }
}
