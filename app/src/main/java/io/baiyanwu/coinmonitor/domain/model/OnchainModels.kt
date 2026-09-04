package io.baiyanwu.coinmonitor.domain.model

enum class PoolTokenSide(val apiValue: String) {
    BASE("base"),
    QUOTE("quote")
}

/**
 * 搜索页临时使用的池子候选信息。数据库只持久化 poolAddress 和 tokenSide，
 * 其余展示字段会在下一次搜索时由 DexScreener 重新提供。
 */
data class OnchainPoolOption(
    val poolAddress: String,
    val tokenSide: PoolTokenSide,
    val pairLabel: String,
    val dexId: String,
    val labels: List<String> = emptyList(),
    val liquidityUsd: Double,
    val volume24hUsd: Double? = null,
    val priceUsd: Double,
    val change24hPercent: Double? = null,
    val poolUrl: String? = null
)

data class OnchainChain(
    val chainIndex: String,
    val displayName: String,
    val family: ChainFamily,
    val dexScreenerId: String,
    val geckoTerminalId: String
)

object OnchainChainRegistry {
    val entries: List<OnchainChain> = listOf(
        OnchainChain("1", "ETH", ChainFamily.EVM, "ethereum", "eth"),
        OnchainChain("8453", "Base", ChainFamily.EVM, "base", "base"),
        OnchainChain("56", "BSC", ChainFamily.EVM, "bsc", "bsc"),
        OnchainChain("42161", "Arbitrum", ChainFamily.EVM, "arbitrum", "arbitrum"),
        OnchainChain("137", "Polygon", ChainFamily.EVM, "polygon", "polygon_pos"),
        OnchainChain("10", "Optimism", ChainFamily.EVM, "optimism", "optimism"),
        OnchainChain("43114", "Avalanche", ChainFamily.EVM, "avalanche", "avax"),
        OnchainChain("59144", "Linea", ChainFamily.EVM, "linea", "linea"),
        OnchainChain("534352", "Scroll", ChainFamily.EVM, "scroll", "scroll"),
        OnchainChain("81457", "Blast", ChainFamily.EVM, "blast", "blast"),
        OnchainChain("34443", "Mode", ChainFamily.EVM, "mode", "mode"),
        OnchainChain("5000", "Mantle", ChainFamily.EVM, "mantle", "mantle"),
        OnchainChain("1101", "Polygon zkEVM", ChainFamily.EVM, "polygonzkevm", "polygon-zkevm"),
        OnchainChain("324", "zkSync", ChainFamily.EVM, "zksync", "zksync"),
        OnchainChain("250", "Fantom", ChainFamily.EVM, "fantom", "ftm"),
        OnchainChain("7000", "Zeta", ChainFamily.EVM, "zetachain", "zetachain"),
        OnchainChain("501", "SOL", ChainFamily.SOL, "solana", "solana")
    )

    private val byChainIndex = entries.associateBy(OnchainChain::chainIndex)
    private val byDexScreenerId = entries.associateBy { it.dexScreenerId.lowercase() }

    fun find(chainIndex: String?): OnchainChain? = chainIndex?.let(byChainIndex::get)

    fun findByDexScreenerId(dexScreenerId: String?): OnchainChain? {
        return dexScreenerId
            ?.trim()
            ?.lowercase()
            ?.takeIf(String::isNotBlank)
            ?.let(byDexScreenerId::get)
    }

    /**
     * 已知链沿用本地精确映射；未知链直接使用 DexScreener 返回的 chainId，
     * 避免本地链注册表变成搜索白名单。
     */
    fun resolve(
        chainIndexOrDexScreenerId: String?,
        family: ChainFamily? = null
    ): OnchainChain? {
        val raw = chainIndexOrDexScreenerId?.trim()?.takeIf(String::isNotBlank) ?: return null
        return find(raw)
            ?: findByDexScreenerId(raw)
            ?: dynamic(raw, family ?: ChainFamily.OTHER)
    }

    fun resolveDexScreenerChain(
        dexScreenerId: String?,
        family: ChainFamily
    ): OnchainChain? {
        val raw = dexScreenerId?.trim()?.takeIf(String::isNotBlank) ?: return null
        return findByDexScreenerId(raw) ?: dynamic(raw, family)
    }

    private fun dynamic(dexScreenerId: String, family: ChainFamily): OnchainChain {
        val normalizedId = dexScreenerId.lowercase()
        return OnchainChain(
            chainIndex = normalizedId,
            displayName = normalizedId
                .split('-', '_', ' ')
                .filter(String::isNotBlank)
                .joinToString(" ") { part ->
                    part.replaceFirstChar { char -> char.titlecase() }
                }
                .ifBlank { dexScreenerId },
            family = family,
            dexScreenerId = normalizedId,
            geckoTerminalId = normalizedId
        )
    }
}

fun normalizeOnchainAddress(family: ChainFamily?, address: String): String {
    val trimmed = address.trim()
    return if (family == ChainFamily.EVM) trimmed.lowercase() else trimmed
}

fun onchainAddressesEqual(family: ChainFamily, left: String, right: String): Boolean {
    return if (family == ChainFamily.EVM) left.equals(right, ignoreCase = true) else left == right
}

fun inferOnchainChainFamily(
    dexScreenerId: String?,
    addresses: Iterable<String>
): ChainFamily {
    OnchainChainRegistry.findByDexScreenerId(dexScreenerId)?.let { return it.family }
    if (dexScreenerId.equals("solana", ignoreCase = true)) return ChainFamily.SOL
    return if (addresses.any { looksLikeOnchainAddress(ChainFamily.EVM, it) }) {
        ChainFamily.EVM
    } else {
        ChainFamily.OTHER
    }
}

data class GeckoTerminalInterval(
    val timeframe: String,
    val aggregate: Int,
    val localAggregationDays: Int = 1
)

fun KlineInterval.toGeckoTerminalInterval(): GeckoTerminalInterval {
    return when (this) {
        KlineInterval.ONE_MINUTE -> GeckoTerminalInterval("minute", 1)
        KlineInterval.FIVE_MINUTES -> GeckoTerminalInterval("minute", 5)
        KlineInterval.FIFTEEN_MINUTES -> GeckoTerminalInterval("minute", 15)
        KlineInterval.ONE_HOUR -> GeckoTerminalInterval("hour", 1)
        KlineInterval.FOUR_HOURS -> GeckoTerminalInterval("hour", 4)
        KlineInterval.ONE_DAY -> GeckoTerminalInterval("day", 1)
        KlineInterval.THREE_DAYS -> GeckoTerminalInterval("day", 1, localAggregationDays = 3)
        KlineInterval.ONE_WEEK -> GeckoTerminalInterval("day", 1, localAggregationDays = 7)
        KlineInterval.ONE_MONTH -> GeckoTerminalInterval("day", 1, localAggregationDays = 30)
    }
}

fun KlineInterval.toOnchainDisplayLabel(): String {
    return if (this == KlineInterval.ONE_MONTH) "30D" else label
}

fun looksLikeOnchainAddress(family: ChainFamily, value: String): Boolean {
    val trimmed = value.trim()
    return when (family) {
        ChainFamily.EVM -> trimmed.matches(Regex("^0x[0-9a-fA-F]{40}$"))
        ChainFamily.SOL -> trimmed.length in 32..44 && trimmed.all { it in BASE58_ALPHABET }
        ChainFamily.OTHER -> false
    }
}

private const val BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
