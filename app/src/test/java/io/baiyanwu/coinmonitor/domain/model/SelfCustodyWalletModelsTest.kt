package io.baiyanwu.coinmonitor.domain.model

import java.math.BigDecimal
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfCustodyWalletModelsTest {
    @Test
    fun `wallet password requires length and three character groups`() {
        assertTrue(WalletPasswordPolicy.isStrong("Secure123!"))
        assertTrue(WalletPasswordPolicy.isStrong("long-password-123"))

        assertFalse(WalletPasswordPolicy.isStrong("Short1!"))
        assertFalse(WalletPasswordPolicy.isStrong("alllowercase123"))
        assertFalse(WalletPasswordPolicy.isStrong("Secure 123!"))
    }

    @Test
    fun `launch networks have stable mainnet identities`() {
        assertEquals(
            listOf(1L, 8453L, 42161L, 10L, 137L, 56L, null),
            WalletNetwork.entries.map(WalletNetwork::chainId)
        )
        assertEquals(6, WalletNetwork.entries.count(WalletNetwork::isEvm))
        assertFalse(WalletNetwork.SOLANA.isEvm)
    }

    @Test
    fun `new wallet configuration enables four primary networks by default`() {
        assertEquals(
            listOf(WalletNetwork.ETHEREUM, WalletNetwork.BNB_CHAIN, WalletNetwork.ROBINHOOD, WalletNetwork.SOLANA),
            WalletNetwork.defaultEnabledNetworks
        )
        assertEquals(WalletNetwork.defaultEnabledNetworkIds, WalletNetworkConfiguration().enabledNetworkIds)
    }

    @Test
    fun `custom rpc takes node priority while alchemy remains index`() {
        val config = WalletNetworkConfiguration(
            alchemyApiKey = "key",
            customRpcUrls = mapOf(WalletNetwork.BASE.id to "https://rpc.example")
        )

        assertEquals(WalletProviderMode.CUSTOM_RPC_WITH_ALCHEMY, config.modeFor(WalletNetwork.BASE))
        assertEquals(WalletProviderMode.ALCHEMY, config.modeFor(WalletNetwork.ETHEREUM))
        assertEquals("https://rpc.example", config.rpcUrl(WalletNetwork.BASE))
        assertTrue(config.rpcUrl(WalletNetwork.ETHEREUM)!!.contains("alchemy.com"))
    }

    @Test
    fun `pure rpc mode has no token index`() {
        val config = WalletNetworkConfiguration(
            customRpcUrls = mapOf(WalletNetwork.SOLANA.id to "https://solana.example")
        )

        assertEquals(WalletProviderMode.CUSTOM_RPC_ONLY, config.modeFor(WalletNetwork.SOLANA))
        assertEquals(WalletProviderMode.UNCONFIGURED, config.modeFor(WalletNetwork.ETHEREUM))
        assertFalse(config.hasAlchemy)
    }

    @Test
    fun `dynamic evm network keeps chain identity and alchemy endpoint`() {
        val robinhood = WalletNetwork.ROBINHOOD
        val config = WalletNetworkConfiguration(
            alchemyApiKey = "key",
            availableNetworks = WalletNetwork.seedCatalog,
            enabledNetworkIds = setOf(robinhood.id)
        )

        assertEquals("eip155:4663", robinhood.id)
        assertEquals(4663L, robinhood.chainId)
        assertEquals("https://robinhood-mainnet.g.alchemy.com/v2/key", config.rpcUrl(robinhood))
        assertEquals(listOf(robinhood), config.enabledNetworks)
    }

    @Test
    fun `dynamic network descriptor round trips without legacy decoding`() {
        val json = Json { explicitNulls = false }
        val encoded = json.encodeToString(WalletNetwork.serializer(), WalletNetwork.ROBINHOOD)
        val decoded = json.decodeFromString(WalletNetwork.serializer(), encoded)

        assertEquals(WalletNetwork.ROBINHOOD, decoded)
    }

    @Test
    fun `EVM private key wallet supports every EVM network`() {
        val wallet = WalletProfile(
            id = "wallet",
            name = "Wallet 1",
            kind = WalletKind.PRIVATE_KEY,
            evmAddress = "0x0000000000000000000000000000000000000001",
            solanaAddress = null,
            privateKeyType = WalletPrivateKeyType.EVM,
            backedUp = true,
            createdAtMillis = 1L
        )

        assertTrue(wallet.supports(WalletNetwork.BASE))
        assertTrue(wallet.supports(WalletNetwork.ETHEREUM))
        assertTrue(wallet.supports(WalletNetwork.BNB_CHAIN))
        assertFalse(wallet.supports(WalletNetwork.SOLANA))
    }

    @Test
    fun `Solana private key wallet does not support EVM networks`() {
        val wallet = WalletProfile(
            id = "solana-wallet",
            name = "Wallet 2",
            kind = WalletKind.PRIVATE_KEY,
            solanaAddress = "11111111111111111111111111111111",
            privateKeyType = WalletPrivateKeyType.SOLANA,
            backedUp = true,
            createdAtMillis = 2L
        )

        assertTrue(wallet.supports(WalletNetwork.SOLANA))
        assertFalse(wallet.supports(WalletNetwork.ETHEREUM))
    }

    @Test
    fun `portfolio estimate excludes unpriced assets`() {
        val priced = asset(id = "priced", value = "12.50")
        val unpriced = asset(id = "unpriced", value = null)

        assertEquals(
            BigDecimal("12.50"),
            WalletPortfolio(listOf(priced, unpriced), emptyList(), tokenIndexAvailable = true).totalUsd
        )
    }

    @Test
    fun `refresh failures keep asset balance and activity capabilities separate`() {
        val failures = WalletRefreshFailures(
            aggregateAssetIndex = true,
            assetIndex = setOf(WalletNetwork.ETHEREUM),
            nativeBalance = setOf(WalletNetwork.BASE),
            activityIndex = setOf(WalletNetwork.ETHEREUM)
        )

        assertEquals(setOf(WalletNetwork.ETHEREUM, WalletNetwork.BASE), failures.allNetworks)
        assertTrue(failures.hasFailures)
        assertTrue(failures.aggregateAssetIndex)
        assertFalse(WalletRefreshFailures().hasFailures)
    }

    private fun asset(id: String, value: String?) = SelfCustodyAsset(
        id = id,
        network = WalletNetwork.ETHEREUM,
        tokenAddress = null,
        name = "Ethereum",
        symbol = "ETH",
        decimals = 18,
        rawBalance = "1",
        balance = BigDecimal.ONE,
        priceUsd = value?.toBigDecimal(),
        valueUsd = value?.toBigDecimal(),
        logoUrl = null,
        verified = true,
        isNative = true
    )
}
