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

    @Test fun unsafeUrlsAreRejectedAndAiLiteralPlusIsEncoded() {
        assertEquals(ClipboardWebsiteRecognition.Outcome.Invalid, ClipboardWebsiteRecognition.recognize("javascript:$CA", newSite))
        assertEquals(ClipboardWebsiteRecognition.Outcome.Invalid, ClipboardWebsiteRecognition.recognize("https://user:pass@example.com/$CA", newSite))
        val url = clipboardAIPromptUrl("gpt", clipboardMatches(CA, listOf(testPair())).single(), "1+2 & x")
        assertTrue(url.contains("1%2B2%20%26%20x")); assertFalse(url.contains("Market%20cap%3A%20null"))
    }
}
