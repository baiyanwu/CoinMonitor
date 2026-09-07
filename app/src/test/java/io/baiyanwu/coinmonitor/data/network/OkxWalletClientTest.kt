package io.baiyanwu.coinmonitor.data.network

import io.baiyanwu.coinmonitor.domain.model.OkxWalletCredentials
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OkxWalletClientTest {
    @Test fun `supported chain response maps current balance chain indexes`() = kotlinx.coroutines.runBlocking {
        val client = OkxWalletClient(
            httpClient = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
                if (chain.request().url.encodedPath == "/api/v5/public/time") {
                    jsonResponse(chain.request(), """{"code":"0","data":[{"ts":"1607418537715"}]}""")
                } else {
                    jsonResponse(chain.request(), """{"code":"0","data":[{"name":"Ethereum","chainIndex":"1"},{"name":"Solana","chainIndex":"501"}]}""")
                }
            }).build(),
            credentialsProvider = { OkxWalletCredentials(true, "key", "secret", "pass") },
            clock = Clock.fixed(Instant.parse("2020-12-08T09:08:57.715Z"), ZoneOffset.UTC)
        )
        assertEquals(setOf("1", "501"), client.getSupportedChainIndexes())
    }

    @Test fun `signer produces known HMAC SHA256 Base64 value`() {
        val path = "/api/v6/dex/balance/total-value-by-address?address=0xabc&chains=1&assetType=1&excludeRiskToken=true"
        assertEquals(
            "P7QbbMSfhEDHTSr8Vg9pXKVHs5RX3+Zmhz5Nb8K+xBw=",
            OkxWalletRequestSigner.signature("secret", "2020-12-08T09:08:57.715Z", "GET", path)
        )
    }

    @Test fun `request signs final encoded query and maps token fields`() = kotlinx.coroutines.runBlocking {
        var captured: Request? = null
        val client = OkxWalletClient(
            httpClient = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
                captured = chain.request()
                jsonResponse(chain.request(), """{"code":"0","data":[{"tokenAssets":[{"chainIndex":"501","tokenContractAddress":"mint/address","symbol":"SOL","balance":"1.25","tokenPrice":"150.5","isRiskToken":true}]}]}""")
            }).build(),
            credentialsProvider = { OkxWalletCredentials(true, "key", "secret", "pass") },
            clock = Clock.fixed(Instant.parse("2020-12-08T09:08:57.715Z"), ZoneOffset.UTC)
        )
        val rows = client.getAllTokenBalances("a/b", listOf("501"))
        val request = requireNotNull(captured)
        assertEquals("a%2Fb", request.url.queryParameter("address")?.let { java.net.URLEncoder.encode(it, "UTF-8").replace("%2F", "%2F") })
        assertTrue(request.url.encodedQuery!!.contains("address=a%2Fb&chains=501&excludeRiskToken=1"))
        assertEquals("key", request.header("OK-ACCESS-KEY"))
        val expectedPath = request.url.encodedPath + "?" + request.url.encodedQuery
        assertEquals(OkxWalletRequestSigner.signature("secret", "2020-12-08T09:08:57.715Z", "GET", expectedPath), request.header("OK-ACCESS-SIGN"))
        assertEquals("mint/address", rows.single().contractAddress)
        assertTrue(rows.single().isRiskToken)
    }

    @Test fun `total request maps risk switch and token only asset type`() = kotlinx.coroutines.runBlocking {
        var captured: Request? = null
        val client = OkxWalletClient(
            httpClient = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
                captured = chain.request()
                jsonResponse(chain.request(), """{"code":"0","data":[{"totalValue":"42.01"}]}""")
            }).build(),
            credentialsProvider = { OkxWalletCredentials(true, "key", "secret", "pass") }
        )
        assertEquals("42.01", client.getTotalValue("0x0000000000000000000000000000000000000000", listOf("1"), includeRisk = false))
        assertEquals("1", captured!!.url.queryParameter("assetType"))
        assertEquals("true", captured!!.url.queryParameter("excludeRiskToken"))
    }

    @Test fun `signed requests use OKX server time offset`() = kotlinx.coroutines.runBlocking {
        var signedRequest: Request? = null
        val local = Instant.parse("2020-12-08T09:08:57.715Z")
        val server = local.plusSeconds(60)
        val client = OkxWalletClient(
            httpClient = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
                if (chain.request().url.encodedPath == "/api/v5/public/time") {
                    jsonResponse(chain.request(), """{"code":"0","data":[{"ts":"${server.toEpochMilli()}"}]}""")
                } else {
                    signedRequest = chain.request()
                    jsonResponse(chain.request(), """{"code":"0","data":[{"totalValue":"1"}]}""")
                }
            }).build(),
            credentialsProvider = { OkxWalletCredentials(true, "key", "secret", "pass") },
            clock = Clock.fixed(local, ZoneOffset.UTC)
        )
        client.getTotalValue("0x0000000000000000000000000000000000000000", listOf("1"), false)
        assertEquals("2020-12-08T09:09:57.715Z", signedRequest!!.header("OK-ACCESS-TIMESTAMP"))
    }

    private fun jsonResponse(request: Request, body: String) = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
        .body(body.toResponseBody("application/json".toMediaType())).build()
}
