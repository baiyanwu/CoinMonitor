package io.baiyanwu.coinmonitor.data.refresh

import io.baiyanwu.coinmonitor.domain.model.OnchainDataProvider
import io.baiyanwu.coinmonitor.domain.model.OnchainRefreshMode
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.domain.model.normalizeOnchainAddress
import kotlin.math.ceil

data class PreparedOnchainPollingItem(
    val originalItem: WatchItem,
    val providerItem: WatchItem
)

data class OnchainPollingBatch(
    val key: String,
    val provider: OnchainDataProvider,
    val itemIds: Set<String>,
    val cycleIntervalMillis: Long,
    val minimumRequestGapMillis: Long,
    val recurring: Boolean = true
)

data class OnchainPollingPlan(
    val batches: List<OnchainPollingBatch> = emptyList()
) {
    val requestBatchCount: Int get() = batches.count(OnchainPollingBatch::recurring)
    val displayRequestSpacingMillis: Long
        get() = batches.maxOfOrNull(OnchainPollingBatch::minimumRequestGapMillis) ?: 0L
    val displayCycleIntervalSeconds: Int
        get() = batches
            .filter(OnchainPollingBatch::recurring)
            .maxOfOrNull { (it.cycleIntervalMillis / 1_000L).toInt() }
            ?: DEFAULT_ONCHAIN_CYCLE_SECONDS
}

interface OnchainPollingStrategy {
    fun createBatches(
        provider: OnchainDataProvider,
        items: List<PreparedOnchainPollingItem>,
        mode: OnchainRefreshMode,
        fixedIntervalSeconds: Int,
        recurring: Boolean = true
    ): List<OnchainPollingBatch>

    fun retryDelayMillis(consecutiveFailures: Int): Long
}

class ChainBatchingOnchainPollingStrategy(
    private val maxAddressesPerRequest: Int,
    private val smartCycleIntervalSeconds: Int,
    private val minimumRequestGapMillis: Long = 1_000L,
    private val monthlyRequestBudget: Int? = null
) : OnchainPollingStrategy {
    override fun createBatches(
        provider: OnchainDataProvider,
        items: List<PreparedOnchainPollingItem>,
        mode: OnchainRefreshMode,
        fixedIntervalSeconds: Int,
        recurring: Boolean
    ): List<OnchainPollingBatch> {
        if (items.isEmpty()) return emptyList()
        val cycleSeconds = when (mode) {
            OnchainRefreshMode.SMART -> smartCycleIntervalSeconds
            OnchainRefreshMode.FIXED -> maxOf(fixedIntervalSeconds, smartCycleIntervalSeconds)
        }
        val batches = items
            .mapNotNull { target ->
                val chainId = target.providerItem.chainIndex?.takeIf(String::isNotBlank)
                    ?: return@mapNotNull null
                val address = target.providerItem.tokenAddress
                    ?.takeIf(String::isNotBlank)
                    ?.let { normalizeOnchainAddress(target.providerItem.chainFamily, it) }
                    ?: return@mapNotNull null
                PreparedTarget(chainId, address, target.originalItem.id)
            }
            .groupBy(PreparedTarget::chainId)
            .toSortedMap()
            .flatMap { (chainId, chainItems) ->
                chainItems
                    .groupBy(PreparedTarget::normalizedAddress)
                    .toSortedMap()
                    .entries
                    .chunked(maxAddressesPerRequest)
                    .mapIndexed { index, addressGroups ->
                        OnchainPollingBatch(
                            key = "${provider.name}:$chainId:$index",
                            provider = provider,
                            itemIds = addressGroups
                                .flatMap { entry -> entry.value.map(PreparedTarget::itemId) }
                                .toSortedSet(),
                            cycleIntervalMillis = cycleSeconds * 1_000L,
                            minimumRequestGapMillis = minimumRequestGapMillis,
                            recurring = recurring
                        )
                    }
            }
        val effectiveCycleMillis = maxOf(
            cycleSeconds * 1_000L,
            batches.size * minimumRequestGapMillis,
            monthlyBudgetCycleMillis(batches.size)
        )
        return batches.map { batch -> batch.copy(cycleIntervalMillis = effectiveCycleMillis) }
    }

    override fun retryDelayMillis(consecutiveFailures: Int): Long {
        val seconds = when (consecutiveFailures) {
            1 -> 5
            2 -> 10
            3 -> 20
            else -> 30
        }
        return seconds * 1_000L
    }

    private fun monthlyBudgetCycleMillis(batchCount: Int): Long {
        val budget = monthlyRequestBudget?.takeIf { it > 0 } ?: return 0L
        if (batchCount <= 0) return 0L
        return ceil(BILLING_WINDOW_SECONDS.toDouble() * batchCount / budget)
            .toLong() * 1_000L
    }

    private data class PreparedTarget(
        val chainId: String,
        val normalizedAddress: String,
        val itemId: String
    )
}

object OnchainPollingStrategies {
    val dexScreener: OnchainPollingStrategy = ChainBatchingOnchainPollingStrategy(
        maxAddressesPerRequest = 30,
        smartCycleIntervalSeconds = 30
    )

    val okxDex: OnchainPollingStrategy = ChainBatchingOnchainPollingStrategy(
        maxAddressesPerRequest = 100,
        smartCycleIntervalSeconds = 30,
        // OKX grants 100K Premium calls/month on the free tier. Reserve 20% for
        // searches, manual refreshes and other user-triggered calls.
        monthlyRequestBudget = 80_000
    )

    val default: OnchainPollingStrategy = dexScreener
}

private const val DEFAULT_ONCHAIN_CYCLE_SECONDS = 30
private const val BILLING_WINDOW_SECONDS = 31L * 24L * 60L * 60L
