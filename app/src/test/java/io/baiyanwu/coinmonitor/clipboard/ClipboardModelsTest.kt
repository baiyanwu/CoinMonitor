package io.baiyanwu.coinmonitor.clipboard

import io.baiyanwu.coinmonitor.data.network.*
import io.baiyanwu.coinmonitor.domain.model.PoolTokenSide
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

internal const val CA = "0x1234567890abcdef1234567890abcdef12345678"
internal fun testPair(chain: String = "ethereum", address: String = CA, liquidity: Double = 1000.0) = DexScreenerPair(
    chainId = chain, pairAddress = "0xPool", baseToken = DexScreenerToken(address, "Token", "TKN"),
    quoteToken = DexScreenerToken("0x0000000000000000000000000000000000000000", "USD", "USD"),
    priceUsd = "2", priceNative = "2", liquidity = DexScreenerLiquidity(liquidity), marketCap = 1000000.0,
    info = DexScreenerInfo(websites = listOf(DexScreenerWebsite("Site", "https://example.com")))
)

class ClipboardModelsTest {
    @Test fun extractsAddressesFromTextAndUrlsWithoutPartialMatches() {
        val sol = "So11111111111111111111111111111111111111112"
        assertEquals(listOf(CA, sol), ClipboardAddressParser.extract("CA: $CA\nhttps://site/token/$sol?x=1 $CA"))
        assertTrue(ClipboardAddressParser.extract("${CA}ab").isEmpty())
        assertTrue(ClipboardAddressParser.extract("hello, ordinary text").isEmpty())
    }

    @Test fun onlyEvmAddressDeduplicationIgnoresCase() {
        assertEquals(1, ClipboardAddressParser.extract("$CA ${CA.replace("abcdef", "ABCDEF")}").size)
        val sol = "So11111111111111111111111111111111111111112"
        assertEquals(2, ClipboardAddressParser.extract("$sol ${sol.replace("So", "so")}").size)
    }

    @Test fun multipleChainsStaySeparateEvenWhenPoolAddressesAreEqual() {
        val matches = clipboardMatches(CA, listOf(testPair(), testPair("base")))
        assertEquals(setOf("ethereum", "base"), matches.map { it.chain.dexScreenerId }.toSet())
        assertEquals(2, matches.map { it.watchItem(false).id }.distinct().size)
    }

    @Test fun ignoresSymbolOnlyAndInvalidPoolMatches() {
        assertTrue(clipboardMatches(CA, listOf(testPair(address = "unrelated"))).isEmpty())
        assertTrue(clipboardMatches(CA, listOf(testPair(liquidity = 0.0))).isEmpty())
    }

    @Test fun selectsPoolUsingExistingLiquidityPolicy() {
        val best = testPair(liquidity = 5000.0).copy(pairAddress = "bestPool")
        assertEquals("bestPool", clipboardMatches(CA, listOf(testPair(), best)).single().selection.pair.pairAddress)
    }

    @Test fun quoteSideNeverInheritsBaseTokenMarketCapOrLinks() {
        val quote = testPair().quoteToken.address
        val match = clipboardMatches(quote, listOf(testPair())).single()
        assertEquals(PoolTokenSide.QUOTE, match.selection.tokenSide)
        assertEquals(1.0, match.selection.priceUsd, 0.0)
        assertNull(match.marketCap)
        assertTrue(match.links.isEmpty())
        assertNull(match.watchItem(false).iconUrl)
    }

    @Test fun quickAddKeepsChainAndPoolIdentityAndFloatingPreference() {
        val match = clipboardMatches(CA, listOf(testPair())).single()
        val item = match.watchItem(true, 1234)
        assertEquals("onchain:1:$CA", item.id)
        assertEquals("TKN / USD", item.symbol)
        assertEquals("0xpool", item.poolAddress)
        assertEquals(PoolTokenSide.BASE, item.poolTokenSide)
        assertTrue(item.overlaySelected)
        assertFalse(match.watchItem(false).overlaySelected)
    }

    @Test fun dexDestinationUsesPoolNotTokenAddress() {
        val match = clipboardMatches(CA, listOf(testPair())).single()
        assertEquals("https://dexscreener.com/ethereum/0xPool", clipboardDestinations().first().url(match))
    }

    @Test fun unsupportedChainRoutesAreDisabledAndCustomMappingsWork() {
        val match = clipboardMatches(CA, listOf(testPair("arbitrum"))).single()
        assertNull(clipboardDestinations().first { it.id == "gmgn" }.url(match))
        assertNotNull(clipboardDestinations().first { it.id == "x" }.url(match))
        val custom = ClipboardDestination("custom", "Custom", "https://example.com/{chain}/{ca}", chainValues = mapOf("arbitrum" to "arb"))
        assertEquals("https://example.com/arb/$CA", custom.url(match))
    }

    @Test fun editedDexDestinationDoesNotUseDefaultPoolRoute() {
        val match = clipboardMatches(CA, listOf(testPair())).single()
        val destination = clipboardDestinations().first().copy(template = "https://example.com/{ca}")
        assertEquals("https://example.com/$CA", destination.url(match))
    }

    @Test fun rejectsNonWebAndCredentialBearingLinks() {
        assertNull(safeClipboardUrl("javascript:alert(1)"))
        assertNull(safeClipboardUrl("intent://token"))
        assertNull(safeClipboardUrl("https://user:pass@example.com/path"))
        assertNotNull(safeClipboardUrl("https://example.com/path"))
    }

    @Test fun decodesSocialUrlsAndDropsUnsafeProjectLinks() {
        val pair = Json { ignoreUnknownKeys = true }.decodeFromString<DexScreenerPair>(
            """{"info":{"socials":[{"type":"twitter","url":"https://x.com/token"},{"type":"bad","url":"javascript:alert(1)"}]}}"""
        )
        val match = clipboardMatches(CA, listOf(testPair().copy(info = pair.info))).single()
        assertEquals(listOf("twitter" to "https://x.com/token"), match.links)
    }

    @Test fun validatesDestinationTemplatesAndMappingRows() {
        assertEquals(mapOf("solana" to "sol"), clipboardMapping("solana=sol"))
        assertTrue(runCatching { clipboardMapping("solana=sol\nsolana=solana") }.isFailure)
        assertTrue(runCatching { clipboardMapping("solana") }.isFailure)
        assertTrue(clipboardDestinations().all(::validClipboardDestination))
        assertFalse(validClipboardDestination(ClipboardDestination("c", "C", "https://example.com/{typo}")))
    }

    @Test fun chartRowsAreChronologicalAndInvalidPricesExcluded() {
        val points = clipboardChartPoints(listOf(listOf(20.0, 1.0, 1.0, 1.0, 3.0),
            listOf(10.0, 1.0, 1.0, 1.0, 2.0), listOf(0.0), listOf(30.0, 1.0, 1.0, 1.0, Double.NaN)))
        assertEquals(listOf(10.0, 20.0), points.map { it.timestamp })
        assertEquals(listOf(2.0, 3.0), points.map { it.price })
    }

    @Test fun panelGrowsBelowAndFlipsAboveAtScreenBottom() {
        assertEquals(ClipboardPanelPosition(20, 108), clipboardPanelPosition(20, 50, 100, 320, 400, 400, 800, 8))
        assertEquals(ClipboardPanelPosition(80, 292), clipboardPanelPosition(380, 700, 750, 320, 400, 400, 800, 8))
        assertEquals(ClipboardPanelPosition(0, 0), clipboardPanelPosition(-20, 0, 40, 400, 800, 400, 800, 8))
    }

    @Test fun panelMeasureUsesAvailableAndroidWindowInsteadOfDesktopFrame() {
        assertEquals(
            ClipboardPanelMeasure(width = 210, maxHeight = 416),
            clipboardPanelMeasure(
                availableWidth = 344,
                availableHeight = 784,
                fullWindowHeight = 800,
                compactBreakpoint = 220,
                largeCardCap = 210
            )
        )
        assertEquals(
            ClipboardPanelMeasure(width = 210, maxHeight = 520),
            clipboardPanelMeasure(
                availableWidth = 1000,
                availableHeight = 900,
                fullWindowHeight = 1000,
                compactBreakpoint = 220,
                largeCardCap = 210
            )
        )
        assertEquals(
            ClipboardPanelMeasure(width = 204, maxHeight = 416),
            clipboardPanelMeasure(
                availableWidth = 304,
                availableHeight = 784,
                fullWindowHeight = 800,
                compactBreakpoint = 220,
                largeCardCap = 210
            )
        )
    }

    @Test fun decodesGeckoTerminalHolderCount() {
        val response = Json { ignoreUnknownKeys = true }.decodeFromString<GeckoTerminalTokenInfoResponse>(
            """{"data":{"attributes":{"address":"$CA","holders":{"count":2874}}}}"""
        )
        assertEquals(CA, response.data.attributes.address)
        assertEquals(2_874, response.data.attributes.holders?.count)
    }

    @Test fun compactMarketCapDoesNotSubstituteMissingValues() {
        assertEquals("--", compactClipboardNumber(null))
        assertEquals("$12.3M", compactClipboardNumber(12_300_000.0))
        assertEquals("$0", compactClipboardNumber(0.0))
    }
}
