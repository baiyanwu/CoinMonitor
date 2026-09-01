package io.baiyanwu.coinmonitor.data.network

import io.baiyanwu.coinmonitor.domain.model.ChainFamily
import io.baiyanwu.coinmonitor.domain.model.PoolTokenSide
import io.baiyanwu.coinmonitor.domain.model.onchainAddressesEqual
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random

class DexScreenerClient(
    private val api: DexScreenerApi,
    private val limiter: RequestRateLimiter = RequestRateLimiter(240, 60_000L)
) {
    suspend fun searchPairs(query: String): List<DexScreenerPair> = request {
        api.searchPairs(query).pairs.orEmpty()
    }

    suspend fun getTokenPairs(chainId: String, tokenAddress: String): List<DexScreenerPair> = request {
        api.getTokenPairs(chainId, tokenAddress)
    }

    suspend fun getTokenPairsBatch(
        chainId: String,
        tokenAddresses: List<String>
    ): List<DexScreenerPair> {
        require(tokenAddresses.size in 1..30)
        return request {
            api.getTokenPairsBatch(chainId, tokenAddresses.joinToString(","))
        }
    }

    private suspend fun <T> request(block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            limiter.awaitPermit()
            try {
                return block()
            } catch (error: HttpException) {
                if (error.code() != 429 || attempt >= MAX_RETRIES) throw error
                val retryAfterMillis = parseRetryAfterMillis(
                    error.response()?.headers()?.get("Retry-After")
                )
                val backoffMillis = retryAfterMillis ?: (
                    (1_000L shl attempt) + Random.nextLong(150L, 650L)
                    ).coerceAtMost(MAX_RETRY_DELAY_MILLIS)
                delay(backoffMillis)
                attempt += 1
            }
        }
    }

    private companion object {
        const val MAX_RETRIES = 2
        const val MAX_RETRY_DELAY_MILLIS = 30_000L
    }
}

class GeckoTerminalClient(
    private val api: GeckoTerminalApi,
    private val limiter: RequestRateLimiter = RequestRateLimiter(8, 60_000L)
) {
    suspend fun getPoolOhlcv(
        network: String,
        poolAddress: String,
        timeframe: String,
        aggregate: Int,
        limit: Int,
        tokenSide: PoolTokenSide,
        beforeTimestamp: Long? = null
    ): GeckoTerminalOhlcvResponse {
        limiter.awaitPermit()
        return api.getPoolOhlcv(
            network = network,
            poolAddress = poolAddress,
            timeframe = timeframe,
            aggregate = aggregate,
            limit = limit,
            token = tokenSide.apiValue,
            beforeTimestamp = beforeTimestamp
        )
    }
}

class RequestRateLimiter(
    private val maxRequests: Int,
    private val windowMillis: Long
) {
    private val mutex = Mutex()
    private val timestamps = ArrayDeque<Long>()

    suspend fun awaitPermit() {
        while (true) {
            val waitMillis = mutex.withLock {
                val now = System.currentTimeMillis()
                while (timestamps.isNotEmpty() && now - timestamps.first() >= windowMillis) {
                    timestamps.removeFirst()
                }
                if (timestamps.size < maxRequests) {
                    timestamps.addLast(now)
                    0L
                } else {
                    (windowMillis - (now - timestamps.first())).coerceAtLeast(1L)
                }
            }
            if (waitMillis == 0L) return
            delay(waitMillis)
        }
    }
}

data class SelectedDexPair(
    val pair: DexScreenerPair,
    val tokenSide: PoolTokenSide,
    val tokenAddress: String,
    val tokenName: String,
    val tokenSymbol: String,
    val priceUsd: Double,
    val change24hPercent: Double?
)

object DexScreenerPairSelector {
    fun candidates(
        pairs: List<DexScreenerPair>,
        tokenAddress: String,
        family: ChainFamily
    ): List<SelectedDexPair> {
        return pairs
            .mapNotNull { pair -> pair.toSelection(tokenAddress, family) }
            .distinctBy { selection ->
                if (family == ChainFamily.SOL) {
                    selection.pair.pairAddress
                } else {
                    selection.pair.pairAddress.lowercase()
                }
            }
            .sortedWith(
                compareByDescending<SelectedDexPair> { it.tokenSide == PoolTokenSide.BASE }
                    .thenByDescending { it.pair.liquidity?.usd ?: 0.0 }
                    .thenByDescending { it.pair.volume["h24"] ?: 0.0 }
                    .thenBy { it.pair.pairAddress.lowercase() }
            )
    }

    fun select(
        pairs: List<DexScreenerPair>,
        tokenAddress: String,
        family: ChainFamily,
        preferredPoolAddress: String? = null
    ): SelectedDexPair? {
        val candidates = candidates(pairs, tokenAddress, family)
        if (candidates.isEmpty()) return null

        preferredPoolAddress?.let { preferred ->
            candidates.firstOrNull {
                onchainAddressesEqual(family, it.pair.pairAddress, preferred)
            }?.let { return it }
        }

        return candidates.first()
    }

    fun matchingTokenAddresses(
        pair: DexScreenerPair,
        keyword: String,
        family: ChainFamily
    ): List<String> {
        val normalized = keyword.trim()
        if (normalized.isBlank()) return emptyList()
        return buildList {
            if (pair.baseToken.matches(normalized, family)) add(pair.baseToken.address)
            if (pair.quoteToken.matches(normalized, family)) add(pair.quoteToken.address)
        }
    }

    private fun DexScreenerPair.toSelection(
        targetAddress: String,
        family: ChainFamily
    ): SelectedDexPair? {
        if (pairAddress.isBlank()) return null
        val side = when {
            onchainAddressesEqual(family, baseToken.address, targetAddress) -> PoolTokenSide.BASE
            onchainAddressesEqual(family, quoteToken.address, targetAddress) -> PoolTokenSide.QUOTE
            else -> return null
        }
        val liquidityUsd = liquidity?.usd ?: return null
        if (!liquidityUsd.isFinite() || liquidityUsd <= 0.0) return null
        val basePriceUsd = priceUsd?.toDoubleOrNull() ?: return null
        val targetPriceUsd = when (side) {
            PoolTokenSide.BASE -> basePriceUsd
            PoolTokenSide.QUOTE -> {
                val ratio = priceNative?.toDoubleOrNull() ?: return null
                if (ratio <= 0.0) return null
                basePriceUsd / ratio
            }
        }
        if (!targetPriceUsd.isFinite() || targetPriceUsd <= 0.0) return null
        val token = if (side == PoolTokenSide.BASE) baseToken else quoteToken
        return SelectedDexPair(
            pair = this,
            tokenSide = side,
            tokenAddress = token.address,
            tokenName = token.name.ifBlank { token.symbol },
            tokenSymbol = token.symbol.ifBlank { token.address.take(8) },
            priceUsd = targetPriceUsd,
            change24hPercent = priceChange?.get("h24").takeIf { side == PoolTokenSide.BASE }
        )
    }

    private fun DexScreenerToken.matches(keyword: String, family: ChainFamily): Boolean {
        return onchainAddressesEqual(family, address, keyword) ||
            symbol.contains(keyword, ignoreCase = true) ||
            name.contains(keyword, ignoreCase = true)
    }

}

class PinnedPoolSelectionPolicy(
    private val missThreshold: Int = DEFAULT_MISS_THRESHOLD
) {
    private val missingPinnedPoolCounts = ConcurrentHashMap<String, Int>()

    init {
        require(missThreshold > 0)
    }

    fun select(
        itemId: String,
        pairs: List<DexScreenerPair>,
        tokenAddress: String,
        family: ChainFamily,
        preferredPoolAddress: String?
    ): SelectedDexPair? {
        val preferredPool = preferredPoolAddress?.takeIf(String::isNotBlank)
            ?: return DexScreenerPairSelector.select(pairs, tokenAddress, family)
        val selected = DexScreenerPairSelector.select(
            pairs = pairs,
            tokenAddress = tokenAddress,
            family = family,
            preferredPoolAddress = preferredPool
        ) ?: run {
            missingPinnedPoolCounts.merge(itemId, 1, Int::plus)
            return null
        }
        if (onchainAddressesEqual(family, selected.pair.pairAddress, preferredPool)) {
            missingPinnedPoolCounts.remove(itemId)
            return selected
        }

        val pinnedPoolStillReturned = pairs.any { pair ->
            onchainAddressesEqual(family, pair.pairAddress, preferredPool)
        }
        if (pinnedPoolStillReturned) {
            // 固定池仍在响应中却已没有有效价格或流动性，视为明确失效，立即换池。
            missingPinnedPoolCounts.remove(itemId)
            return selected
        }

        val consecutiveMisses = missingPinnedPoolCounts.merge(itemId, 1, Int::plus) ?: 1
        return if (consecutiveMisses >= missThreshold) {
            missingPinnedPoolCounts.remove(itemId)
            selected
        } else {
            // 短时缺失保留旧报价，避免上游响应抖动导致池子来回切换。
            null
        }
    }

    private companion object {
        const val DEFAULT_MISS_THRESHOLD = 3
    }
}

internal fun parseRetryAfterMillis(
    value: String?,
    nowMillis: Long = System.currentTimeMillis()
): Long? {
    val raw = value?.trim()?.takeIf(String::isNotBlank) ?: return null
    raw.toLongOrNull()?.let { seconds ->
        return seconds.coerceAtLeast(0L) * 1_000L
    }
    return runCatching {
        val retryAtMillis = ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME)
            .toInstant()
            .toEpochMilli()
        (retryAtMillis - nowMillis).coerceAtLeast(0L)
    }.getOrNull()
}
