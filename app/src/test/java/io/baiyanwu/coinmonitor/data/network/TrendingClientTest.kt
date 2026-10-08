package io.baiyanwu.coinmonitor.data.network

import io.baiyanwu.coinmonitor.domain.model.*
import kotlinx.serialization.json.Json
import kotlinx.coroutines.runBlocking
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class TrendingClientTest {
    private val address = "0x" + "a".repeat(40)
    private fun decode(text: String) = Json.parseToJsonElement(text)
    private val gmgnRow get() = """{"chain":"base","address":"$address","symbol":"TEST","market_cap":"1200","volume":"50","price_change_percent":"-2.5"}"""

    @Test fun `gmgn accepts direct and double envelopes and rejects conflicting chains`() {
        val direct = """{"code":0,"data":{"rank":[$gmgnRow]}}"""
        val wrapped = """{"code":0,"data":$direct}"""
        assertEquals(TrendingDecoder.gmgn(decode(direct), TrendingChain.BASE), TrendingDecoder.gmgn(decode(wrapped), TrendingChain.BASE))
        val row = TrendingDecoder.gmgn(decode(direct), TrendingChain.BASE).single()
        assertEquals(1200.0, row.marketCap!!, 0.0)
        assertEquals(-2.5, row.change!!, 0.0)
        assertTrue(TrendingDecoder.gmgn(decode(direct), TrendingChain.BSC).isEmpty())
    }
    @Test fun `gmgn hot search uses block chain and period while rejecting row conflicts`() {
        val row = """{"address":"$address","symbol":"TEST","visiting_count":12}"""
        val json = """{"code":0,"data":[{"chain":"base","interval":"5m","tokens":[$row,$gmgnRow]},{"chain":"bsc","interval":"5m","tokens":[$gmgnRow]},{"chain":"base","interval":"1h","tokens":[$row]}]}"""
        assertEquals(1, TrendingDecoder.hotSearch(decode(json), TrendingOptions(chain = null)).size)
        assertTrue(TrendingDecoder.hotSearch(decode(json), TrendingOptions(chain = TrendingChain.BSC)).isEmpty())
    }
    @Test fun `okx normalizes evm addresses but keeps chains separate`() {
        val json = """{"code":"0","data":[{"chainIndex":"8453","tokenContractAddress":"$address","tokenSymbol":"A","riskLevelControl":"4"},{"chainIndex":"8453","tokenContractAddress":"0x${"A".repeat(40)}","tokenSymbol":"B"},{"chainIndex":"56","tokenContractAddress":"$address","tokenSymbol":"C"}]}"""
        val rows = TrendingDecoder.okx(decode(json), null)
        assertEquals(2, rows.size)
        assertTrue(rows.first().risk)
        assertEquals(1, TrendingDecoder.okx(decode(json), TrendingChain.BASE).size)
    }
    @Test fun `pump uses usd market cap and leaves unavailable metrics unknown`() {
        val sol = "So11111111111111111111111111111111111111112"
        val json = """[{"mint":"$sol","name":"Wrapped","symbol":"SOL","market_cap":100000,"market_cap_usd":"1234","image_uri":"javascript:alert(1)"},{"mint":"$sol","name":"duplicate","symbol":"SOL"},{"chain_id":"other","mint":"bad"}]"""
        val token = TrendingDecoder.pump(decode(json)).single()
        assertEquals(1234.0, token.marketCap!!, 0.0)
        assertNull(token.price); assertNull(token.change); assertNull(token.iconUrl)
    }
    @Test fun `nonfinite numbers remain unknown`() {
        val row = gmgnRow.replace("1200", "NaN")
        assertNull(TrendingDecoder.gmgn(decode("""{"code":0,"data":{"rank":[$row]}}"""), TrendingChain.BASE).single().marketCap)
    }
    @Test fun `pump price uses circulating supply with token decimals`() {
        val sol = "So11111111111111111111111111111111111111112"
        fun price(supply: String, decimals: Int) = TrendingDecoder.pump(decode(
            """[{"mint":"$sol","name":"Test","symbol":"TEST","usd_market_cap":1200,"circulating_supply_str":"$supply","base_decimals":$decimals}]"""
        )).single().price
        assertEquals(0.0000012, price("1000000000000000", 6)!!, 1e-15)
        assertEquals(0.0000024, price("500000000000000", 6)!!, 1e-15)
        assertEquals(0.0000012, price("1000000000000000000", 9)!!, 1e-15)
    }
    @Test fun `pump price stays unknown with missing invalid or zero supply`() {
        val sol = "So11111111111111111111111111111111111111112"
        for ((supply, decimals) in listOf("0" to "6", "-1" to "6", "1.5" to "6", "NaN" to "6", "1000" to "-1", "1000" to "19", "1000" to "6.5", "1000" to "null")) {
            val row = """[{"mint":"$sol","name":"Test","symbol":"TEST","usd_market_cap":1200,"circulating_supply_str":"$supply","base_decimals":$decimals}]"""
            assertNull(TrendingDecoder.pump(decode(row)).single().price)
        }
    }
    @Test fun `pump request carries no credentials`() {
        val request = TrendingClient.pumpRequest()
        assertEquals("50", request.url.queryParameter("topK"))
        assertNull(request.header("X-APIKEY")); assertNull(request.header("OK-ACCESS-KEY"))
    }
    @Test fun `gmgn requests preserve filters and hot search parameters`() {
        val request = TrendingClient.gmgnRequest(TrendingChain.SOLANA, "1m", "fixture-key", 100000)
        assertEquals(listOf("renounced", "frozen"), request.url.queryParameterValues("filters"))
        assertEquals("volume", request.url.queryParameter("order_by"))
        assertEquals("100", request.url.queryParameter("timestamp"))
        assertEquals("fixture-key", request.header("X-APIKEY"))
        val hot = TrendingClient.gmgnHotSearchRequest(TrendingOptions(chain = null), "fixture-key")
        val buffer = okio.Buffer(); hot.body!!.writeTo(buffer)
        val text = buffer.readUtf8()
        assertTrue(text.contains("hot-search")); assertTrue(text.contains("robinhood")); assertFalse(text.contains("fixture-key"))
        assertEquals("POST", hot.method)
    }
    @Test fun `okx signs exact encoded query and uses search rank mapping`() {
        val credentials = OkxWalletCredentials(true, "fixture-key", "fixture-secret", "fixture-passphrase")
        val request = TrendingClient.okxRequest(TrendingOptions(chain = TrendingChain.BASE, period = "4h", mode = TrendingMode.HOT_SEARCH), credentials, 123456789)
        assertEquals("3", request.url.queryParameter("rankingTimeFrame"))
        assertEquals("9", request.url.queryParameter("rankBy"))
        assertEquals("8453", request.url.queryParameter("chainIndex"))
        assertEquals(OkxOnchainRequestSigner.signature(credentials.secretKey, request.header("OK-ACCESS-TIMESTAMP")!!, "GET",
            request.url.encodedPath + "?" + request.url.encodedQuery), request.header("OK-ACCESS-SIGN"))
        assertNull(TrendingClient.okxRequest(TrendingOptions(chain = null), credentials).url.queryParameter("chainIndex"))
    }
    @Test fun `wrapped gmgn rate limit honors latest reset and retry after`() {
        val error = failure { TrendingClient.validate(TrendingSource.GMGN, 200, Headers.Builder().add("Retry-After", "80").build(),
            decode("""{"code":0,"data":{"code":429,"reset_at":60}}"""), 1000) }
        assertEquals(TrendingFailure.RATE_LIMIT, error.failure)
        assertEquals(81000, error.retryAt)
    }
    @Test fun `rate limit without reset applies source fallback`() {
        assertEquals(301000, failure { TrendingClient.validate(TrendingSource.GMGN, 429, Headers.Builder().build(), null, 1000) }.retryAt)
        assertEquals(61000, failure { TrendingClient.validate(TrendingSource.PUMP, 429, Headers.Builder().build(), null, 1000) }.retryAt)
    }
    @Test fun `okx authentication quota and error envelopes never become empty success`() {
        assertEquals(TrendingFailure.AUTHENTICATION, failure { TrendingClient.validate(TrendingSource.OKX, 200, Headers.Builder().build(), decode("""{"code":"50111"}"""), 0) }.failure)
        assertEquals(TrendingFailure.QUOTA, failure { TrendingClient.validate(TrendingSource.OKX, 402, Headers.Builder().build(), null, 0) }.failure)
        assertEquals(TrendingFailure.INVALID_DATA, failure { TrendingClient.validate(TrendingSource.GMGN, 200, Headers.Builder().build(), decode("""{"code":100}"""), 0) }.failure)
    }
    @Test fun `gmgn api header is redacted regardless of casing`() {
        assertEquals("***", NetworkLogRedactor.redactHeaderValue("X-APIKEY", "fixture-key"))
        assertEquals("***", NetworkLogRedactor.redactHeaderValue("x-apikey", "fixture-key"))
    }
    @Test fun `changing chain cannot bypass physical request spacing`() = runBlocking {
        val started = java.util.concurrent.CopyOnWriteArrayList<Long>()
        val client = TrendingClient(OkHttpClient.Builder().addInterceptor { chain ->
            started += System.nanoTime()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"code":0,"data":{"rank":[]}}""".toResponseBody()).build()
        }.build())
        client.fetch(TrendingSource.GMGN, TrendingOptions(chain = TrendingChain.BASE), "fixture-key", OkxWalletCredentials())
        client.fetch(TrendingSource.GMGN, TrendingOptions(chain = TrendingChain.BSC), "fixture-key", OkxWalletCredentials())
        assertEquals(2, started.size)
        assertTrue("Changed chain should still wait between requests", (started[1] - started[0]) / 1_000_000 >= 1900)
    }
    private fun failure(block: () -> Unit): TrendingException {
        try { block() } catch (error: TrendingException) { return error }
        error("Expected a TrendingException")
    }
}
