package io.baiyanwu.coinmonitor.ui.settings

import io.baiyanwu.coinmonitor.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.domain.model.OverlayLeadingDisplayMode
import io.baiyanwu.coinmonitor.domain.model.OverlaySettings
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.domain.repository.OverlayRepository
import io.baiyanwu.coinmonitor.domain.repository.WatchlistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class OverlaySettingsUiState(
    val settings: OverlaySettings = OverlaySettings(),
    val items: List<WatchItem> = emptyList(),
    val isLoaded: Boolean = false,
    val noticeMessage: String? = null
)

class OverlaySettingsViewModel(
    private val appContainer: AppContainer,
    private val overlayRepository: OverlayRepository,
    private val watchlistRepository: WatchlistRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(OverlaySettingsUiState())
    val uiState: StateFlow<OverlaySettingsUiState> = _uiState.asStateFlow()
    private var currentNoticeMessage: String? = null

    init {
        viewModelScope.launch {
            combine(
                overlayRepository.observeSettings(),
                watchlistRepository.observeWatchlist()
            ) { settings, items ->
                OverlaySettingsUiState(
                    settings = settings,
                    items = items,
                    isLoaded = true,
                    noticeMessage = currentNoticeMessage
                )
            }.collect {
                _uiState.value = it
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            overlayRepository.setEnabled(enabled)
        }
    }

    fun setLocked(locked: Boolean) {
        viewModelScope.launch {
            overlayRepository.setLocked(locked)
        }
    }

    fun setOpacity(opacity: Float) {
        viewModelScope.launch {
            overlayRepository.setOpacity(opacity)
        }
    }

    fun setMaxCount(maxCount: Int) {
        viewModelScope.launch {
            overlayRepository.setMaxCount(maxCount)
        }
    }

    fun setLeadingDisplayMode(mode: OverlayLeadingDisplayMode) {
        viewModelScope.launch {
            overlayRepository.setLeadingDisplayMode(mode)
        }
    }

    fun setFontScale(fontScale: Float) {
        viewModelScope.launch {
            overlayRepository.setFontScale(fontScale)
        }
    }

    fun setSnapToEdge(enabled: Boolean) {
        viewModelScope.launch {
            overlayRepository.setSnapToEdge(enabled)
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

    fun consumeNotice() {
        if (currentNoticeMessage == null) return
        currentNoticeMessage = null
        _uiState.value = _uiState.value.copy(noticeMessage = null)
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                OverlaySettingsViewModel(
                    appContainer = container,
                    overlayRepository = container.overlayRepository,
                    watchlistRepository = container.watchlistRepository
                )
            }
        }
    }
}
