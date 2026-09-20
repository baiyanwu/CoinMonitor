package io.baiyanwu.coinmonitor.data.network

import io.baiyanwu.coinmonitor.domain.model.OkxWalletCredentials
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
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

class OkxDexMarketClientTest {
    private val fixedClock = Clock.fixed(Instant.parse("2020-12-08T09:08:57.715Z"), ZoneOffset.UTC)

    @Test
    fun `search discovers chains dynamically and maps token fields`() = runBlocking {
        val requestedPaths = mutableListOf<String>()
        val client = client { request ->
            requestedPaths += request.url.encodedPath
            when (request.url.encodedPath) {
                "/api/v5/public/time" -> jsonResponse(
                    request,
                    """{"code":"0","data":[{"ts":"1607418537715"}]}"""
                )
                "/api/v6/dex/market/supported/chain" -> jsonResponse(
                    request,
                    """{"code":"0","data":[{"chainIndex":"1","chainName":"Ethereum","chainSymbol":"ETH"}]}"""
                )
                "/api/v6/dex/market/token/search" -> jsonResponse(
                    request,
                    """{"code":"0","data":[{"chainIndex":"1","tokenName":"Target","tokenSymbol":"TGT","tokenLogoUrl":"https://example.com/tgt.png","tokenContractAddress":"0x1111111111111111111111111111111111111111","price":"2.5","change":"3.5","marketCap":"1000"}]}"""
                )
                else -> error("unexpected request ${request.url}")
            }
        }

        val token = client.searchTokens("TGT").single()

        assertEquals(
            listOf("/api/v5/public/time", "/api/v6/dex/market/supported/chain", "/api/v6/dex/market/token/search"),
            requestedPaths
        )
        assertEquals("1", token.chainIndex)
        assertEquals("TGT", token.tokenSymbol)
        assertEquals(2.5, token.price!!, 0.0)
    }

    @Test
    fun `quote request signs exact post body and maps market data`() = runBlocking {
        var captured: Request? = null
        val client = client { request ->
            if (request.url.encodedPath == "/api/v5/public/time") {
                return@client jsonResponse(
                    request,
                    """{"code":"0","data":[{"ts":"1607418537715"}]}"""
                )
            }
            captured = request
            jsonResponse(
                request,
                """{"code":"0","data":[{"chainIndex":"1","tokenContractAddress":"0x1111111111111111111111111111111111111111","price":"2.5","priceChange24H":"3.5","marketCap":"1000"}]}"""
            )
        }

        val quote = client.getQuotes(
            listOf("1" to "0x1111111111111111111111111111111111111111")
        ).single()
        val request = requireNotNull(captured)
        val body = requireNotNull(request.body).let { requestBody ->
            okio.Buffer().use { buffer -> requestBody.writeTo(buffer); buffer.readUtf8() }
        }
        val expectedSignature = OkxOnchainRequestSigner.signature(
            secretKey = "secret",
            timestamp = "2020-12-08T09:08:57.715Z",
            method = "POST",
            requestPath = "/api/v6/dex/market/price-info",
            body = body
        )

        assertEquals(expectedSignature, request.header("OK-ACCESS-SIGN"))
        assertTrue(body.contains("tokenContractAddress"))
        assertEquals(3.5, quote.change24hPercent!!, 0.0)
        assertEquals(1000.0, quote.marketCap!!, 0.0)
    }

    @Test
    fun `unconfigured client rejects authenticated requests`() = runBlocking {
        val client = OkxDexMarketClient(
            httpClient = OkHttpClient(),
            credentialsProvider = { OkxWalletCredentials() },
            clock = fixedClock
        )

        val error = runCatching { client.getSupportedChains() }.exceptionOrNull()

        assertTrue(error is OkxDexApiException)
        assertEquals("CREDENTIALS", (error as OkxDexApiException).apiCode)
    }

    private fun client(response: (Request) -> Response): OkxDexMarketClient = OkxDexMarketClient(
        httpClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain -> response(chain.request()) })
            .build(),
        credentialsProvider = { OkxWalletCredentials(true, "key", "secret", "pass") },
        clock = fixedClock
    )

    private fun jsonResponse(request: Request, body: String) = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body(body.toResponseBody("application/json".toMediaType()))
        .build()
}
