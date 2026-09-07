package io.baiyanwu.coinmonitor.data.repository

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnchainProviderPolicyTest {
    @Test
    fun `main source has no removed OKX DEX quote endpoints or capabilities`() {
        val source = readKotlinMainSources()

        listOf(
            "wsdex.okx.com",
            "OkxOnChainApi",
            "OkxOnChainRequestSigner",
            "OkxApiCredentials",
            "OkxCredentialsRepository"
        ).forEach { forbidden ->
            assertFalse("Found removed capability: $forbidden", source.contains(forbidden))
        }
        assertTrue(source.contains("/api/v6/dex/balance/all-token-balances-by-address"))
        assertTrue(source.contains("/api/v6/dex/balance/total-value-by-address"))
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

        assertTrue(databaseSource.contains("version = 9"))
        assertTrue(databaseSource.contains("Migration(7, 8)"))
        assertTrue(databaseSource.contains("Migration(8, 9)"))
        assertTrue(databaseSource.contains("ADD COLUMN poolAddress TEXT"))
        assertTrue(databaseSource.contains("ADD COLUMN poolTokenSide TEXT"))
        assertTrue(databaseSource.contains("ADD COLUMN overlayOrder INTEGER"))
        assertTrue(databaseSource.contains("SET source = 'ONCHAIN'"))
        assertTrue(databaseSource.contains("lastPrice = NULL"))
        assertFalse(databaseSource.contains("UPDATE watch_items SET id"))
        assertTrue(schema8.contains("\"version\": 8"))
        assertTrue(schema8.contains("\"columnName\": \"poolAddress\""))
        assertTrue(schema8.contains("\"columnName\": \"poolTokenSide\""))
        assertTrue(schema9.contains("\"version\": 9"))
        assertTrue(schema9.contains("\"columnName\": \"overlayOrder\""))
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
