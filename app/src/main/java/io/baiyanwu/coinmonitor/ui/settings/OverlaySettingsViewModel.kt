package io.baiyanwu.coinmonitor.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.domain.model.ArrangedEdgeDisplayMode
import io.baiyanwu.coinmonitor.domain.model.MarqueeSpeed
import io.baiyanwu.coinmonitor.domain.model.OverlayDisplayType
import io.baiyanwu.coinmonitor.domain.model.OverlayLeadingDisplayMode
import io.baiyanwu.coinmonitor.domain.model.OverlaySettings
import io.baiyanwu.coinmonitor.domain.repository.OverlayRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OverlaySettingsUiState(
    val settings: OverlaySettings = OverlaySettings(),
    val isLoaded: Boolean = false
)

class OverlaySettingsViewModel(
    private val overlayRepository: OverlayRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(OverlaySettingsUiState())
    val uiState: StateFlow<OverlaySettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            overlayRepository.observeSettings().collect { settings ->
                _uiState.value = OverlaySettingsUiState(
                    settings = settings,
                    isLoaded = true
                )
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

    fun setDisplayType(displayType: OverlayDisplayType) {
        viewModelScope.launch {
            overlayRepository.setDisplayType(displayType)
        }
    }

    fun setArrangedOpacity(opacity: Float) {
        viewModelScope.launch {
            overlayRepository.setArrangedOpacity(opacity)
        }
    }

    fun setArrangedMaxCount(maxCount: Int) {
        viewModelScope.launch {
            overlayRepository.setArrangedMaxCount(maxCount)
        }
    }

    fun setArrangedLeadingDisplayMode(mode: OverlayLeadingDisplayMode) {
        viewModelScope.launch {
            overlayRepository.setArrangedLeadingDisplayMode(mode)
        }
    }

    fun setArrangedFontScale(fontScale: Float) {
        viewModelScope.launch {
            overlayRepository.setArrangedFontScale(fontScale)
        }
    }

    fun setArrangedSnapToEdge(enabled: Boolean) {
        viewModelScope.launch {
            overlayRepository.setArrangedSnapToEdge(enabled)
        }
    }

    fun setArrangedEdgeDisplayMode(mode: ArrangedEdgeDisplayMode) {
        viewModelScope.launch {
            overlayRepository.setArrangedEdgeDisplayMode(mode)
        }
    }

    fun setArrangedEdgeTabOpacity(opacity: Float) {
        viewModelScope.launch {
            overlayRepository.setArrangedEdgeTabOpacity(opacity)
        }
    }

    fun setArrangedEdgeAutoCollapseSeconds(seconds: Int) {
        viewModelScope.launch {
            overlayRepository.setArrangedEdgeAutoCollapseSeconds(seconds)
        }
    }

    fun setMarqueeOpacity(opacity: Float) {
        viewModelScope.launch {
            overlayRepository.setMarqueeOpacity(opacity)
        }
    }

    fun setMarqueeMaxCount(maxCount: Int) {
        viewModelScope.launch {
            overlayRepository.setMarqueeMaxCount(maxCount)
        }
    }

    fun setMarqueeFontScale(fontScale: Float) {
        viewModelScope.launch {
            overlayRepository.setMarqueeFontScale(fontScale)
        }
    }

    fun setMarqueeSpeed(speed: MarqueeSpeed) {
        viewModelScope.launch {
            overlayRepository.setMarqueeSpeed(speed)
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                OverlaySettingsViewModel(
                    overlayRepository = container.overlayRepository
                )
            }
        }
    }
}
