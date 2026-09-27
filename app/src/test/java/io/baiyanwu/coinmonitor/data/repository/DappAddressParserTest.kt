package io.baiyanwu.coinmonitor.data.repository

import io.baiyanwu.coinmonitor.domain.model.DappCatalog
import java.nio.file.Files
import java.nio.file.Paths
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DappAddressParserTest {
    @Test
    fun `normalizes secure host and preserves path`() {
        assertEquals(
            "https://app.uniswap.org/swap",
            DappAddressParser.normalize("app.uniswap.org/swap")
        )
    }

    @Test
    fun `rejects insecure and malformed addresses`() {
        assertNull(DappAddressParser.normalize("http://example.com"))
        assertNull(DappAddressParser.normalize("not a host"))
        assertNull(DappAddressParser.normalize(""))
    }

    @Test
    fun `extracts normalized origin without path`() {
        assertEquals(
            "https://pancakeswap.finance",
            DappAddressParser.origin("https://PancakeSwap.finance/swap")
        )
    }

    @Test
    fun `user web address suggestion requires a real domain or valid ip`() {
        assertEquals(
            "https://app.uniswap.org/swap",
            DappAddressParser.normalizeUserWebAddress("app.uniswap.org/swap")
        )
        assertEquals(
            "https://1.1.1.1/",
            DappAddressParser.normalizeUserWebAddress("https://1.1.1.1/")
        )
        assertNull(DappAddressParser.normalizeUserWebAddress("uniswap"))
        assertNull(DappAddressParser.normalizeUserWebAddress("BNB"))
        assertNull(DappAddressParser.normalizeUserWebAddress("https://999.1.1.1"))
        assertNull(DappAddressParser.normalizeUserWebAddress("https://bad_host.example"))
    }

    @Test
    fun `bundled catalog has unique secure entries and every category`() {
        val root = Paths.get("").toAbsolutePath()
        val direct = root.resolve("app/src/main/res/raw/dapp_catalog_v1.json")
        val module = root.resolve("src/main/res/raw/dapp_catalog_v1.json")
        val path = if (Files.exists(direct)) direct else module
        val catalog = Json.decodeFromString<DappCatalog>(String(Files.readAllBytes(path)))

        assertEquals(catalog.dapps.size, catalog.dapps.map { it.id }.distinct().size)
        assertEquals(catalog.dapps.size, catalog.dapps.map { it.domain }.distinct().size)
        assertTrue(catalog.dapps.all { DappAddressParser.normalize(it.url) != null })
        assertTrue(catalog.dapps.all { DappAddressParser.origin(it.url) != null })
        assertEquals(
            io.baiyanwu.coinmonitor.domain.model.DappCategory.entries.toSet(),
            catalog.dapps.map { it.category }.toSet()
        )
    }
}
