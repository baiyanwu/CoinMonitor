package io.baiyanwu.coinmonitor.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.data.refresh.GlobalQuoteRefreshCoordinator
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.domain.repository.OverlayRepository
import io.baiyanwu.coinmonitor.domain.repository.WatchlistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

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
    private val quoteRefreshCoordinator: GlobalQuoteRefreshCoordinator
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var currentItems: List<WatchItem> = emptyList()
    private var currentOverlayIds: Set<String> = emptySet()
    private var currentOverlayEnabled: Boolean = false
    private var manualRefreshing: Boolean = false

    init {
        viewModelScope.launch {
            combine(
                watchlistRepository.observeWatchlist(),
                overlayRepository.observeOverlayItems(),
                overlayRepository.observeSettings()
            ) { items, overlayItems, settings ->
                HomeUiPayload(
                    items = items,
                    overlayIds = overlayItems.map { it.id }.toSet(),
                    overlayEnabled = settings.enabled
                )
            }.collect { payload ->
                val items = payload.items
                currentItems = items
                currentOverlayIds = payload.overlayIds
                currentOverlayEnabled = payload.overlayEnabled
                publishUiState(isLoaded = true)
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
                quoteRefreshCoordinator.refreshNow()
            }
            manualRefreshing = false
            publishUiState(isLoaded = _uiState.value.isLoaded)
        }
    }

    fun setScreenActive(active: Boolean) {
        quoteRefreshCoordinator.setHomeActive(active)
    }

    override fun onCleared() {
        quoteRefreshCoordinator.setHomeActive(false)
        super.onCleared()
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
                    quoteRefreshCoordinator = container.globalQuoteRefreshCoordinator
                )
            }
        }
    }
}

private data class HomeUiPayload(
    val items: List<WatchItem>,
    val overlayIds: Set<String>,
    val overlayEnabled: Boolean
)
