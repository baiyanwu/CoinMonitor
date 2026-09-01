package io.baiyanwu.coinmonitor.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkLogRedactorTest {
    @Test
    fun `redacts credentials and session headers case insensitively`() {
        val sensitiveHeaders = listOf(
            "Authorization",
            "Proxy-Authorization",
            "Cookie",
            "Set-Cookie",
            "X-API-Key",
            "OK-ACCESS-KEY",
            "ok-access-sign",
            "Ok-Access-Passphrase"
        )

        sensitiveHeaders.forEach { name ->
            assertEquals("***", NetworkLogRedactor.redactHeaderValue(name, "top-secret"))
        }
        assertEquals(
            "application/json",
            NetworkLogRedactor.redactHeaderValue("Content-Type", "application/json")
        )
    }

    @Test
    fun `redacts credential fields from json log text`() {
        val raw = """{"apiKey":"key-value","passphrase":"pass-value","sign":"signature-value","channel":"price"}"""

        val redacted = NetworkLogRedactor.redactText(raw)

        assertFalse(redacted.contains("key-value"))
        assertFalse(redacted.contains("pass-value"))
        assertFalse(redacted.contains("signature-value"))
        assertTrue(redacted.contains("\"apiKey\":\"***\""))
        assertTrue(redacted.contains("\"channel\":\"price\""))
    }
}
