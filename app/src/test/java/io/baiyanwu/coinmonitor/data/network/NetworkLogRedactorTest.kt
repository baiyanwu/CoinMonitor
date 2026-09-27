package io.baiyanwu.coinmonitor.data.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkLogRedactorTest {
    @Test
    fun `redacts alchemy keys from rpc and portfolio paths`() {
        val secret = "alchemy-secret-value"
        val values = listOf(
            "https://eth-mainnet.g.alchemy.com/v2/$secret",
            "https://api.g.alchemy.com/data/v1/$secret/assets/tokens/by-address",
            "request failed for https://api.g.alchemy.com/prices/v1/$secret/tokens"
        )

        values.forEach { value ->
            val redacted = NetworkLogRedactor.redactUrl(value)
            assertFalse(secret in redacted)
            assertTrue("***" in redacted)
        }
    }

    @Test
    fun `redacts sensitive json and query values`() {
        val secret = "do-not-log-this"
        assertFalse(secret in NetworkLogRedactor.redactText("{\"apiKey\":\"$secret\"}"))
        assertFalse(secret in NetworkLogRedactor.redactUrl("https://example.test/rpc?apiKey=$secret"))
    }
}
