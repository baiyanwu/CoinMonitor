package io.baiyanwu.coinmonitor.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.domain.model.NetworkLogEntry
import io.baiyanwu.coinmonitor.domain.repository.NetworkLogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class NetworkLogUiState(
    val recordingEnabled: Boolean = false,
    val entries: List<NetworkLogEntry> = emptyList()
)

class NetworkLogViewModel(
    private val networkLogRepository: NetworkLogRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(NetworkLogUiState())
    val uiState: StateFlow<NetworkLogUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                networkLogRepository.observeRecordingEnabled(),
                networkLogRepository.observeEntries()
            ) { recordingEnabled, entries ->
                NetworkLogUiState(
                    recordingEnabled = recordingEnabled,
                    entries = entries
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun setRecordingEnabled(enabled: Boolean) {
        networkLogRepository.setRecordingEnabled(enabled)
    }

    fun clear() {
        networkLogRepository.clear()
    }

    fun onEntryClick(entryId: Long) {
        // 详情页先预留事件出口，当前版本仍保持最轻量的一行日志模式。
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                NetworkLogViewModel(
                    networkLogRepository = container.networkLogRepository
                )
            }
        }
    }
}
