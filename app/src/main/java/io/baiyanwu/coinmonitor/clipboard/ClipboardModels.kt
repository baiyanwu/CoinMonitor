package io.baiyanwu.coinmonitor.clipboard

import io.baiyanwu.coinmonitor.data.network.DexScreenerPair
import io.baiyanwu.coinmonitor.data.network.DexScreenerPairSelector
import io.baiyanwu.coinmonitor.data.network.SelectedDexPair
import io.baiyanwu.coinmonitor.domain.model.*
import kotlinx.serialization.Serializable
import java.net.URI
import java.net.URLEncoder
import java.util.Locale

@Serializable
data class ClipboardSettings(
    val enabled: Boolean = true,
    val addToFloating: Boolean = false,
    val destinations: List<ClipboardDestination> = clipboardDestinations()
)

@Serializable
data class ClipboardDestination(
    val id: String,
    val name: String,
    val template: String,
    val enabled: Boolean = true,
    val chainValues: Map<String, String> = emptyMap(),
    val chainTemplates: Map<String, String> = emptyMap()
) {
    fun url(match: ClipboardMatch): String? {
        if (id == "dex" && template.isBlank()) {
            return safeClipboardUrl("https://dexscreener.com/${match.chain.dexScreenerId}/${match.selection.pair.pairAddress}")
        }
        val chain = match.chain.dexScreenerId
        val source = if (template.isBlank()) chainTemplates[chain] ?: return null else template
        val chainValue = if (chainValues.isEmpty()) chain else chainValues[chain]
        if (source.contains("{chain}") && chainValue == null) return null
        return safeClipboardUrl(source.replace("{ca}", encode(match.selection.tokenAddress))
            .replace("{chain}", encode(chainValue.orEmpty())))
    }
}

fun clipboardDestinations(): List<ClipboardDestination> = listOf(
    ClipboardDestination("dex", "DexScreener", ""),
    ClipboardDestination("gmgn", "GMGN", "https://gmgn.ai/{chain}/token/{ca}",
        chainValues = mapOf("ethereum" to "eth", "bsc" to "bsc", "base" to "base", "solana" to "sol", "robinhood" to "robinhood")),
    ClipboardDestination("okx", "OKX", "https://web3.okx.com/token/{chain}/{ca}",
        chainValues = mapOf("ethereum" to "ethereum", "bsc" to "bsc", "base" to "base", "solana" to "solana", "robinhood" to "robinhood-chain")),
    ClipboardDestination("binance", "Binance", "https://web3.binance.com/zh-CN/token/{chain}/{ca}",
        chainValues = mapOf("ethereum" to "eth", "bsc" to "bsc", "base" to "base", "solana" to "sol", "robinhood" to "robinhood")),
    ClipboardDestination("x", "X", "https://x.com/search?q={ca}&src=typed_query&f=live"),
    ClipboardDestination("explorer", "Explorer", "", chainTemplates = mapOf(
        "ethereum" to "https://etherscan.io/address/{ca}", "bsc" to "https://bscscan.com/address/{ca}",
        "base" to "https://basescan.org/address/{ca}", "solana" to "https://solscan.io/account/{ca}",
        "robinhood" to "https://robinhoodchain.blockscout.com/address/{ca}")),
    ClipboardDestination("fomo", "Fomo", "https://fomo.family/tokens/{chain}/{ca}",
        chainValues = mapOf("ethereum" to "ethereum", "bsc" to "bnb", "base" to "base", "solana" to "solana", "monad" to "monad", "robinhood" to "robinhood"))
)

fun safeClipboardUrl(value: String?): String? = value?.takeIf {
    runCatching {
        val uri = URI(it)
        uri.scheme?.lowercase() in setOf("https", "http") && !uri.host.isNullOrBlank() && uri.userInfo == null
    }.getOrDefault(false)
}

private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

object ClipboardAddressParser {
    private val pattern = Regex("(?<![A-Za-z0-9])(?:0x[0-9a-fA-F]{40}|[1-9A-HJ-NP-Za-km-z]{32,44})(?![A-Za-z0-9])")
    fun extract(text: String): List<String> = pattern.findAll(text.take(32_768))
        .map { it.value }.distinctBy { if (it.startsWith("0x")) it.lowercase() else it }.take(10).toList()
}

data class ClipboardMatch(val chain: OnchainChain, val selection: SelectedDexPair) {
    val marketCap: Double? get() = selection.pair.marketCap
        .takeIf { selection.tokenSide == PoolTokenSide.BASE && it != null && it.isFinite() && it >= 0 }
    // DexScreener's info belongs to the base token, never to the quote token.
    val links: List<Pair<String, String>> get() {
        if (selection.tokenSide != PoolTokenSide.BASE) return emptyList()
        val info = selection.pair.info ?: return emptyList()
        return (info.websites.mapNotNull { link -> safeClipboardUrl(link.url)?.let { (link.label ?: "Website") to it } } +
            info.socials.mapNotNull { link -> safeClipboardUrl(link.url)?.let { (link.type ?: link.platform ?: "Social") to it } })
            .distinctBy { it.second }.take(8)
    }

    fun watchItem(addToFloating: Boolean, now: Long = System.currentTimeMillis()): WatchItem {
        val address = normalizeOnchainAddress(chain.family, selection.tokenAddress)
        return WatchItem(
            id = "onchain:${chain.chainIndex}:$address", symbol = selection.pairLabel,
            name = selection.tokenName, exchangeSource = ExchangeSource.ONCHAIN,
            marketType = MarketType.ONCHAIN_TOKEN, chainFamily = chain.family, chainIndex = chain.chainIndex,
            tokenAddress = address, poolAddress = normalizeOnchainAddress(chain.family, selection.pair.pairAddress),
            poolTokenSide = selection.tokenSide, overlaySelected = addToFloating,
            iconUrl = selection.pair.info?.imageUrl.takeIf { selection.tokenSide == PoolTokenSide.BASE },
            lastPrice = selection.priceUsd, change24hPercent = selection.change24hPercent,
            lastUpdatedAt = now, addedAt = now
        )
    }
}

fun clipboardMatches(address: String, pairs: List<DexScreenerPair>): List<ClipboardMatch> =
    pairs.groupBy { it.chainId }.mapNotNull { (chainId, chainPairs) ->
        val family = inferOnchainChainFamily(chainId, listOf(address))
        val chain = OnchainChainRegistry.resolveDexScreenerChain(chainId, family) ?: return@mapNotNull null
        DexScreenerPairSelector.select(chainPairs, address, family)?.let { ClipboardMatch(chain, it) }
    }.sortedBy { it.chain.displayName }

fun compactClipboardNumber(value: Double?): String {
    if (value == null || !value.isFinite() || value < 0) return "--"
    val (divisor, suffix) = when {
        value >= 1e12 -> 1e12 to "T"
        value >= 1e9 -> 1e9 to "B"
        value >= 1e6 -> 1e6 to "M"
        value >= 1e3 -> 1e3 to "K"
        else -> 1.0 to ""
    }
    return "$" + String.format(Locale.US, "%.2f", value / divisor).trimEnd('0').trimEnd('.') + suffix
}
