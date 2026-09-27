package io.baiyanwu.coinmonitor.data.repository

import io.baiyanwu.coinmonitor.data.refresh.OnchainPollingBatch
import io.baiyanwu.coinmonitor.data.refresh.OnchainPollingPlan
import io.baiyanwu.coinmonitor.data.refresh.PreparedOnchainPollingItem
import io.baiyanwu.coinmonitor.domain.model.AppPreferences
import io.baiyanwu.coinmonitor.domain.model.ChainFamily
import io.baiyanwu.coinmonitor.domain.model.MarketQuote
import io.baiyanwu.coinmonitor.domain.model.MarketType
import io.baiyanwu.coinmonitor.domain.model.OnchainDataProvider
import io.baiyanwu.coinmonitor.domain.model.OnchainRefreshMode
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

data class OnchainBatchExecutionResult(
    val quotes: List<MarketQuote>,
    val unresolvedItems: List<WatchItem>
)

/**
 * The single routing boundary for on-chain search, quote priority, provider adaptation and failover.
 * Schedulers receive provider-bound batches from here and never choose a provider themselves.
 */
class OnchainProviderRouter(
    providers: List<OnchainMarketProvider>,
    private val providerOrder: () -> List<OnchainDataProvider> = {
        AppPreferences.DEFAULT_ONCHAIN_PROVIDER_ORDER
    },
    val routingState: Flow<String> = flowOf("static")
) {
    private val providerByType = providers.associateBy(OnchainMarketProvider::type)

    suspend fun search(
        keyword: String,
        chainFamilyFilter: ChainFamily? = null
    ): List<WatchItem> {
        var lastError: Exception? = null
        var lastAttemptFailed = false
        orderedProviders().forEach { provider ->
            if (!provider.isConfigured()) return@forEach
            try {
                val results = provider.search(keyword, chainFamilyFilter)
                lastAttemptFailed = false
                if (results.isNotEmpty()) return results
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                lastError = error
                lastAttemptFailed = true
            }
        }
        if (lastAttemptFailed && lastError != null) throw lastError
        return emptyList()
    }

    fun createPollingPlan(
        items: List<WatchItem>,
        mode: OnchainRefreshMode,
        fixedIntervalSeconds: Int
    ): OnchainPollingPlan {
        val assignments = assignToFirstAvailableProvider(items)
        val batches = orderedProviders().flatMap { provider ->
            val targets = assignments[provider].orEmpty()
            provider.pollingStrategy.createBatches(
                provider = provider.type,
                items = targets,
                mode = mode,
                fixedIntervalSeconds = fixedIntervalSeconds,
                recurring = true
            )
        }
        return OnchainPollingPlan(batches)
    }

    fun createFallbackBatches(
        items: List<WatchItem>,
        failedProvider: OnchainDataProvider,
        mode: OnchainRefreshMode,
        fixedIntervalSeconds: Int
    ): List<OnchainPollingBatch> {
        if (items.isEmpty()) return emptyList()
        val providers = orderedProviders()
        val failedIndex = providers.indexOfFirst { it.type == failedProvider }
        if (failedIndex < 0 || failedIndex == providers.lastIndex) return emptyList()
        val fallbackProviders = providers.drop(failedIndex + 1)
        val assignments = linkedMapOf<OnchainMarketProvider, MutableList<PreparedOnchainPollingItem>>()
        items.filter { it.marketType == MarketType.ONCHAIN_TOKEN }.forEach { item ->
            val match = fallbackProviders.firstNotNullOfOrNull { provider ->
                if (!provider.isConfigured()) return@firstNotNullOfOrNull null
                provider.prepareItem(item)?.let { providerItem -> provider to providerItem }
            } ?: return@forEach
            assignments.getOrPut(match.first, ::mutableListOf) += PreparedOnchainPollingItem(
                originalItem = item,
                providerItem = match.second
            )
        }
        return fallbackProviders.flatMap { provider ->
            val targets = assignments[provider].orEmpty()
            provider.pollingStrategy.createBatches(
                provider = provider.type,
                items = targets,
                mode = mode,
                fixedIntervalSeconds = fixedIntervalSeconds,
                recurring = false
            )
        }
    }

    suspend fun executeBatch(
        batch: OnchainPollingBatch,
        items: List<WatchItem>
    ): OnchainBatchExecutionResult {
        val targetItems = items.filter { it.id in batch.itemIds }
        val provider = providerByType[batch.provider]
        if (provider == null || !provider.isConfigured()) {
            return OnchainBatchExecutionResult(emptyList(), targetItems)
        }
        val preparedById = targetItems.mapNotNull { item ->
            provider.prepareItem(item)?.let { prepared -> item.id to prepared }
        }.toMap()
        val quotes = try {
            provider.fetchQuotes(preparedById.values.toList())
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            emptyList()
        }.filter { it.id in preparedById }
        val quoteIds = quotes.mapTo(hashSetOf(), MarketQuote::id)
        return OnchainBatchExecutionResult(
            quotes = quotes,
            unresolvedItems = targetItems.filter { it.id !in quoteIds }
        )
    }

    suspend fun fetchQuotes(items: List<WatchItem>): List<MarketQuote> {
        val unresolved = items.associateByTo(linkedMapOf(), WatchItem::id)
        val resolved = linkedMapOf<String, MarketQuote>()
        orderedProviders().forEach { provider ->
            if (unresolved.isEmpty()) return@forEach
            if (!provider.isConfigured()) return@forEach
            val providerItems = unresolved.values.mapNotNull(provider::prepareItem)
            if (providerItems.isEmpty()) return@forEach
            val quotes = try {
                provider.fetchQuotes(providerItems)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                emptyList()
            }
            quotes.forEach { quote ->
                if (quote.id in unresolved) {
                    resolved[quote.id] = quote
                    unresolved.remove(quote.id)
                }
            }
        }
        return resolved.values.toList()
    }

    fun retryDelayMillis(provider: OnchainDataProvider, consecutiveFailures: Int): Long {
        return providerByType[provider]
            ?.pollingStrategy
            ?.retryDelayMillis(consecutiveFailures)
            ?: DEFAULT_RETRY_DELAY_MILLIS
    }

    private fun assignToFirstAvailableProvider(
        items: List<WatchItem>
    ): Map<OnchainMarketProvider, List<PreparedOnchainPollingItem>> {
        val assignments = linkedMapOf<OnchainMarketProvider, MutableList<PreparedOnchainPollingItem>>()
        val providers = orderedProviders()
        items.filter { it.marketType == MarketType.ONCHAIN_TOKEN }.forEach { item ->
            val match = providers.firstNotNullOfOrNull { provider ->
                if (!provider.isConfigured()) return@firstNotNullOfOrNull null
                provider.prepareItem(item)?.let { providerItem -> provider to providerItem }
            } ?: return@forEach
            assignments.getOrPut(match.first, ::mutableListOf) += PreparedOnchainPollingItem(
                originalItem = item,
                providerItem = match.second
            )
        }
        return assignments
    }

    private fun orderedProviders(): List<OnchainMarketProvider> {
        val order = AppPreferences.normalizeOnchainProviderOrder(providerOrder())
        return order.mapNotNull(providerByType::get)
    }

    private companion object {
        const val DEFAULT_RETRY_DELAY_MILLIS = 30_000L
    }
}
