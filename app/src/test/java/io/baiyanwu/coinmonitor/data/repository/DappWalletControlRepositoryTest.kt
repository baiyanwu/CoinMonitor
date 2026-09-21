package io.baiyanwu.coinmonitor.data.repository

import io.baiyanwu.coinmonitor.domain.model.WalletCustomToken
import io.baiyanwu.coinmonitor.domain.model.WalletNetwork
import io.baiyanwu.coinmonitor.domain.model.WalletNetworkConfiguration
import io.baiyanwu.coinmonitor.domain.repository.WalletCustomTokenRepository
import io.baiyanwu.coinmonitor.domain.repository.WalletNetworkSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DappWalletControlRepositoryTest {
    private val repository = DappWalletControlRepository(NoOpNetworkSettings(), NoOpCustomTokens())

    @Test
    fun `parses secure add chain request`() {
        val request = repository.prepareAddChain(
            buildJsonObject {
                put("chainId", "0x38")
                put("chainName", "BNB Chain")
                put("nativeCurrency", buildJsonObject {
                    put("name", "BNB")
                    put("symbol", "BNB")
                    put("decimals", 18)
                })
                put("rpcUrls", buildJsonArray { add(JsonPrimitive("https://bsc.example")) })
                put("blockExplorerUrls", buildJsonArray { add(JsonPrimitive("https://scan.example")) })
            }
        )

        assertEquals(56L, request.chainId)
        assertEquals("bsc.example", java.net.URI(request.rpcUrl).host)
    }

    @Test
    fun `rejects insecure DApp RPC URL`() {
        val error = runCatching {
            repository.prepareAddChain(
                buildJsonObject {
                    put("chainId", "0x123")
                    put("chainName", "Unsafe")
                    put("nativeCurrency", buildJsonObject {
                        put("symbol", "ETH")
                        put("decimals", 18)
                    })
                    put("rpcUrls", buildJsonArray { add(JsonPrimitive("http://rpc.example")) })
                }
            )
        }.exceptionOrNull()

        assertTrue(error?.message?.contains("HTTPS RPC") == true)
    }

    @Test
    fun `parses ERC20 watch asset for active network`() {
        val request = repository.prepareWatchAsset(
            walletId = "wallet-1",
            network = WalletNetwork.BNB_CHAIN,
            params = buildJsonObject {
                put("type", "ERC20")
                put("contract", "0x2222222222222222222222222222222222222222")
                put("symbol", "USDT")
                put("decimals", 18)
            }
        )

        assertEquals("wallet-1", request.walletId)
        assertEquals("USDT", request.symbol)
        assertEquals(WalletNetwork.BNB_CHAIN, request.network)
    }

    private class NoOpCustomTokens : WalletCustomTokenRepository {
        override fun get(walletId: String): List<WalletCustomToken> = emptyList()
        override suspend fun add(token: WalletCustomToken) = Unit
        override suspend fun remove(walletId: String, networkId: String, contractAddress: String) = Unit
        override suspend fun clear(walletIds: Collection<String>) = Unit
    }

    private class NoOpNetworkSettings : WalletNetworkSettingsRepository {
        private val config = WalletNetworkConfiguration()
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
        ): WalletNetwork = WalletNetwork.evm(chainId, null, null, symbol, name, explorerUrl, userDefined = true)
        override suspend fun removeCustomEvmNetwork(networkId: String) = Unit
    }
}
