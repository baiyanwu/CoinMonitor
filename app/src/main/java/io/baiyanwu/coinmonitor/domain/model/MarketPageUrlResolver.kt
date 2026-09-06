package io.baiyanwu.coinmonitor.domain.model

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Resolves the public market page that corresponds to a watch item.
 *
 * Exchange item IDs retain the exact upstream instrument ID, while on-chain items retain the
 * selected pool address. Keeping URL construction here makes the mapping testable without Android
 * framework dependencies.
 */
object MarketPageUrlResolver {
    fun resolve(item: WatchItem): String? {
        if (item.marketType == MarketType.ONCHAIN_TOKEN || item.exchangeSource == ExchangeSource.ONCHAIN) {
            return resolveDexScreener(item)
        }

        return when (item.exchangeSource) {
            ExchangeSource.BINANCE -> resolveBinance(item)
            ExchangeSource.BINANCE_ALPHA -> BINANCE_ALPHA_MARKET_URL
            ExchangeSource.OKX -> resolveOkx(item)
            ExchangeSource.ONCHAIN -> resolveDexScreener(item)
        }
    }

    private fun resolveDexScreener(item: WatchItem): String? {
        val chainId = OnchainChainRegistry.resolve(resolveOnchainChainIndex(item), item.chainFamily)
            ?.dexScreenerId
            ?.takeIf(String::isNotBlank)
            ?: return null
        val poolAddress = item.poolAddress?.trim()?.takeIf(String::isNotBlank)
        if (poolAddress != null) {
            return "$DEX_SCREENER_URL/${encodePathSegment(chainId)}/${encodePathSegment(poolAddress)}"
        }

        val tokenAddress = resolveOnchainTokenAddress(item) ?: return null
        return "$DEX_SCREENER_URL/search?q=${encodeQueryValue(tokenAddress)}"
    }

    private fun resolveOnchainChainIndex(item: WatchItem): String? {
        item.chainIndex?.trim()?.takeIf(String::isNotBlank)?.let { return it }
        val segments = item.id.split(':')
        if (segments.size < 3 || segments.first().lowercase() !in ONCHAIN_ID_PREFIXES) return null
        return segments[1].trim().takeIf(String::isNotBlank)
    }

    private fun resolveOnchainTokenAddress(item: WatchItem): String? {
        item.tokenAddress?.trim()?.takeIf(String::isNotBlank)?.let { return it }
        val segments = item.id.split(':')
        if (segments.size < 3 || segments.first().lowercase() !in ONCHAIN_ID_PREFIXES) return null
        return segments.last().trim().takeIf(String::isNotBlank)
    }

    private fun resolveBinance(item: WatchItem): String? {
        val instrumentId = item.id.substringAfterLast(':').trim().takeIf(String::isNotBlank)
            ?: compactDisplaySymbol(item.symbol)
            ?: return null
        val encodedInstrumentId = encodePathSegment(instrumentId.uppercase())
        return if (item.marketType == MarketType.CEX_USDT_FUTURES) {
            "$BINANCE_URL/en/futures/$encodedInstrumentId"
        } else {
            val pair = displayPair(item.symbol)
                ?: instrumentId.uppercase()
            "$BINANCE_URL/en/trade/${encodePathSegment(pair)}?type=spot"
        }
    }

    private fun resolveOkx(item: WatchItem): String? {
        val instrumentId = item.id.substringAfterLast(':').trim().takeIf(String::isNotBlank)
            ?: displayPair(item.symbol)?.replace('_', '-')
            ?: return null
        val normalizedInstrumentId = if (item.marketType == MarketType.CEX_USDT_FUTURES) {
            instrumentId.uppercase().let { id -> if (id.endsWith("-SWAP")) id else "$id-SWAP" }
        } else {
            instrumentId.uppercase()
        }
        val marketPath = if (item.marketType == MarketType.CEX_USDT_FUTURES) {
            "trade-swap"
        } else {
            "trade-spot"
        }
        return "$OKX_URL/$marketPath/${encodePathSegment(normalizedInstrumentId.lowercase())}"
    }

    private fun displayPair(symbol: String): String? {
        val parts = symbol.trim().split('/', limit = 2)
        if (parts.size != 2 || parts.any(String::isBlank)) return null
        return "${parts[0].uppercase()}_${parts[1].uppercase()}"
    }

    private fun compactDisplaySymbol(symbol: String): String? {
        return symbol
            .trim()
            .replace("/", "")
            .replace("-", "")
            .takeIf(String::isNotBlank)
    }

    private fun encodePathSegment(value: String): String = encode(value)

    private fun encodeQueryValue(value: String): String = encode(value)

    private fun encode(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.toString()).replace("+", "%20")
    }

    private const val DEX_SCREENER_URL = "https://dexscreener.com"
    private const val BINANCE_URL = "https://www.binance.com"
    private const val OKX_URL = "https://www.okx.com"
    private val ONCHAIN_ID_PREFIXES = setOf("onchain", "okx-onchain", "okx-dex")

    // Alpha is an integrated DEX surface and persisted Alpha items do not contain chain/contract
    // metadata. Use Binance's official Alpha market directory instead of fabricating a pair URL.
    private const val BINANCE_ALPHA_MARKET_URL = "https://www.binance.com/en/price/binance-alpha"
}
