package io.baiyanwu.coinmonitor.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import android.content.Context
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.domain.repository.AppPreferencesRepository
import io.baiyanwu.coinmonitor.domain.repository.MarketQuoteRepository
import io.baiyanwu.coinmonitor.domain.repository.MarketSearchRepository
import io.baiyanwu.coinmonitor.domain.repository.WatchlistRepository
import io.baiyanwu.coinmonitor.ui.AppConfigurationApplier
import io.baiyanwu.coinmonitor.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    val hasSearched: Boolean = false,
    val results: List<WatchItem> = emptyList(),
    val addedIds: Set<String> = emptySet(),
    val errorMessage: String? = null
)

class SearchViewModel(
    private val appContext: Context,
    private val appPreferencesRepository: AppPreferencesRepository,
    private val watchlistRepository: WatchlistRepository,
    private val marketSearchRepository: MarketSearchRepository,
    private val marketQuoteRepository: MarketQuoteRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            watchlistRepository.observeWatchlist().collect { items ->
                _uiState.update { it.copy(addedIds = items.map { row -> row.id }.toSet()) }
            }
        }
    }

    fun updateQuery(query: String) {
        _uiState.update { it.copy(query = query) }
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

    fun search() {
        val keyword = uiState.value.query.trim()
        if (keyword.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, hasSearched = true, errorMessage = null) }
            runCatching {
                marketSearchRepository.search(keyword)
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
