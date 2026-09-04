package io.baiyanwu.coinmonitor.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.domain.model.OverlaySettings
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.domain.repository.OverlayRepository
import io.baiyanwu.coinmonitor.domain.repository.WatchlistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class OverlayItemsSettingsUiState(
    val settings: OverlaySettings = OverlaySettings(),
    val selectedItems: List<WatchItem> = emptyList(),
    val availableItems: List<WatchItem> = emptyList(),
    val isLoaded: Boolean = false,
    val noticeMessage: String? = null
)

class OverlayItemsSettingsViewModel(
    private val appContainer: AppContainer,
    private val overlayRepository: OverlayRepository,
    private val watchlistRepository: WatchlistRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(OverlayItemsSettingsUiState())
    val uiState: StateFlow<OverlayItemsSettingsUiState> = _uiState.asStateFlow()
    private var currentNoticeMessage: String? = null

    init {
        viewModelScope.launch {
            combine(
                overlayRepository.observeSettings(),
                watchlistRepository.observeHomeWatchlist()
            ) { settings, items ->
                val selectedItems = items
                    .filter(WatchItem::overlaySelected)
                    .sortedWith(
                        compareBy<WatchItem>(
                            { if (it.overlayOrder == null) 1 else 0 },
                            { it.overlayOrder ?: Long.MAX_VALUE },
                            WatchItem::addedAt,
                            WatchItem::id
                        )
                    )
                OverlayItemsSettingsUiState(
                    settings = settings,
                    selectedItems = selectedItems,
                    availableItems = items.filterNot(WatchItem::overlaySelected),
                    isLoaded = true,
                    noticeMessage = currentNoticeMessage
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun toggleItem(id: String) {
        viewModelScope.launch {
            runCatching {
                overlayRepository.toggleItem(id)
            }.onFailure { throwable ->
                currentNoticeMessage = throwable.message
                    ?: appContainer.appContext.getString(R.string.overlay_add_failed)
                _uiState.value = _uiState.value.copy(noticeMessage = currentNoticeMessage)
            }
        }
    }

    fun moveItem(id: String, targetBeforeId: String?) {
        viewModelScope.launch {
            runCatching {
                overlayRepository.moveOverlayItem(id, targetBeforeId)
            }.onFailure { throwable ->
                currentNoticeMessage = throwable.message
                    ?: appContainer.appContext.getString(R.string.overlay_reorder_failed)
                _uiState.value = _uiState.value.copy(noticeMessage = currentNoticeMessage)
            }
        }
    }

    fun consumeNotice() {
        if (currentNoticeMessage == null) return
        currentNoticeMessage = null
        _uiState.value = _uiState.value.copy(noticeMessage = null)
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                OverlayItemsSettingsViewModel(
                    appContainer = container,
                    overlayRepository = container.overlayRepository,
                    watchlistRepository = container.watchlistRepository
                )
            }
        }
    }
}
