package io.baiyanwu.coinmonitor.ui.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.data.repository.PartialExchangeSearchException
import io.baiyanwu.coinmonitor.domain.model.ChainFamily
import io.baiyanwu.coinmonitor.domain.model.OnchainPoolOption
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.domain.model.normalizeOnchainAddress
import io.baiyanwu.coinmonitor.domain.model.onchainAddressesEqual
import io.baiyanwu.coinmonitor.domain.repository.AppPreferencesRepository
import io.baiyanwu.coinmonitor.domain.repository.MarketQuoteRepository
import io.baiyanwu.coinmonitor.domain.repository.MarketSearchRepository
import io.baiyanwu.coinmonitor.domain.repository.WatchlistRepository
import io.baiyanwu.coinmonitor.ui.AppConfigurationApplier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SearchMode {
    EXCHANGE,
    ONCHAIN
}

enum class SearchEntryMode {
    HOME,
    KLINE
}

data class SearchUiState(
    val searchMode: SearchMode = SearchMode.EXCHANGE,
    val exchangePage: SearchPageState = SearchPageState(),
    val onchainPage: SearchPageState = SearchPageState(),
    val addedSemanticKeys: Set<String> = emptySet(),
    val existingIdsBySemanticKey: Map<String, String> = emptyMap()
) {
    fun pageState(mode: SearchMode): SearchPageState = when (mode) {
        SearchMode.EXCHANGE -> exchangePage
        SearchMode.ONCHAIN -> onchainPage
    }

    val activePageState: SearchPageState
        get() = pageState(searchMode)
}

data class SearchPageState(
    val query: String = "",
    val loading: Boolean = false,
    val hasSearched: Boolean = false,
    val results: List<WatchItem> = emptyList(),
    val errorMessage: String? = null
)

internal fun SearchUiState.updatePageState(
    mode: SearchMode,
    transform: (SearchPageState) -> SearchPageState
): SearchUiState = when (mode) {
    SearchMode.EXCHANGE -> copy(exchangePage = transform(exchangePage))
    SearchMode.ONCHAIN -> copy(onchainPage = transform(onchainPage))
}

internal fun SearchPageState.withSearchResults(results: List<WatchItem>): SearchPageState = copy(
    loading = false,
    hasSearched = true,
    results = results,
    errorMessage = null
)

internal fun shouldSkipDuplicateSearch(
    submittedQuery: String?,
    currentQuery: String,
    pageState: SearchPageState
): Boolean {
    return submittedQuery == currentQuery &&
        (pageState.loading || (pageState.hasSearched && pageState.errorMessage == null))
}

internal fun SearchPageState.withSelectedOnchainPool(
    semanticKey: String,
    option: OnchainPoolOption
): SearchPageState = copy(
    results = results.map { item ->
        if (item.semanticKey == semanticKey) item.withSelectedPool(option) else item
    }
)

private fun WatchItem.withSelectedPool(option: OnchainPoolOption): WatchItem = copy(
    symbol = option.pairLabel,
    poolAddress = normalizeOnchainAddress(chainFamily, option.poolAddress),
    poolTokenSide = option.tokenSide,
    lastPrice = option.priceUsd,
    change24hPercent = option.change24hPercent,
    marketCap = option.marketCap,
    lastUpdatedAt = System.currentTimeMillis(),
    selectedPool = option
)

internal class LatestPoolSelectionTracker {
    private val generations = mutableMapOf<String, Long>()

    @Synchronized
    fun next(semanticKey: String): Long {
        val generation = (generations[semanticKey] ?: 0L) + 1L
        generations[semanticKey] = generation
        return generation
    }

    @Synchronized
    fun isCurrent(semanticKey: String, generation: Long): Boolean {
        return generations[semanticKey] == generation
    }
}

internal fun CoroutineScope.createOrderedPoolSelectionJob(
    previousJob: Job?,
    block: suspend () -> Unit
): Job = launch(start = CoroutineStart.LAZY) {
    previousJob?.join()
    block()
}

class SearchViewModel(
    private val appContext: Context,
    private val appPreferencesRepository: AppPreferencesRepository,
    private val watchlistRepository: WatchlistRepository,
    private val marketSearchRepository: MarketSearchRepository,
    private val marketQuoteRepository: MarketQuoteRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    private val searchJobs = mutableMapOf<SearchMode, Job>()
    private val submittedQueries = mutableMapOf<SearchMode, String>()
    private val poolSelectionJobs = mutableMapOf<String, Job>()
    private val poolSelectionTracker = LatestPoolSelectionTracker()
    private var persistedItemsBySemanticKey: Map<String, WatchItem> = emptyMap()

    init {
        viewModelScope.launch {
            watchlistRepository.observeWatchlist().collect { items ->
                val existingIds = items.associate { row -> row.semanticKey to row.id }
                persistedItemsBySemanticKey = items.associateBy(WatchItem::semanticKey)
                _uiState.update { state ->
                    state.copy(
                        addedSemanticKeys = existingIds.keys,
                        existingIdsBySemanticKey = existingIds,
                        onchainPage = state.onchainPage.copy(
                            results = state.onchainPage.results.map(::bindPersistedPoolSelection)
                        )
                    )
                }
            }
        }
    }

    fun updateQuery(mode: SearchMode, query: String) {
        searchJobs.remove(mode)?.cancel()
        val normalizedQuery = normalizeSubmittedQuery(mode, query)
        if (submittedQueries[mode] != normalizedQuery) {
            submittedQueries.remove(mode)
        }
        _uiState.update { state ->
            state.updatePageState(mode) {
                it.copy(
                    query = query,
                    loading = false,
                    hasSearched = false,
                    results = emptyList(),
                    errorMessage = null
                )
            }
        }
    }

    fun setSearchMode(mode: SearchMode) {
        _uiState.update { state ->
            if (state.searchMode == mode) state else state.copy(searchMode = mode)
        }
    }

    fun clearQuery(mode: SearchMode) {
        searchJobs.remove(mode)?.cancel()
        submittedQueries.remove(mode)
        _uiState.update { state ->
            state.updatePageState(mode) { SearchPageState() }
        }
    }

    fun search(mode: SearchMode) {
        val currentState = uiState.value
        val keyword = currentState.pageState(mode).query.trim()
        if (keyword.isBlank()) return
        val submittedQuery = normalizeSubmittedQuery(mode, keyword)
        if (shouldSkipDuplicateSearch(
                submittedQuery = submittedQueries[mode],
                currentQuery = submittedQuery,
                pageState = currentState.pageState(mode)
            )
        ) {
            return
        }

        searchJobs.remove(mode)?.cancel()
        submittedQueries[mode] = submittedQuery
        searchJobs[mode] = viewModelScope.launch {
            _uiState.update { state ->
                state.updatePageState(mode) {
                    it.copy(
                        loading = true,
                        hasSearched = true,
                        results = emptyList(),
                        errorMessage = null
                    )
                }
            }
            try {
                when (mode) {
                    SearchMode.EXCHANGE -> {
                        marketSearchRepository.searchExchange(keyword).collect { results ->
                            _uiState.update { state ->
                                state.updatePageState(mode) {
                                    it.withSearchResults(results)
                                }
                            }
                        }
                    }
                    SearchMode.ONCHAIN -> {
                        val results = marketSearchRepository.searchOnchain(keyword)
                            .map(::bindPersistedPoolSelection)
                        _uiState.update { state ->
                            state.updatePageState(mode) {
                                it.withSearchResults(results)
                            }
                        }
                    }
                }
                _uiState.update { state ->
                    state.updatePageState(mode) {
                        it.copy(
                            loading = false,
                            hasSearched = true,
                            errorMessage = null
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (submittedQueries[mode] == submittedQuery) {
                    submittedQueries.remove(mode)
                }
                val preferences = appPreferencesRepository.getPreferences()
                val errorMessage = if (error is PartialExchangeSearchException) {
                    AppConfigurationApplier.getString(
                        context = appContext,
                        preferences = preferences,
                        resId = R.string.error_search_partial_sources
                    )
                } else {
                    error.message ?: AppConfigurationApplier.getString(
                        context = appContext,
                        preferences = preferences,
                        resId = R.string.error_search_retry
                    )
                }
                _uiState.update { state ->
                    state.updatePageState(mode) {
                        it.copy(
                            loading = false,
                            hasSearched = true,
                            errorMessage = errorMessage
                        )
                    }
                }
            }
        }
    }

    private fun normalizeSubmittedQuery(mode: SearchMode, query: String): String {
        val trimmed = query.trim()
        return if (mode == SearchMode.EXCHANGE) trimmed.uppercase() else trimmed
    }

    fun toggleWatchItem(item: WatchItem) {
        viewModelScope.launch {
            val existingId = uiState.value.existingIdsBySemanticKey[item.semanticKey]
            if (existingId != null) {
                watchlistRepository.remove(existingId)
                return@launch
            }

            val itemToSave = item.copy(addedAt = System.currentTimeMillis())
            addAndRefresh(itemToSave)
        }
    }

    fun selectOnchainPool(item: WatchItem, option: OnchainPoolOption) {
        if (item.poolAddress != null &&
            item.chainFamily != null &&
            onchainAddressesEqual(item.chainFamily, item.poolAddress, option.poolAddress)
        ) {
            return
        }

        val updatedItem = item.withSelectedPool(option)
        _uiState.update { state ->
            state.updatePageState(SearchMode.ONCHAIN) { page ->
                page.withSelectedOnchainPool(item.semanticKey, option)
            }
        }

        val existingId = uiState.value.existingIdsBySemanticKey[item.semanticKey] ?: return
        val semanticKey = item.semanticKey
        val generation = poolSelectionTracker.next(semanticKey)
        val previousJob = poolSelectionJobs.remove(semanticKey)
        previousJob?.cancel()
        val job = viewModelScope.createOrderedPoolSelectionJob(previousJob) {
            try {
                // 等上一代数据库写入彻底结束后再落新选择，保证最后一次点击最终胜出。
                persistPoolSelectionAndRefresh(existingId, updatedItem) {
                    poolSelectionTracker.isCurrent(semanticKey, generation)
                }
            } finally {
                if (poolSelectionTracker.isCurrent(semanticKey, generation)) {
                    poolSelectionJobs.remove(semanticKey)
                }
            }
        }
        poolSelectionJobs[semanticKey] = job
        job.start()
    }

    /**
     * 从 K 线页进入搜索时，点击结果后直接把标的补进观察列表并返回。
     */
    fun selectItemForKline(item: WatchItem, onCompleted: (String) -> Unit) {
        viewModelScope.launch {
            val existingId = uiState.value.existingIdsBySemanticKey[item.semanticKey]
            val targetId = if (existingId != null) {
                persistPoolSelectionAndRefresh(existingId, item)
                existingId
            } else {
                val itemToSave = item.copy(addedAt = System.currentTimeMillis())
                addAndRefresh(itemToSave).id
            }
            onCompleted(targetId)
        }
    }

    private suspend fun addAndRefresh(item: WatchItem): WatchItem {
        watchlistRepository.add(item)
        val persistedItem = watchlistRepository.getWatchlist()
            .firstOrNull { it.semanticKey == item.semanticKey }
            ?: item
        val quotes = try {
            marketQuoteRepository.fetchQuotes(listOf(persistedItem))
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            emptyList()
        }
        if (quotes.isNotEmpty()) {
            watchlistRepository.updateQuotes(quotes)
        }
        return persistedItem
    }

    private fun bindPersistedPoolSelection(item: WatchItem): WatchItem {
        val persisted = persistedItemsBySemanticKey[item.semanticKey] ?: return item
        val persistedPool = persisted.poolAddress?.takeIf(String::isNotBlank) ?: return item
        val family = item.chainFamily ?: return item
        val matchingOption = item.poolOptions.firstOrNull { option ->
            onchainAddressesEqual(family, option.poolAddress, persistedPool)
        } ?: return item
        return item.withSelectedPool(matchingOption)
    }

    private suspend fun persistPoolSelectionAndRefresh(
        existingId: String,
        item: WatchItem,
        isCurrent: () -> Boolean = { true }
    ) {
        if (!isCurrent()) return
        val poolAddress = item.poolAddress?.takeIf(String::isNotBlank) ?: return
        val side = item.poolTokenSide ?: return
        val persisted = persistedItemsBySemanticKey[item.semanticKey]
        val family = item.chainFamily ?: persisted?.chainFamily ?: ChainFamily.EVM
        val unchanged = persisted?.poolAddress?.let { currentPool ->
            onchainAddressesEqual(family, currentPool, poolAddress) && persisted.poolTokenSide == side
        } == true
        if (unchanged) return

        if (!watchlistRepository.updateOnchainPoolBinding(existingId, poolAddress, side)) return
        if (!isCurrent()) return
        val quotes = try {
            marketQuoteRepository.fetchQuotes(listOf(item.copy(id = existingId)))
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            emptyList()
        }
        if (!isCurrent()) return
        if (quotes.isNotEmpty()) {
            watchlistRepository.updateQuotes(quotes)
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SearchViewModel(
                    appContext = container.appContext,
                    appPreferencesRepository = container.appPreferencesRepository,
                    watchlistRepository = container.watchlistRepository,
                    marketSearchRepository = container.marketSearchRepository,
                    marketQuoteRepository = container.marketQuoteRepository
                )
            }
        }
    }
}
