package io.baiyanwu.coinmonitor.domain.model

import kotlinx.serialization.Serializable
import java.math.BigDecimal

object WalletPasswordPolicy {
    const val MIN_LENGTH = 10
    const val REQUIRED_CHARACTER_GROUPS = 3

    fun isStrong(password: CharSequence): Boolean = isStrong(
        length = password.length,
        hasWhitespace = password.any(Char::isWhitespace),
        hasUppercase = password.any(Char::isUpperCase),
        hasLowercase = password.any(Char::isLowerCase),
        hasDigit = password.any(Char::isDigit),
        hasSymbol = password.any { !it.isLetterOrDigit() && !it.isWhitespace() }
    )

    fun isStrong(password: CharArray): Boolean = isStrong(
        length = password.size,
        hasWhitespace = password.any(Char::isWhitespace),
        hasUppercase = password.any(Char::isUpperCase),
        hasLowercase = password.any(Char::isLowerCase),
        hasDigit = password.any(Char::isDigit),
        hasSymbol = password.any { !it.isLetterOrDigit() && !it.isWhitespace() }
    )

    private fun isStrong(
        length: Int,
        hasWhitespace: Boolean,
        hasUppercase: Boolean,
        hasLowercase: Boolean,
        hasDigit: Boolean,
        hasSymbol: Boolean
    ): Boolean {
        val characterGroups = listOf(hasUppercase, hasLowercase, hasDigit, hasSymbol).count { it }
        return length >= MIN_LENGTH && !hasWhitespace && characterGroups >= REQUIRED_CHARACTER_GROUPS
    }
}

@Serializable
enum class WalletKind { MNEMONIC, PRIVATE_KEY }

@Serializable
enum class WalletPrivateKeyType { EVM, SOLANA }

@Serializable
data class WalletNetwork(
    val id: String,
    val chainId: Long?,
    val alchemyNetwork: String?,
    val alchemyRpcHost: String?,
    val symbol: String,
    val displayName: String,
    val explorerUrl: String,
    val decimals: Int,
    val isEvm: Boolean,
    val alchemyNetworkAliases: Set<String> = emptySet(),
    val alchemyPortfolioSupported: Boolean = false,
    val alchemyTransfersSupported: Boolean = false,
    val userDefined: Boolean = false
) {
    val name: String get() = if (isEvm) "EVM_$chainId" else id.uppercase().replace(':', '_')

    fun transactionUrl(hash: String): String? = explorerUrl.takeIf(String::isNotBlank)
        ?.trimEnd('/')
        ?.let { "$it/tx/$hash" }

    fun matchesAlchemyNetwork(value: String): Boolean = value == alchemyNetwork || value in alchemyNetworkAliases

    companion object {
        val ETHEREUM = evm(
            chainId = 1, alchemyNetwork = "eth-mainnet",
            host = "eth-mainnet.g.alchemy.com", symbol = "ETH", name = "Ethereum",
            explorer = "https://etherscan.io", indexed = true
        )
        val BASE = evm(
            chainId = 8453, alchemyNetwork = "base-mainnet",
            host = "base-mainnet.g.alchemy.com", symbol = "ETH", name = "Base",
            explorer = "https://basescan.org", indexed = true
        )
        val ARBITRUM = evm(
            chainId = 42161, alchemyNetwork = "arb-mainnet",
            host = "arb-mainnet.g.alchemy.com", symbol = "ETH", name = "Arbitrum",
            explorer = "https://arbiscan.io", indexed = true
        )
        val OPTIMISM = evm(
            chainId = 10, alchemyNetwork = "opt-mainnet",
            host = "opt-mainnet.g.alchemy.com", symbol = "ETH", name = "Optimism",
            explorer = "https://optimistic.etherscan.io", indexed = true
        )
        val POLYGON = evm(
            chainId = 137, alchemyNetwork = "polygon-mainnet",
            host = "polygon-mainnet.g.alchemy.com", symbol = "POL", name = "Polygon",
            explorer = "https://polygonscan.com", indexed = true,
            aliases = setOf("matic-mainnet")
        )
        val BNB_CHAIN = evm(
            chainId = 56, alchemyNetwork = "bnb-mainnet",
            host = "bnb-mainnet.g.alchemy.com", symbol = "BNB", name = "BNB Chain",
            explorer = "https://bscscan.com", indexed = true
        )
        val SOLANA = WalletNetwork(
            id = "solana:mainnet", chainId = null,
            alchemyNetwork = "sol-mainnet", alchemyRpcHost = "solana-mainnet.g.alchemy.com",
            symbol = "SOL", displayName = "Solana", explorerUrl = "https://explorer.solana.com",
            decimals = 9, isEvm = false, alchemyPortfolioSupported = true,
            alchemyTransfersSupported = true
        )
        val ROBINHOOD = evm(
            chainId = 4663, alchemyNetwork = "robinhood-mainnet",
            host = "robinhood-mainnet.g.alchemy.com", symbol = "ETH", name = "Robinhood Chain",
            explorer = "https://robinhoodchain.blockscout.com", indexed = false
        )

        /** Networks enabled by default for a newly configured wallet. */
        val entries: List<WalletNetwork> = listOf(ETHEREUM, BASE, ARBITRUM, OPTIMISM, POLYGON, BNB_CHAIN, SOLANA)
        val seedCatalog: List<WalletNetwork> = entries + ROBINHOOD
        val defaultEnabledNetworks: List<WalletNetwork> = listOf(ETHEREUM, BNB_CHAIN, ROBINHOOD, SOLANA)
        val defaultEnabledNetworkIds: Set<String> = defaultEnabledNetworks.mapTo(linkedSetOf(), WalletNetwork::id)

        fun fromId(value: String): WalletNetwork? = seedCatalog.firstOrNull { it.id == value }

        fun evm(
            chainId: Long,
            alchemyNetwork: String?,
            host: String?,
            symbol: String,
            name: String,
            explorer: String,
            indexed: Boolean = false,
            aliases: Set<String> = emptySet(),
            userDefined: Boolean = false
        ) = WalletNetwork(
            id = "eip155:$chainId",
            chainId = chainId,
            alchemyNetwork = alchemyNetwork,
            alchemyRpcHost = host,
            symbol = symbol,
            displayName = name,
            explorerUrl = explorer,
            decimals = 18,
            isEvm = true,
            alchemyNetworkAliases = aliases,
            alchemyPortfolioSupported = indexed,
            alchemyTransfersSupported = indexed,
            userDefined = userDefined
        )
    }
}

@Serializable
data class WalletProfile(
    val id: String,
    val name: String,
    val kind: WalletKind,
    val evmAddress: String? = null,
    val solanaAddress: String? = null,
    val privateKeyType: WalletPrivateKeyType? = null,
    val backedUp: Boolean = kind == WalletKind.PRIVATE_KEY,
    val createdAtMillis: Long
) {
    fun addressFor(network: WalletNetwork): String? = when {
        kind == WalletKind.PRIVATE_KEY && privateKeyType == null -> null
        kind == WalletKind.PRIVATE_KEY && privateKeyType == WalletPrivateKeyType.EVM && !network.isEvm -> null
        kind == WalletKind.PRIVATE_KEY && privateKeyType == WalletPrivateKeyType.SOLANA && network.isEvm -> null
        network.isEvm -> evmAddress
        else -> solanaAddress
    }

    fun supports(network: WalletNetwork): Boolean = addressFor(network) != null
}

data class SelfCustodyAsset(
    val id: String,
    val network: WalletNetwork,
    val tokenAddress: String?,
    val name: String,
    val symbol: String,
    val decimals: Int,
    val rawBalance: String,
    val balance: BigDecimal,
    val priceUsd: BigDecimal?,
    val valueUsd: BigDecimal?,
    val logoUrl: String?,
    val verified: Boolean,
    val isNative: Boolean,
    val transferable: Boolean = true,
    val userAdded: Boolean = false
)

@Serializable
data class WalletCustomToken(
    val walletId: String,
    val networkId: String,
    val contractAddress: String,
    val symbol: String,
    val decimals: Int
)

enum class WalletActivityDirection { INCOMING, OUTGOING, SELF, UNKNOWN }
enum class WalletActivityStatus { PENDING, CONFIRMED, FAILED, STALE }

data class WalletActivity(
    val id: String,
    val walletId: String,
    val network: WalletNetwork,
    val transactionHash: String,
    val direction: WalletActivityDirection,
    val symbol: String,
    val amount: BigDecimal?,
    val counterparty: String?,
    val timestampMillis: Long,
    val status: WalletActivityStatus,
    val locallySubmitted: Boolean,
    val tokenAddress: String? = null
)

data class WalletPortfolio(
    val assets: List<SelfCustodyAsset>,
    val activities: List<WalletActivity>,
    val refreshFailures: WalletRefreshFailures = WalletRefreshFailures(),
    val tokenIndexAvailable: Boolean,
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    val totalUsd: BigDecimal = assets.mapNotNull(SelfCustodyAsset::valueUsd)
        .fold(BigDecimal.ZERO, BigDecimal::add)
}

data class WalletRefreshFailures(
    val aggregateAssetIndex: Boolean = false,
    val assetIndex: Set<WalletNetwork> = emptySet(),
    val nativeBalance: Set<WalletNetwork> = emptySet(),
    val activityIndex: Set<WalletNetwork> = emptySet()
) {
    val allNetworks: Set<WalletNetwork> get() = assetIndex + nativeBalance + activityIndex
    val hasFailures: Boolean get() = aggregateAssetIndex || allNetworks.isNotEmpty()
}

data class WalletNetworkConfiguration(
    val alchemyApiKey: String = "",
    val customRpcUrls: Map<String, String> = emptyMap(),
    val availableNetworks: List<WalletNetwork> = WalletNetwork.seedCatalog,
    val enabledNetworkIds: Set<String> = WalletNetwork.defaultEnabledNetworkIds
) {
    val hasAlchemy: Boolean get() = alchemyApiKey.isNotBlank()
    val enabledNetworks: List<WalletNetwork> get() = availableNetworks.filter { it.id in enabledNetworkIds }

    fun rpcUrl(network: WalletNetwork): String? = customRpcUrls[network.id]?.takeIf(String::isNotBlank)
        ?: alchemyApiKey.takeIf(String::isNotBlank)?.let { key ->
            network.alchemyRpcHost?.let { "https://$it/v2/$key" }
        }

    fun network(id: String): WalletNetwork? = availableNetworks.firstOrNull { it.id == id }

    fun networkForAlchemy(value: String): WalletNetwork? =
        availableNetworks.firstOrNull { it.matchesAlchemyNetwork(value) }
}

enum class WalletProviderMode { ALCHEMY, CUSTOM_RPC_WITH_ALCHEMY, CUSTOM_RPC_ONLY, UNCONFIGURED }

fun WalletNetworkConfiguration.modeFor(network: WalletNetwork): WalletProviderMode = when {
    customRpcUrls[network.id].isNullOrBlank() && hasAlchemy && network.alchemyRpcHost != null -> WalletProviderMode.ALCHEMY
    !customRpcUrls[network.id].isNullOrBlank() && hasAlchemy -> WalletProviderMode.CUSTOM_RPC_WITH_ALCHEMY
    !customRpcUrls[network.id].isNullOrBlank() -> WalletProviderMode.CUSTOM_RPC_ONLY
    else -> WalletProviderMode.UNCONFIGURED
}
