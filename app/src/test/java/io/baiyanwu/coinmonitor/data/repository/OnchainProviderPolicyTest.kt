package io.baiyanwu.coinmonitor.data.repository

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnchainProviderPolicyTest {
    @Test
    fun `main source keeps onchain providers explicit and user credentialed`() {
        val source = readKotlinMainSources().replace(
            "\"https://web3.okx.com/token/{chain}/{ca}\"", "\"public browser shortcut\""
        )

        listOf(
            "wsdex.okx.com",
            "OkxOnChainApi",
            "OkxApiCredentials",
            "OkxCredentialsRepository"
        ).forEach { forbidden ->
            assertFalse("Found removed capability: $forbidden", source.contains(forbidden))
        }
        assertTrue(source.contains("/api/v6/dex/balance/all-token-balances-by-address"))
        assertTrue(source.contains("/api/v6/dex/balance/total-value-by-address"))
        assertTrue(source.contains("/api/v6/dex/market/token/search"))
        assertTrue(source.contains("/api/v6/dex/market/price-info"))
        assertTrue(source.contains("OnchainDataProvider"))
        assertTrue(source.contains("OnchainProviderRouter"))
        assertTrue(source.contains("OnchainPollingStrategy"))
        assertTrue(source.contains("createFallbackBatches"))
        assertTrue(source.contains("OkxWalletCredentials"))
        assertTrue(source.contains("https://www.okx.com/"))
        assertTrue(source.contains("OKX_PUBLIC_WS_URL"))
        assertTrue(source.contains("https://api.dexscreener.com/"))
        assertTrue(source.contains("https://api.geckoterminal.com/"))
    }

    @Test
    fun `room migrations preserve ids clear old onchain quotes and add overlay order`() {
        val databaseSource = readSource(
            "app/src/main/java/io/baiyanwu/coinmonitor/data/local/CoinMonitorDatabase.kt",
            "src/main/java/io/baiyanwu/coinmonitor/data/local/CoinMonitorDatabase.kt"
        )
        val schema8 = readSource(
            "app/schemas/io.baiyanwu.coinmonitor.data.local.CoinMonitorDatabase/8.json",
            "schemas/io.baiyanwu.coinmonitor.data.local.CoinMonitorDatabase/8.json"
        )
        val schema9 = readSource(
            "app/schemas/io.baiyanwu.coinmonitor.data.local.CoinMonitorDatabase/9.json",
            "schemas/io.baiyanwu.coinmonitor.data.local.CoinMonitorDatabase/9.json"
        )
        val schema10 = readSource(
            "app/schemas/io.baiyanwu.coinmonitor.data.local.CoinMonitorDatabase/10.json",
            "schemas/io.baiyanwu.coinmonitor.data.local.CoinMonitorDatabase/10.json"
        )
        val schema11 = readSource(
            "app/schemas/io.baiyanwu.coinmonitor.data.local.CoinMonitorDatabase/11.json",
            "schemas/io.baiyanwu.coinmonitor.data.local.CoinMonitorDatabase/11.json"
        )
        val schema12 = readSource(
            "app/schemas/io.baiyanwu.coinmonitor.data.local.CoinMonitorDatabase/12.json",
            "schemas/io.baiyanwu.coinmonitor.data.local.CoinMonitorDatabase/12.json"
        )

        assertTrue(databaseSource.contains("version = 12"))
        assertTrue(databaseSource.contains("Migration(7, 8)"))
        assertTrue(databaseSource.contains("Migration(8, 9)"))
        assertTrue(databaseSource.contains("Migration(9, 10)"))
        assertTrue(databaseSource.contains("Migration(10, 11)"))
        assertTrue(databaseSource.contains("Migration(11, 12)"))
        assertTrue(databaseSource.contains("ADD COLUMN poolAddress TEXT"))
        assertTrue(databaseSource.contains("ADD COLUMN poolTokenSide TEXT"))
        assertTrue(databaseSource.contains("ADD COLUMN overlayOrder INTEGER"))
        assertTrue(databaseSource.contains("ADD COLUMN marketCap REAL"))
        assertTrue(databaseSource.contains("ADD COLUMN onchainDataProvider TEXT NOT NULL DEFAULT 'DEX_SCREENER'"))
        assertTrue(databaseSource.contains("SET source = 'ONCHAIN'"))
        assertTrue(databaseSource.contains("lastPrice = NULL"))
        assertFalse(databaseSource.contains("UPDATE watch_items SET id"))
        assertTrue(schema8.contains("\"version\": 8"))
        assertTrue(schema8.contains("\"columnName\": \"poolAddress\""))
        assertTrue(schema8.contains("\"columnName\": \"poolTokenSide\""))
        assertTrue(schema9.contains("\"version\": 9"))
        assertTrue(schema9.contains("\"columnName\": \"overlayOrder\""))
        assertTrue(schema10.contains("\"version\": 10"))
        assertTrue(schema10.contains("\"columnName\": \"marketCap\""))
        assertTrue(schema11.contains("\"version\": 11"))
        assertTrue(schema11.contains("\"columnName\": \"onchainDataProvider\""))
        assertTrue(schema12.contains("\"version\": 12"))
        assertTrue(schema12.contains("\"tableName\": \"dapp_search_history\""))
    }

    private fun readKotlinMainSources(): String {
        val root = locate(
            "app/src/main/java",
            "src/main/java"
        )
        return Files.walk(root).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
                .sorted()
                .map { path -> String(Files.readAllBytes(path)) }
                .collect(java.util.stream.Collectors.toList())
                .joinToString("\n")
        }
    }

    private fun readSource(rootRelativePath: String, moduleRelativePath: String): String {
        return String(Files.readAllBytes(locate(rootRelativePath, moduleRelativePath)))
    }

    private fun locate(rootRelativePath: String, moduleRelativePath: String): Path {
        val cwd = Paths.get("").toAbsolutePath()
        return listOf(
            cwd.resolve(rootRelativePath),
            cwd.resolve(moduleRelativePath),
            cwd.parent?.resolve(rootRelativePath)
        ).filterNotNull().firstOrNull(Files::exists)
            ?: error("Unable to locate $rootRelativePath from $cwd")
    }
}
