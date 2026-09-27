package io.baiyanwu.coinmonitor.data.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Test

class WalletRpcClientTest {
    @Test fun `transaction preflight reads EVM and Solana token balances from RPC`() = kotlinx.coroutines.runBlocking {
        val seenMethods = mutableListOf<String>()
        val http = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            val requestBody = Buffer().also { chain.request().body!!.writeTo(it) }.readUtf8()
            val method = Json.parseToJsonElement(requestBody).jsonObject.getValue("method").jsonPrimitive.content
            seenMethods += method
            val result = if (method == "eth_call") {
                "\"0x64\""
            } else {
                """{"value":{"amount":"200","decimals":6,"uiAmountString":"0.0002"}}"""
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"jsonrpc":"2.0","id":1,"result":$result}""".toResponseBody("application/json".toMediaType()))
                .build()
        }).build()
        val client = WalletRpcClient(http)

        assertEquals("0x64", client.evmTokenBalance("https://rpc.example", "0xtoken", "0xabc"))
        assertEquals("200", client.solanaTokenBalance("https://rpc.example", "token-account"))
        assertEquals(listOf("eth_call", "getTokenAccountBalance"), seenMethods)
    }
}
