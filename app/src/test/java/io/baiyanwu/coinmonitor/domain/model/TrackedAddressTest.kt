package io.baiyanwu.coinmonitor.domain.model

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class TrackedAddressTest {
    private val address = "0x" + "aB".repeat(20)

    @Test fun sameEvmAddressAcrossChainsMergesWithoutResettingValueOrOrder() {
        val original = TrackedAddress(address, listOf("1"), 100, "123")
        val entries = mergeTrackedAddress(listOf(original), address.lowercase(), "8453", 200)
        assertEquals(1, entries.size)
        assertEquals(listOf("1", "8453"), entries.single().chains)
        assertEquals("123", entries.single().totalValueUsd)
        assertEquals(100L, entries.single().addedAtMillis)
        assertEquals(entries, mergeTrackedAddress(entries, address, "8453", 300))
    }

    @Test fun differentAddressesRemainSeparateAndInvalidChainsAreRejected() {
        val entries = mergeTrackedAddress(emptyList(), address, "1", 100)
        assertEquals(2, mergeTrackedAddress(entries, "0x" + "c".repeat(40), "56", 200).size)
        assertEquals(entries, mergeTrackedAddress(entries, address, "501", 200))
        assertEquals(entries, mergeTrackedAddress(entries, "invalid", "1", 200))
        assertNotEquals(trackedAddressKey("CaseSensitive"), trackedAddressKey("casesensitive"))
    }

    @Test fun portfolioTotalIsNotDuplicatedAcrossChainsAndUnpricedHoldingsAreMarked() {
        val snapshot = WalletPortfolioSnapshot(address, WalletAddressKind.EVM, listOf(
            asset("1", "eth", "10"), asset("8453", "usdc", "20"), asset("8453", "unknown", null),
            asset("56", "scam", "900", true)
        ), BigDecimal("30"), true, 200)
        val entry = TrackedAddress(address, listOf("1", "56"), 100).withPortfolio(snapshot)
        assertEquals("30", entry.totalValueUsd)
        assertEquals(mapOf("1" to "10", "56" to "0", "8453" to "20"), entry.chainValuesUsd)
        assertEquals(setOf("8453"), entry.estimatedChains)
        assertTrue(entry.totalIsEstimated)
        assertEquals(entry, Json.decodeFromString<TrackedAddress>(Json.encodeToString(entry)))
        val empty = entry.withPortfolio(snapshot.copy(assets = emptyList(), totalValueUsd = BigDecimal.ZERO))
        assertTrue(empty.chainValuesUsd.values.all { it == "0" })
    }

    @Test(expected = IllegalArgumentException::class)
    fun snapshotFromAnotherAddressCannotOverwriteTrackedAddress() {
        TrackedAddress(address, listOf("1"), 100).withPortfolio(
            WalletPortfolioSnapshot("0x" + "c".repeat(40), WalletAddressKind.EVM, emptyList(), BigDecimal.ZERO, false, 200)
        )
    }

    private fun asset(chain: String, contract: String, value: String?, risk: Boolean = false) = WalletAsset(
        chain, chain, contract, contract, BigDecimal.ONE, value?.toBigDecimal(), value?.toBigDecimal(), risk
    )
}
