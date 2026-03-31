package io.baiyanwu.coinmonitor.ui.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.domain.repository.AppPreferencesRepository
import io.baiyanwu.coinmonitor.domain.repository.MarketQuoteRepository
import io.baiyanwu.coinmonitor.domain.repository.MarketSearchRepository
import io.baiyanwu.coinmonitor.domain.repository.OkxCredentialsRepository
import io.baiyanwu.coinmonitor.domain.repository.WatchlistRepository
import io.baiyanwu.coinmonitor.ui.AppConfigurationApplier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val query: String = "",
    val searchMode: SearchMode = SearchMode.EXCHANGE,
    val loading: Boolean = false,
    val hasSearched: Boolean = false,
    val results: List<WatchItem> = emptyList(),
    val addedIds: Set<String> = emptySet(),
    val errorMessage: String? = null,
    val hasOkxCredentials: Boolean = false
)

data class OnchainSearchSelection(
    val chainIndex: String
)

class SearchViewModel(
    private val appContext: Context,
    private val appPreferencesRepository: AppPreferencesRepository,
    private val watchlistRepository: WatchlistRepository,
    private val marketSearchRepository: MarketSearchRepository,
    private val marketQuoteRepository: MarketQuoteRepository,
    private val okxCredentialsRepository: OkxCredentialsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        SearchUiState(
            hasOkxCredentials = resolveOkxCredentialConfigured()
        )
    )
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            watchlistRepository.observeWatchlist().collect { items ->
                _uiState.update { it.copy(addedIds = items.map { row -> row.id }.toSet()) }
            }
        }
        viewModelScope.launch {
            okxCredentialsRepository.observeCredentials().collect { credentials ->
                _uiState.update {
                    it.copy(hasOkxCredentials = credentials.enabled && credentials.isReady)
                }
            }
        }
    }

    fun updateQuery(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun setSearchMode(mode: SearchMode) {
        val hasOkxCredentials = resolveOkxCredentialConfigured()
        _uiState.update {
            if (it.searchMode == mode) {
                it.copy(hasOkxCredentials = hasOkxCredentials)
            } else {
                it.copy(
                    searchMode = mode,
                    loading = false,
                    hasSearched = false,
                    errorMessage = null,
                    hasOkxCredentials = hasOkxCredentials
                )
            }
        }
    }

    fun clearQuery() {
        _uiState.update {
            it.copy(
                query = "",
                loading = false,
                hasSearched = false,
                results = emptyList(),
                errorMessage = null
            )
        }
    }

    /**
     * 链上模式必须显式带上当前选中的链，否则请求会退回成“全链搜索”，结果会被严重放大。
     */
    fun search(onchainSelection: OnchainSearchSelection? = null) {
        val hasOkxCredentials = resolveOkxCredentialConfigured()
        _uiState.update { it.copy(hasOkxCredentials = hasOkxCredentials) }
        val currentState = uiState.value.copy(hasOkxCredentials = hasOkxCredentials)
        val keyword = currentState.query.trim()
        if (keyword.isBlank()) return

        if (currentState.searchMode == SearchMode.ONCHAIN && !currentState.hasOkxCredentials) {
            viewModelScope.launch {
                _uiState.update {
                    it.copy(
                        loading = false,
                        hasSearched = true,
                        results = emptyList(),
                        errorMessage = AppConfigurationApplier.getString(
                            context = appContext,
                            preferences = appPreferencesRepository.getPreferences(),
                            resId = R.string.search_onchain_credential_missing
                        )
                    )
                }
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, hasSearched = true, errorMessage = null) }
            runCatching {
                when (currentState.searchMode) {
                    SearchMode.EXCHANGE -> marketSearchRepository.searchExchange(keyword)
                    SearchMode.ONCHAIN -> marketSearchRepository.searchOnchain(
                        keyword = keyword,
                        chainIndex = onchainSelection?.chainIndex.orEmpty()
                    )
                }
            }.onSuccess { results ->
                _uiState.update {
                    it.copy(
                        loading = false,
                        hasSearched = true,
                        results = results,
                        errorMessage = null
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        loading = false,
                        hasSearched = true,
                        errorMessage = error.message ?: AppConfigurationApplier.getString(
                            context = appContext,
                            preferences = appPreferencesRepository.getPreferences(),
                            resId = R.string.error_search_retry
                        )
                    )
                }
            }
        }
    }

    fun toggleWatchItem(item: WatchItem) {
        viewModelScope.launch {
            if (uiState.value.addedIds.contains(item.id)) {
                watchlistRepository.remove(item.id)
                return@launch
            }

            val itemToSave = item.copy(addedAt = System.currentTimeMillis())
            watchlistRepository.add(itemToSave)
            runCatching {
                marketQuoteRepository.fetchQuotes(listOf(itemToSave))
            }.onSuccess { quotes ->
                if (quotes.isNotEmpty()) {
                    watchlistRepository.updateQuotes(quotes)
                }
            }
        }
    }

    /**
     * 从 K 线页进入搜索时，点击结果后直接把标的补进观察列表并返回。
     */
    fun selectItemForKline(item: WatchItem, onCompleted: (String) -> Unit) {
        viewModelScope.launch {
            val targetItem = if (uiState.value.addedIds.contains(item.id)) {
                item
            } else {
                val itemToSave = item.copy(addedAt = System.currentTimeMillis())
                watchlistRepository.add(itemToSave)
                runCatching {
                    marketQuoteRepository.fetchQuotes(listOf(itemToSave))
                }.onSuccess { quotes ->
                    if (quotes.isNotEmpty()) {
                        watchlistRepository.updateQuotes(quotes)
                    }
                }
                itemToSave
            }
            onCompleted(targetItem.id)
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
                    marketQuoteRepository = container.marketQuoteRepository,
                    okxCredentialsRepository = container.okxCredentialsRepository
                )
            }
        }
    }

    /**
     * 搜索页和设置页必须共享同一套凭证来源，否则会出现“已经保存但页面仍提示未配置”的错觉。
     */
    private fun resolveOkxCredentialConfigured(): Boolean {
        val credentials = okxCredentialsRepository.getCredentials()
        return credentials.enabled && credentials.isReady
    }
}
