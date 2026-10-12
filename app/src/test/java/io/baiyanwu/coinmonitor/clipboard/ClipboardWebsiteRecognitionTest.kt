package io.baiyanwu.coinmonitor.clipboard

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ClipboardWebsiteRecognitionTest {
    private val newSite = ClipboardDestination("custom", "", "")
    private fun recognize(url: String, existing: ClipboardDestination = newSite): ClipboardWebsiteRecognition.Result =
        (ClipboardWebsiteRecognition.recognize(url, existing) as ClipboardWebsiteRecognition.Outcome.Recognized).result

    @Test fun knownRouteImportsOnlyVerifiedMappingsAndPreservesUserEdits() {
        val first = recognize("https://gmgn.ai/eth/token/$CA")
        assertEquals("ethereum", first.chain)
        assertEquals("GMGN", first.destination.name)
        assertEquals("sol", first.destination.chainValues["solana"])
        val edited = first.destination.copy(name = "研究站", enabled = false,
            chainValues = first.destination.chainValues - "solana" + ("base" to "custom"), removedNetworkRules = setOf("solana"))
        val again = recognize("https://gmgn.ai/eth/token/$CA", edited).destination
        assertEquals("研究站", again.name); assertFalse(again.enabled)
        assertEquals("custom", again.chainValues["base"]); assertFalse(again.chainValues.containsKey("solana"))
    }

    @Test fun unknownWebsiteNeverInfersOtherNetworks() {
        val result = recognize("https://example.com/base/token/$CA").destination
        assertEquals(mapOf("base" to "base"), result.chainValues)
        assertNull(result.url(clipboardMatches(CA, listOf(testPair("ethereum"))).single()))
        val caOnly = recognize("https://example.com/token/$CA").destination
        assertTrue(caOnly.chainValues.isEmpty())
        assertNotNull(caOnly.url(clipboardMatches(CA, listOf(testPair("ethereum"))).single()))
    }

    @Test fun multipleAddressesRequireExplicitSelectionAndNeverExtractFromHost() {
        val second = "0x" + "a".repeat(40)
        val url = "https://example.com/token/$CA?quote=$second"
        assertTrue(ClipboardWebsiteRecognition.recognize(url, newSite) is ClipboardWebsiteRecognition.Outcome.Ambiguous)
        val selected = ClipboardWebsiteRecognition.recognize(url, newSite, CA) as ClipboardWebsiteRecognition.Outcome.Recognized
        assertTrue(selected.result.destination.template.contains("{ca}")); assertTrue(selected.result.destination.template.contains(second))
        assertEquals(ClipboardWebsiteRecognition.Outcome.NoAddress, ClipboardWebsiteRecognition.recognize("https://$CA.example.com/", newSite))
    }

    @Test fun explorerRulesAndDeletedMappingsSurviveRecognition() {
        val first = recognize("https://etherscan.io/address/$CA").destination
        assertTrue(first.template.isEmpty()); assertEquals("https://etherscan.io/address/{ca}", first.chainTemplates["ethereum"])
        val existing = first.copy(chainTemplates = first.chainTemplates + ("base" to "https://basescan.org/address/{ca}"))
        val again = recognize("https://etherscan.io/address/$CA", existing).destination
        assertEquals(existing.chainTemplates, again.chainTemplates)
        val deleted = first.copy(chainTemplates = emptyMap(), removedNetworkRules = setOf("ethereum"))
        assertTrue(validClipboardDestination(deleted))
        val restored = recognize("https://etherscan.io/address/0x" + "a".repeat(40), deleted).destination
        assertTrue(restored.chainTemplates.isEmpty()); assertEquals(setOf("ethereum"), restored.removedNetworkRules)
    }

    @Test fun oldConfigurationDecodesWithoutResettingNamesOrOrder() {
        val settings = Json.decodeFromString<ClipboardSettings>("""{"enabled":false,"destinations":[{"id":"custom","name":"My site","template":"https://example.com/{ca}","enabled":false}]}""")
        assertFalse(settings.enabled); assertEquals("custom", settings.destinations.single().id)
        assertFalse(settings.destinations.single().enabled); assertEquals("price", settings.valueMode)
    }

    @Test fun aveSuffixRecognizesNetworkAndPreservesManualRules() {
        val first = recognize("https://ave.ai/token/$CA-arc")
        assertEquals("arc", first.chain)
        assertEquals("https://ave.ai/token/{ca}-{chain}", first.destination.template)
        assertEquals("sol", first.destination.chainValues["solana"])
        val edited = first.destination.copy(chainValues = first.destination.chainValues - "bsc" + ("ethereum" to "custom"),
            removedNetworkRules = setOf("bsc"))
        val again = recognize("https://ave.ai/token/$CA-eth", edited).destination
        assertEquals("custom", again.chainValues["ethereum"])
        assertFalse(again.chainValues.containsKey("bsc"))
        val unknown = recognize("https://example.com/token/$CA-arc")
        assertNull(unknown.chain)
        assertEquals("https://example.com/token/{ca}-arc", unknown.destination.template)
    }

    @Test fun newBuiltInRoutesImportTheirNetworkRules() {
        val sol = "So11111111111111111111111111111111111111112"
        for ((id, url, chain) in listOf(
            Triple("pump", "https://pump.fun/coin/$sol", null),
            Triple("axiom", "https://axiom.trade/token/$sol", null),
            Triple("debot", "https://debot.ai/token/bsc/$CA", "bsc"),
            Triple("birdeye", "https://birdeye.so/bsc/token/$CA", "bsc")
        )) {
            val result = recognize(url)
            val default = clipboardDestinations().first { it.id == id }
            assertEquals(chain, result.chain)
            assertEquals(default.name, result.destination.name)
            assertEquals(default.template, result.destination.template)
            assertEquals(default.chainTemplates, result.destination.chainTemplates)
            assertEquals(default.chainValues, result.destination.chainValues)
        }
    }

    @Test fun newDefaultsAreAppendedOnceWithoutResettingExistingConfiguration() {
        val custom = ClipboardDestination("custom", "My site", "https://example.com/{ca}", enabled = false)
        val ave = clipboardDestinations().first { it.id == "ave" }.copy(name = "My Ave", enabled = false,
            chainValues = mapOf("bsc" to "custom"), removedNetworkRules = setOf("solana"))
        val old = ClipboardSettings(enabled = false, destinations = listOf(custom, ave), valueMode = "market_cap", aiQuestion = "Research")
        val migrated = migrateClipboardDestinations(old)
        assertEquals(listOf(custom, ave), migrated.destinations.take(2))
        assertEquals(listOf("custom", "ave", "pump", "axiom", "debot", "birdeye"), migrated.destinations.map { it.id })
        assertEquals(old.copy(destinations = migrated.destinations, destinationsVersion = 1), migrated)
        val deleted = migrated.copy(destinations = migrated.destinations.filterNot { it.id == "pump" })
        val reloaded = Json.decodeFromString<ClipboardSettings>(Json.encodeToString(ClipboardSettings.serializer(), deleted))
        assertEquals(deleted, migrateClipboardDestinations(reloaded))
        assertEquals(12, migrateClipboardDestinations(ClipboardSettings()).destinations.size)
    }

    @Test fun migrationDoesNotDuplicatePreviouslyAddedCustomRoutes() {
        val pump = ClipboardDestination("user-pump", "My Pump", "https://pump.fun/coin/{ca}")
        val ave = clipboardDestinations().first { it.id == "ave" }.copy(id = "user-ave", name = "My Ave")
        val migrated = migrateClipboardDestinations(ClipboardSettings(destinations = listOf(pump, ave)))
        assertEquals(listOf(pump, ave), migrated.destinations.take(2))
        assertFalse(migrated.destinations.any { it.id in setOf("pump", "ave") })
        assertEquals(5, migrated.destinations.size)
    }

    @Test fun caOnlyLegacyRoutesMigrateEvenAfterDefaultsWereAdded() {
        val pump = ClipboardDestination("user-pump", "My Pump", "", enabled = false,
            chainTemplates = mapOf("solana" to "https://pump.fun/coin/{ca}"))
        val deleted = pump.copy(id = "deleted", chainTemplates = emptyMap(), removedNetworkRules = setOf("solana"))
        val custom = pump.copy(id = "custom", chainTemplates = pump.chainTemplates + ("base" to "https://example.com/{ca}"))
        val saved = ClipboardSettings(destinations = listOf(pump, deleted, custom), destinationsVersion = 1)
        val migrated = migrateClipboardDestinations(saved)
        assertEquals(pump.copy(template = "https://pump.fun/coin/{ca}", chainTemplates = emptyMap()), migrated.destinations[0])
        assertEquals(deleted, migrated.destinations[1])
        assertEquals(custom, migrated.destinations[2])
        assertEquals(migrated, migrateClipboardDestinations(migrated))
    }

    @Test fun caOnlySitesHaveNoNetworkMappingsAfterRecognition() {
        val sol = "So11111111111111111111111111111111111111112"
        for (id in listOf("pump", "axiom")) {
            val site = clipboardDestinations().first { it.id == id }
            val result = recognize(site.template.replace("{ca}", sol), site)
            assertNull(result.chain)
            assertTrue(result.destination.template.contains("{ca}"))
            assertFalse(result.destination.template.contains("{chain}"))
            assertTrue(result.destination.chainTemplates.isEmpty())
            assertTrue(result.destination.chainValues.isEmpty())
        }
    }

    @Test fun networkChoicesIncludeCommonAndSavedCustomKeysWithoutDuplicates() {
        val site = newSite.copy(chainValues = mapOf("custom-chain" to "custom"), removedNetworkRules = setOf("deleted-chain"))
        val choices = clipboardNetworkOptions(site)
        assertTrue(choices.containsAll(listOf("ethereum", "solana", "base", "robinhood", "arc", "monad", "custom-chain", "deleted-chain")))
        assertEquals(choices.distinct().sorted(), choices)
    }

    @Test fun unsafeUrlsAreRejectedAndAiLiteralPlusIsEncoded() {
        assertEquals(ClipboardWebsiteRecognition.Outcome.Invalid, ClipboardWebsiteRecognition.recognize("javascript:$CA", newSite))
        assertEquals(ClipboardWebsiteRecognition.Outcome.Invalid, ClipboardWebsiteRecognition.recognize("https://user:pass@example.com/$CA", newSite))
        val url = clipboardAIPromptUrl("gpt", clipboardMatches(CA, listOf(testPair())).single(), "1+2 & x")
        assertTrue(url.contains("1%2B2%20%26%20x")); assertFalse(url.contains("Market%20cap%3A%20null"))
    }
}
