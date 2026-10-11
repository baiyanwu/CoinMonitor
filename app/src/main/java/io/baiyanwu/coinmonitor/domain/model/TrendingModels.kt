package io.baiyanwu.coinmonitor.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class TrendingSource(val title: String) { GMGN("GMGN"), PUMP("PumpFun"), OKX("OKX") }
enum class TrendingBoard { SHARED, GMGN, PUMP, OKX }
enum class TrendingMode { TRENDING, HOT_SEARCH }
enum class TrendingChain(val title: String, val gmgnId: String, val okxId: String) {
    SOLANA("Solana", "sol", "501"), BSC("BSC", "bsc", "56"), BASE("Base", "base", "8453"),
    ETHEREUM("Ethereum", "eth", "1"), ARBITRUM("Arbitrum", "arbitrum", "42161"),
    HYPEREVM("HyperEVM", "hyperevm", "999"), ROBINHOOD("Robinhood", "robinhood", "4663"),
    ARC("Arc", "arc", "5042"), STABLE("Stable", "stable", "988");

    fun validAddress(address: String): Boolean = if (this == SOLANA) {
        Regex("^[1-9A-HJ-NP-Za-km-z]{32,44}$").matches(address)
    } else Regex("^0x[0-9a-fA-F]{40}$").matches(address)
    val filters: List<String> get() = if (this == SOLANA) listOf("renounced", "frozen")
        else listOf("not_honeypot", "verified", "renounced")
}

/** Pump CAIP-2 identities, including unfamiliar EVM networks. */
data class PumpTrendingChain(val index: String) {
    val title: String get() = TrendingChain.entries.firstOrNull { it.okxId == index }?.title ?: "Chain #${index.removePrefix("eip155:")}"
    fun validAddress(address: String): Boolean = (if (index == "501") TrendingChain.SOLANA else TrendingChain.ETHEREUM).validAddress(address)
    companion object {
        fun fromCaip(value: String): PumpTrendingChain? {
            if (value in listOf("solana:mainnet", "solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp")) return PumpTrendingChain("501")
            if (!value.matches(Regex("eip155:[0-9]+"))) return null
            val id = value.substringAfter(':').toULongOrNull()?.takeIf { it > 0uL } ?: return null
            // EVM chain 501 must never collide with OKX's Solana identifier.
            return PumpTrendingChain(if (id == 501uL) "eip155:501" else id.toString())
        }
    }
}

enum class TrendingSort { DEFAULT, TOKEN, MARKET_CAP, PRICE, CHANGE, VOLUME, LIQUIDITY, SMART_MONEY }
enum class TrendingValue { MARKET_CAP, PRICE }

object TrendingRefreshPolicy {
    const val minimumIntervalMillis = 5_000L
    fun isDue(now: Long, nextAllowed: Long = 0,
        blocked: Boolean = false, requested: Boolean = false, lastRequestedAt: Long? = null): Boolean =
        requested && !blocked && now >= nextAllowed &&
            (lastRequestedAt == null || now - lastRequestedAt >= minimumIntervalMillis)
}

data class TrendingOptions(
    val chain: TrendingChain? = TrendingChain.SOLANA,
    val mode: TrendingMode = TrendingMode.TRENDING,
    val period: String = "5m",
    val sort: TrendingSort = TrendingSort.DEFAULT,
    val ascending: Boolean = false,
    val value: TrendingValue = TrendingValue.MARKET_CAP,
    val pumpChainIndex: String? = null
) {
    // Presentation settings do not invalidate a successful ranking snapshot.
    fun queryKey(source: TrendingSource): String = if (source == TrendingSource.PUMP) "pump:multichain:1h"
        else "${source.name}:${chain?.name ?: "all"}:${mode.name}:$period"
    companion object {
        fun periods(source: TrendingSource): List<String> = when (source) {
            TrendingSource.GMGN -> listOf("1m", "5m", "1h", "6h", "24h")
            TrendingSource.OKX -> listOf("5m", "1h", "4h", "24h")
            TrendingSource.PUMP -> emptyList()
        }
    }
}

@Serializable
data class TrendingToken(
    val chainIndex: String,
    val chainTitle: String,
    val address: String,
    val symbol: String,
    val name: String = "",
    val iconUrl: String? = null,
    val price: Double? = null,
    val marketCap: Double? = null,
    val change: Double? = null,
    val volume: Double? = null,
    val liquidity: Double? = null,
    val smartMoney: Double? = null,
    val visitingCount: Double? = null,
    val risk: Boolean = false,
    val links: Map<TrendingSource, String> = emptyMap()
) {
    val id: String get() = "$chainIndex:${if (chainIndex == "501") address else address.lowercase(java.util.Locale.ROOT)}"
    fun metric(sort: TrendingSort): Double? = when (sort) {
        TrendingSort.MARKET_CAP -> marketCap
        TrendingSort.PRICE -> price
        TrendingSort.CHANGE -> change
        TrendingSort.VOLUME -> volume
        TrendingSort.LIQUIDITY -> liquidity
        TrendingSort.SMART_MONEY -> smartMoney
        else -> null
    }
}

object TrendingRules {
    // Mac's data priority is GMGN, OKX, Pump; ticker names are never identities.
    val priority = listOf(TrendingSource.GMGN, TrendingSource.OKX, TrendingSource.PUMP)
    fun demand(board: TrendingBoard, selected: Set<TrendingSource>): Set<TrendingSource> = when (board) {
        TrendingBoard.SHARED -> selected
        TrendingBoard.GMGN -> setOf(TrendingSource.GMGN)
        TrendingBoard.PUMP -> setOf(TrendingSource.PUMP)
        TrendingBoard.OKX -> setOf(TrendingSource.OKX)
    }
    fun intersection(selected: Set<TrendingSource>, snapshots: Map<TrendingSource, List<TrendingToken>>): List<TrendingToken> {
        if (selected.size < 2 || selected.any { it !in snapshots }) return emptyList()
        val sources = priority.filter { it in selected }
        val indexes = sources.associateWith { snapshots.getValue(it).associateBy(TrendingToken::id) }
        return snapshots.getValue(sources.first()).distinctBy(TrendingToken::id).mapNotNull { token ->
            if (sources.any { token.id !in indexes.getValue(it) }) null
            else token.copy(links = sources.flatMap { indexes.getValue(it).getValue(token.id).links.entries }
                .associate { it.toPair() })
        }
    }
    fun newDates(current: List<String>, previous: List<String>?, dates: Map<String, Long>, now: Long): Map<String, Long> {
        val result = dates.filter { it.key in current && now - it.value < 180_000 }.toMutableMap()
        if (previous != null) (current.toSet() - previous.toSet()).forEach { result[it] = now }
        return result
    }
    fun sorted(rows: List<TrendingToken>, options: TrendingOptions, newIds: Set<String>): List<TrendingToken> {
        val pinned = rows.filter { it.id in newIds }
        val remaining = rows.filter { it.id !in newIds }
        if (options.sort == TrendingSort.DEFAULT) return pinned + remaining
        return pinned + remaining.sortedWith { a, b ->
            if (options.sort == TrendingSort.TOKEN) {
                val comparison = a.symbol.compareTo(b.symbol, ignoreCase = true)
                if (options.ascending) comparison else -comparison
            } else {
                val left = a.metric(options.sort)?.takeIf(Double::isFinite)
                val right = b.metric(options.sort)?.takeIf(Double::isFinite)
                when {
                    left == null && right == null -> 0
                    left == null -> 1
                    right == null -> -1
                    options.ascending -> left.compareTo(right)
                    else -> right.compareTo(left)
                }
            }
        }
    }
}
