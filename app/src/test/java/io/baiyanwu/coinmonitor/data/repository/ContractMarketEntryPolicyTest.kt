package io.baiyanwu.coinmonitor.data.repository

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class ContractMarketEntryPolicyTest {
    @Test
    fun `exchange search exposes only usdt futures contracts with compact symbols`() {
        val source = readSource(
            rootRelativePath = "app/src/main/java/io/baiyanwu/coinmonitor/data/repository/DefaultMarketSearchRepository.kt",
            moduleRelativePath = "src/main/java/io/baiyanwu/coinmonitor/data/repository/DefaultMarketSearchRepository.kt"
        )

        assertTrue(source.contains("searchBinanceUsdtFutures"))
        assertTrue(source.contains("id = \"binance-futures:${'$'}{row.symbol}\""))
        assertTrue(source.contains("symbol = row.symbol.uppercase()"))
        assertTrue(source.contains("it.contractType in supportedBinanceFuturesPerpetualContractTypes"))
        assertTrue(source.contains("it.marginAsset == \"USDT\""))
        assertTrue(source.contains("searchOkxUsdtFutures"))
        assertTrue(source.contains("id = \"okx-futures:${'$'}{row.instId}\""))
        assertTrue(source.contains("symbol = row.instId.removeSuffix(\"-SWAP\").replace(\"-\", \"\").uppercase()"))
        assertTrue(source.contains("it.instId.endsWith(\"-USDT-SWAP\")"))
    }

    @Test
    fun `quote refresh subscribes to binance and okx usdt futures`() {
        val source = readSource(
            rootRelativePath = "app/src/main/java/io/baiyanwu/coinmonitor/data/refresh/StreamingQuoteRefreshEngine.kt",
            moduleRelativePath = "src/main/java/io/baiyanwu/coinmonitor/data/refresh/StreamingQuoteRefreshEngine.kt"
        )

        assertTrue(source.contains("BINANCE_FUTURES_PUBLIC_WS_URL = \"wss://fstream.binance.com/market/ws\""))
        assertTrue(source.contains("runBinanceFuturesSocketLoop"))
        assertTrue(source.contains("binance-usdt-futures"))
        assertTrue(source.contains("isOkxPublicTickerItem"))
        assertTrue(source.contains("MarketType.CEX_USDT_FUTURES -> item.id.substringAfter(\"okx-futures:\")"))
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
