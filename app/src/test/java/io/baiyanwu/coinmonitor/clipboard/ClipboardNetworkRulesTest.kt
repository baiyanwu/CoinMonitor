package io.baiyanwu.coinmonitor.clipboard

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ClipboardNetworkRulesTest {
    private val ca = "0x0000000000000000000000000000000000000001"

    @Test fun firstRecognitionShowsKnownRulesButEnablesOnlyDetectedChain() {
        val result = (ClipboardWebsiteRecognition.recognize("https://gmgn.ai/eth/token/$ca",
            ClipboardDestination("custom", "", "")) as ClipboardWebsiteRecognition.Outcome.Recognized).result
        val site = prepareClipboardNetworkRules(result.destination, result.chain, initializeSelection = true)
        assertEquals(clipboardNetworkOptions(site).toSet(), site.chainValues.keys)
        assertEquals(setOf("ethereum"), site.chainValues.keys - site.disabledNetworkRules)
        assertNotNull(site.url(clipboardMatches(ca, listOf(testPair("ethereum", ca))).single()))
        assertNull(site.url(clipboardMatches(ca, listOf(testPair("base", ca))).single()))
    }

    @Test fun uncheckedEditedValuesSurviveSaveAndReRecognition() {
        val original = clipboardDestinations().first { it.id == "gmgn" }
            .copy(chainValues = mapOf("ethereum" to "eth", "base" to "custom"))
        val disabled = prepareClipboardNetworkRules(original).withNetworkRuleEnabled("base", false)
        val restored = Json.decodeFromString<ClipboardDestination>(Json.encodeToString(ClipboardDestination.serializer(), disabled))
        val result = (ClipboardWebsiteRecognition.recognize("https://gmgn.ai/eth/token/$ca", restored)
            as ClipboardWebsiteRecognition.Outcome.Recognized).result
        assertTrue(result.sameFormat)
        val again = prepareClipboardNetworkRules(result.destination)
        assertEquals("custom", again.chainValues["base"])
        assertTrue("base" in again.disabledNetworkRules)
        assertTrue("solana" in again.disabledNetworkRules)
        assertEquals("custom", again.withNetworkRuleEnabled("base", true).chainValues["base"])
    }

    @Test fun customSiteShowsAllNetworksButOnlyEnablesItsConfirmedMapping() {
        val result = (ClipboardWebsiteRecognition.recognize("https://gigax.fun/base/token/$ca",
            ClipboardDestination("custom", "", "")) as ClipboardWebsiteRecognition.Outcome.Recognized).result
        val site = prepareClipboardNetworkRules(result.destination, result.chain, true)
        assertEquals(clipboardNetworkOptions(site).toSet(), site.chainValues.keys)
        assertEquals("base", site.chainValues["base"])
        assertEquals(setOf("base"), site.chainValues.keys - site.disabledNetworkRules)
        assertTrue(site.chainValues.filterKeys { it != "base" }.values.all(String::isEmpty))
        assertTrue(validClipboardDestination(site))
        assertFalse(validClipboardDestination(site.withNetworkRuleEnabled("ethereum", true)))
        val edited = site.copy(chainValues = site.chainValues + ("ethereum" to "eth"))
            .withNetworkRuleEnabled("ethereum", true)
        val restored = Json.decodeFromString<ClipboardDestination>(Json.encodeToString(ClipboardDestination.serializer(), edited))
        val again = (ClipboardWebsiteRecognition.recognize("https://gigax.fun/base/token/$ca", restored)
            as ClipboardWebsiteRecognition.Outcome.Recognized).result
        val reopened = prepareClipboardNetworkRules(again.destination)
        assertEquals("eth", reopened.chainValues["ethereum"])
        assertEquals(setOf("base", "ethereum"), reopened.chainValues.keys - reopened.disabledNetworkRules)
        assertTrue(reopened.url(clipboardMatches(ca, listOf(testPair("ethereum", ca))).single())!!.contains("/eth/token/"))
    }

    @Test fun explorerRulesShowAllKnownUrlsAndEnableOnlyPastedNetwork() {
        val result = (ClipboardWebsiteRecognition.recognize("https://etherscan.io/address/$ca",
            ClipboardDestination("custom", "", "")) as ClipboardWebsiteRecognition.Outcome.Recognized).result
        val site = prepareClipboardNetworkRules(result.destination, result.chain, true)
        assertEquals(clipboardNetworkOptions(site).toSet(), site.chainTemplates.keys)
        assertEquals(setOf("ethereum"), site.chainTemplates.keys - site.disabledNetworkRules)
    }

    @Test fun uncheckedInvalidValueDoesNotBlockSavingButCheckedValueDoes() {
        val site = clipboardDestinations().first { it.id == "gmgn" }
            .copy(chainValues = mapOf("ethereum" to "eth", "base" to ""), disabledNetworkRules = setOf("base"))
        assertTrue(validClipboardDestination(site))
        assertFalse(validClipboardDestination(site.withNetworkRuleEnabled("base", true)))
        val old = Json.decodeFromString<ClipboardDestination>("""{"id":"old","name":"Old","template":"https://example.com/{ca}"}""")
        assertTrue(old.disabledNetworkRules.isEmpty())
    }

    @Test fun builtInSitesExposeAllNetworksWithoutInventingMissingAliases() {
        for (id in listOf("okx", "fomo", "ave", "gmgn")) {
            val original = clipboardDestinations().first { it.id == id }
            val site = prepareClipboardNetworkRules(original)
            assertEquals(clipboardNetworkOptions(site).toSet(), site.chainValues.keys)
            original.chainValues.forEach { (chain, value) -> assertEquals(value, site.chainValues[chain]) }
            assertEquals(original.chainValues.keys, site.chainValues.keys - site.disabledNetworkRules)
            assertTrue(site.chainValues.filterKeys { it !in original.chainValues }.values.all(String::isEmpty))
        }
    }

    @Test fun mostRecentlyCheckedNetworkIsFirstAndUncheckedRowsStayBelowCheckedRows() {
        val original = prepareClipboardNetworkRules(clipboardDestinations().first { it.id == "gmgn" })
        val baseFirst = original.withNetworkRuleEnabled("base", true)
        assertEquals("base", baseFirst.networkRuleRows().first().key)
        val ethFirst = baseFirst.withNetworkRuleEnabled("ethereum", true)
        assertEquals(listOf("ethereum", "base"), ethFirst.networkRuleRows().take(2).map { it.key })
        val unchecked = ethFirst.withNetworkRuleEnabled("ethereum", false)
        assertEquals("base", unchecked.networkRuleRows().first().key)
        assertEquals("eth", unchecked.chainValues["ethereum"])
        val restored = Json.decodeFromString<ClipboardDestination>(Json.encodeToString(ClipboardDestination.serializer(), unchecked))
        assertEquals(unchecked.networkRuleRows().map { it.key }, prepareClipboardNetworkRules(restored).networkRuleRows().map { it.key })
    }
}
