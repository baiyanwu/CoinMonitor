package io.baiyanwu.coinmonitor.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.domain.model.AppPreferences
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.domain.repository.AppPreferencesRepository
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class HomeUiState(
    val isLoaded: Boolean = false,
    val items: List<WatchItem> = emptyList(),
    val overlayIds: Set<String> = emptySet(),
    val overlayEnabled: Boolean = false,
    val isRefreshing: Boolean = false
)

class HomeViewModel(
    private val watchlistRepository: WatchlistRepository,
    private val overlayRepository: OverlayRepository,
    private val marketQuoteRepository: MarketQuoteRepository,
    private val appPreferencesRepository: AppPreferencesRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var currentItems: List<WatchItem> = emptyList()
    private var currentOverlayIds: Set<String> = emptySet()
    private var currentOverlayEnabled: Boolean = false
    private var refreshJob: Job? = null
    private var refreshIntervalMillis: Long =
        AppPreferences.DEFAULT_REFRESH_INTERVAL_SECONDS * 1_000L
    private var manualRefreshing: Boolean = false
    private val refreshMutex = Mutex()

    init {
        viewModelScope.launch {
            combine(
                watchlistRepository.observeWatchlist(),
                overlayRepository.observeOverlayItems(),
                overlayRepository.observeSettings(),
                appPreferencesRepository.observePreferences()
            ) { items, overlayItems, settings, preferences ->
                HomeUiPayload(
                    items = items,
                    overlayIds = overlayItems.map { it.id }.toSet(),
                    overlayEnabled = settings.enabled,
                    refreshIntervalMillis = preferences.refreshIntervalSeconds * 1_000L
                )
            }.collect { payload ->
                val items = payload.items
                currentItems = items
                currentOverlayIds = payload.overlayIds
                currentOverlayEnabled = payload.overlayEnabled
                refreshIntervalMillis = payload.refreshIntervalMillis
                publishUiState(isLoaded = true)
                restartRefreshLoop(items, payload.overlayEnabled)
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
        if (manualRefreshing) return
        viewModelScope.launch {
            manualRefreshing = true
            publishUiState(isLoaded = _uiState.value.isLoaded)
            runCatching {
                refreshQuotes(currentItems)
            }
            manualRefreshing = false
            publishUiState(isLoaded = _uiState.value.isLoaded)
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
                delay(refreshIntervalMillis)
                refreshQuotes(currentItems)
            }
        }
    }

    private suspend fun refreshQuotes(items: List<WatchItem>) {
        if (items.isEmpty()) return
        refreshMutex.withLock {
            val quotes = marketQuoteRepository.fetchQuotes(items)
            if (quotes.isNotEmpty()) {
                watchlistRepository.updateQuotes(quotes)
            }
        }
    }

    private fun publishUiState(isLoaded: Boolean) {
        _uiState.value = HomeUiState(
            isLoaded = isLoaded,
            items = currentItems,
            overlayIds = currentOverlayIds,
            overlayEnabled = currentOverlayEnabled,
            isRefreshing = manualRefreshing
        )
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    watchlistRepository = container.watchlistRepository,
                    overlayRepository = container.overlayRepository,
                    marketQuoteRepository = container.marketQuoteRepository,
                    appPreferencesRepository = container.appPreferencesRepository
                )
            }
        }
    }
}

private data class HomeUiPayload(
    val items: List<WatchItem>,
    val overlayIds: Set<String>,
    val overlayEnabled: Boolean,
    val refreshIntervalMillis: Long
)
