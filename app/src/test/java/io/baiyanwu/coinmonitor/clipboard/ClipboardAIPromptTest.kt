package io.baiyanwu.coinmonitor.clipboard

import io.baiyanwu.coinmonitor.data.network.*
import org.junit.Assert.*
import org.junit.Test
import java.net.URI
import java.net.URLDecoder

class ClipboardAIPromptTest {
    private fun prompt(url: String): String = URLDecoder.decode(URI(url).rawQuery.substringAfter("q="), "UTF-8")

    @Test fun bothProvidersReceiveMacContextAndLiteralQuestion() {
        val info = DexScreenerInfo(
            websites = listOf(DexScreenerWebsite("Website", "https://example.com/?a=1&b=2"),
                DexScreenerWebsite("Docs", "https://docs.example.com")),
            socials = listOf(DexScreenerSocial(type = "twitter", url = "https://x.com/TokenProject"),
                DexScreenerSocial(type = "twitter", url = "https://x.com/search?q=TKN"),
                DexScreenerSocial(type = "twitter", url = "https://fake.example/Profile"))
        )
        val pair = testPair().copy(info = info, marketCap = 7829420.25,
            url = "https://dexscreener.com/ethereum/0xPool")
        val match = clipboardMatches(CA, listOf(pair)).single()
        val question = "Research & + # % {ca}\nhttps://example.com/?a=1&b=2"
        val urls = listOf("gpt", "grok").map { clipboardAIPromptUrl(it, match, question) }
        assertEquals("chatgpt.com", URI(urls[0]).host)
        assertEquals("grok.com", URI(urls[1]).host)
        assertEquals(prompt(urls[0]), prompt(urls[1]))
        val text = prompt(urls[0])
        assertTrue(text.contains("Token: Token (TKN)"))
        assertTrue(text.contains("Contract address: $CA"))
        assertTrue(text.contains("Price (USD): 2.0"))
        assertTrue(text.contains("Market cap (USD): 7,829,420.25"))
        assertTrue(text.contains("Pool address: 0xPool"))
        assertTrue(text.contains("Market: https://dexscreener.com/ethereum/0xPool"))
        assertTrue(text.contains("Website: https://example.com/?a=1&b=2"))
        assertTrue(text.contains("X: https://x.com/TokenProject"))
        assertFalse(text.contains("search?q="))
        assertFalse(text.contains("docs.example.com"))
        assertFalse(text.contains("fake.example"))
        assertTrue(text.endsWith("\n\n$question"))
        assertTrue(urls.all { it.contains("%2B") })
    }

    @Test fun quoteSideDoesNotInheritBaseMarketCapOrProjectLinks() {
        val pair = testPair()
        val match = clipboardMatches(pair.quoteToken.address, listOf(pair)).single()
        val text = prompt(clipboardAIPromptUrl("gpt", match, ""))
        assertTrue(text.contains("Market cap (USD): Unknown"))
        assertFalse(text.contains("Website:"))
        assertFalse(text.contains("X:"))
        assertTrue(text.contains("Pool address: 0xPool"))
    }

    @Test fun invalidMetricsAreUnknownAndUnsafeMarketLinkFallsBackToSelectedPool() {
        val match = clipboardMatches(CA, listOf(testPair().copy(url = "javascript:bad"))).single()
        val invalid = match.copy(selection = match.selection.copy(priceUsd = Double.NaN,
            tokenName = "", tokenSymbol = "", pair = match.selection.pair.copy(marketCap = Double.POSITIVE_INFINITY)))
        val text = prompt(clipboardAIPromptUrl("grok", invalid, ""))
        assertTrue(text.contains("Token: Unknown (Unknown)"))
        assertTrue(text.contains("Price (USD): Unknown"))
        assertTrue(text.contains("Market cap (USD): Unknown"))
        assertTrue(text.contains("Market: https://dexscreener.com/ethereum/0xPool"))
        assertFalse(text.contains("javascript:"))
        assertFalse(text.contains("NaN"))
        assertFalse(text.contains("Infinity"))
        assertFalse(text.endsWith("\n"))
    }

    @Test fun localizedLabelsAreUsedWithoutRewritingSavedQuestion() {
        val match = clipboardMatches(CA, listOf(testPair())).single()
        val labels = ClipboardAIPromptLabels(token = "代币", contract = "合约地址", website = "官网")
        val question = "  我的提问 + &\n第二行  "
        val text = prompt(clipboardAIPromptUrl("gpt", match, question, labels))
        assertTrue(text.startsWith("代币: Token (TKN)"))
        assertTrue(text.contains("合约地址: $CA"))
        assertTrue(text.contains("官网: https://example.com"))
        assertTrue(text.endsWith(question))
    }
}
