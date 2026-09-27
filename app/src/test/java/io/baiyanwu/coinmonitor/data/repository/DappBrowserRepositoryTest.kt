package io.baiyanwu.coinmonitor.data.repository

import io.baiyanwu.coinmonitor.domain.model.WalletNetwork
import io.baiyanwu.coinmonitor.domain.model.WalletNetworkConfiguration
import io.baiyanwu.coinmonitor.domain.repository.WalletNetworkSettingsRepository
import java.math.BigInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DappBrowserRepositoryTest {
    @Test
    fun `prepares BSC contract transaction with RPC nonce estimate and fee`() = runBlocking {
        val seenMethods = mutableListOf<String>()
        val http = rpcClient(seenMethods)
        val repository = DappBrowserRepository(TestNetworkSettings(), http)
        val from = "0x1111111111111111111111111111111111111111"
        val to = "0x2222222222222222222222222222222222222222"

        val transaction = repository.prepareTransaction(
            network = WalletNetwork.BNB_CHAIN,
            expectedAddress = from,
            params = buildJsonObject {
                put("from", from)
                put("to", to)
                put("chainId", "0x38")
                put("value", "0x1")
                put("data", "0x1234")
                put("gas", "0x6000")
            }
        )

        assertEquals(to, transaction.to)
        assertEquals(BigInteger.ONE, transaction.valueAtomic)
        assertEquals(BigInteger("6000", 16) * BigInteger("3b9aca00", 16), transaction.maximumFeeAtomic)
        assertEquals(listOf("eth_getTransactionCount", "eth_estimateGas", "eth_gasPrice", "eth_getBalance"), seenMethods)
    }

    @Test
    fun `rejects transaction for another chain before RPC`() = runBlocking {
        val seenMethods = mutableListOf<String>()
        val repository = DappBrowserRepository(TestNetworkSettings(), rpcClient(seenMethods))
        val error = runCatching {
            repository.prepareTransaction(
                network = WalletNetwork.BNB_CHAIN,
                expectedAddress = "0x1111111111111111111111111111111111111111",
                params = buildJsonObject {
                    put("from", "0x1111111111111111111111111111111111111111")
                    put("to", "0x2222222222222222222222222222222222222222")
                    put("chainId", "0x1")
                }
            )
        }.exceptionOrNull()

        assertTrue(error?.message?.contains("Chain ID") == true)
        assertTrue(seenMethods.isEmpty())
    }

    @Test
    fun `rejects oversized chain id instead of truncating it`() = runBlocking {
        val seenMethods = mutableListOf<String>()
        val repository = DappBrowserRepository(TestNetworkSettings(), rpcClient(seenMethods))
        val error = runCatching {
            repository.prepareTransaction(
                network = WalletNetwork.BNB_CHAIN,
                expectedAddress = "0x1111111111111111111111111111111111111111",
                params = buildJsonObject {
                    put("from", "0x1111111111111111111111111111111111111111")
                    put("to", "0x2222222222222222222222222222222222222222")
                    put("chainId", "0x10000000000000038")
                }
            )
        }.exceptionOrNull()

        assertTrue(error?.message?.contains("Chain ID") == true)
        assertTrue(seenMethods.isEmpty())
    }

    private fun rpcClient(seenMethods: MutableList<String>) = OkHttpClient.Builder()
        .addInterceptor(Interceptor { chain ->
            val raw = Buffer().also { chain.request().body!!.writeTo(it) }.readUtf8()
            val method = Json.parseToJsonElement(raw).jsonObject.getValue("method").jsonPrimitive.content
            seenMethods += method
            val result = when (method) {
                "eth_getTransactionCount" -> "\"0x7\""
                "eth_estimateGas" -> "\"0x5208\""
                "eth_gasPrice" -> "\"0x3b9aca00\""
                "eth_getBalance" -> "\"0xde0b6b3a7640000\""
                else -> error("Unexpected RPC method $method")
            }
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"jsonrpc":"2.0","id":1,"result":$result}""".toResponseBody(JSON_MEDIA))
                .build()
        })
        .build()

    private class TestNetworkSettings : WalletNetworkSettingsRepository {
        private val config = WalletNetworkConfiguration(
            customRpcUrls = mapOf(WalletNetwork.BNB_CHAIN.id to "https://bsc.example")
        )

        override fun observe(): Flow<WalletNetworkConfiguration> = flowOf(config)
        override fun get(): WalletNetworkConfiguration = config
        override fun isSecureStorageAvailable(): Boolean = true
        override suspend fun save(configuration: WalletNetworkConfiguration) = Unit
        override suspend fun saveAlchemyApiKey(apiKey: String) = Unit
        override suspend fun saveCustomRpc(network: WalletNetwork, url: String) = Unit
        override suspend fun clearCustomRpc(network: WalletNetwork) = Unit
        override suspend fun validateRpc(network: WalletNetwork, url: String): Result<Unit> = Result.success(Unit)
        override suspend fun refreshNetworkCatalog(): Result<List<WalletNetwork>> = Result.success(emptyList())
        override suspend fun setNetworkEnabled(networkId: String, enabled: Boolean) = Unit
        override suspend fun addCustomEvmNetwork(
            name: String,
            chainId: Long,
            symbol: String,
            explorerUrl: String,
            rpcUrl: String
        ): WalletNetwork = error("Not used")
        override suspend fun removeCustomEvmNetwork(networkId: String) = Unit
    }

    private companion object {
        val JSON_MEDIA = "application/json".toMediaType()
    }
}
