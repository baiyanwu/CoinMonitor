package io.baiyanwu.coinmonitor.data.refresh

import io.baiyanwu.coinmonitor.domain.model.MarketType
import io.baiyanwu.coinmonitor.domain.model.OnchainChainRegistry
import io.baiyanwu.coinmonitor.domain.model.OnchainRefreshMode
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.domain.model.normalizeOnchainAddress

data class OnchainRefreshPlan(
    val requestBatchCount: Int,
    val cycleIntervalSeconds: Int
)

data class OnchainRefreshBatch(
    val key: String,
    val items: List<WatchItem>
)

/**
 * 链上刷新按“每轮实际请求批次”规划，而不是简单按观察币种数量规划。
 * 同一条链最多 30 个不同地址合并为一批；所有批次在一个完整轮转周期内顺序发送。
 */
object OnchainRefreshPolicy {
    const val MAX_TOKEN_ADDRESSES_PER_REQUEST = 30
    const val SOURCE_CACHE_WINDOW_SECONDS = 30
    const val MIN_REQUEST_GAP_MILLIS = 1_000L

    fun resolve(
        items: List<WatchItem>,
        mode: OnchainRefreshMode,
        fixedIntervalSeconds: Int
    ): OnchainRefreshPlan {
        val batchCount = buildRequestBatches(items).size
        val requestedCycleSeconds = when (mode) {
            OnchainRefreshMode.SMART -> SOURCE_CACHE_WINDOW_SECONDS
            OnchainRefreshMode.FIXED -> fixedIntervalSeconds.coerceAtLeast(
                SOURCE_CACHE_WINDOW_SECONDS
            )
        }
        return OnchainRefreshPlan(
            requestBatchCount = batchCount,
            cycleIntervalSeconds = maxOf(requestedCycleSeconds, batchCount)
        )
    }

    fun requestSpacingMillis(cycleIntervalSeconds: Int, requestBatchCount: Int): Long {
        if (requestBatchCount <= 0) return 0L
        val cycleMillis = cycleIntervalSeconds.coerceAtLeast(1) * 1_000L
        return maxOf(MIN_REQUEST_GAP_MILLIS, cycleMillis / requestBatchCount)
    }

    fun countRequestBatches(items: List<WatchItem>): Int {
        return buildRequestBatches(items).size
    }

    fun buildRequestBatches(items: List<WatchItem>): List<OnchainRefreshBatch> {
        val resolvedItems = items.asSequence()
            .filter { item -> item.marketType == MarketType.ONCHAIN_TOKEN }
            .mapNotNull { item ->
                val chain = OnchainChainRegistry.resolve(
                    chainIndexOrDexScreenerId = item.chainIndex,
                    family = item.chainFamily
                ) ?: return@mapNotNull null
                val address = item.tokenAddress
                    ?.takeIf(String::isNotBlank)
                    ?.let { normalizeOnchainAddress(chain.family, it) }
                    ?: return@mapNotNull null
                ResolvedOnchainItem(
                    chainId = chain.dexScreenerId,
                    normalizedAddress = address,
                    item = item
                )
            }
            .toList()

        return resolvedItems
            .groupBy(ResolvedOnchainItem::chainId)
            .toSortedMap()
            .flatMap { (chainId, chainItems) ->
                chainItems
                    .groupBy(ResolvedOnchainItem::normalizedAddress)
                    .toSortedMap()
                    .entries
                    .chunked(MAX_TOKEN_ADDRESSES_PER_REQUEST)
                    .mapIndexed { batchIndex, addressGroups ->
                        OnchainRefreshBatch(
                            key = "$chainId:$batchIndex",
                            items = addressGroups
                                .flatMap { entry -> entry.value.map(ResolvedOnchainItem::item) }
                                .sortedBy(WatchItem::id)
                        )
                    }
            }
    }

    private data class ResolvedOnchainItem(
        val chainId: String,
        val normalizedAddress: String,
        val item: WatchItem
    )
}
