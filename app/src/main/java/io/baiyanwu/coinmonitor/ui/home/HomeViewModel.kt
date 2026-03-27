package io.baiyanwu.coinmonitor.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.domain.repository.MarketQuoteRepository
import io.baiyanwu.coinmonitor.domain.repository.OverlayRepository
import io.baiyanwu.coinmonitor.domain.repository.WatchlistRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class HomeUiState(
    val items: List<WatchItem> = emptyList(),
    val overlayIds: Set<String> = emptySet(),
    val overlayEnabled: Boolean = false
)

class HomeViewModel(
    private val watchlistRepository: WatchlistRepository,
    private val overlayRepository: OverlayRepository,
    private val marketQuoteRepository: MarketQuoteRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var currentItems: List<WatchItem> = emptyList()
    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            combine(
                watchlistRepository.observeWatchlist(),
                overlayRepository.observeOverlayItems(),
                overlayRepository.observeSettings()
            ) { items, overlayItems, settings ->
                Triple(items, overlayItems.map { it.id }.toSet(), settings.enabled)
            }.collect { (items, overlayIds, overlayEnabled) ->
                currentItems = items
                _uiState.value = HomeUiState(
                    items = items,
                    overlayIds = overlayIds,
                    overlayEnabled = overlayEnabled
                )
                restartRefreshLoop(items, overlayEnabled)
            }
        }
    }

    fun removeWatchItem(id: String) {
        viewModelScope.launch {
            watchlistRepository.remove(id)
        }
    }

    fun toggleOverlay(id: String) {
        viewModelScope.launch {
            overlayRepository.toggleItem(id)
        }
    }

    fun refreshNow() {
        viewModelScope.launch {
            refreshQuotes(currentItems)
        }
    }

    override fun onCleared() {
        refreshJob?.cancel()
        super.onCleared()
    }

    private fun restartRefreshLoop(items: List<WatchItem>, overlayEnabled: Boolean) {
        refreshJob?.cancel()
        if (items.isEmpty() || overlayEnabled) return

        refreshJob = viewModelScope.launch {
            refreshQuotes(items)
            while (isActive) {
                delay(3_000)
                refreshQuotes(currentItems)
            }
        }
    }

    private suspend fun refreshQuotes(items: List<WatchItem>) {
        if (items.isEmpty()) return
        val quotes = marketQuoteRepository.fetchQuotes(items)
        if (quotes.isNotEmpty()) {
            watchlistRepository.updateQuotes(quotes)
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    watchlistRepository = container.watchlistRepository,
                    overlayRepository = container.overlayRepository,
                    marketQuoteRepository = container.marketQuoteRepository
                )
            }
        }
    }
}

