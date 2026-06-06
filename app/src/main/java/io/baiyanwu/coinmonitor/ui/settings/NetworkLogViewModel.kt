package io.baiyanwu.coinmonitor.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.domain.model.NetworkLogEntry
import io.baiyanwu.coinmonitor.domain.model.NetworkLogProtocol
import io.baiyanwu.coinmonitor.domain.repository.NetworkLogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * 网络日志页状态。
 */
data class NetworkLogUiState(
    val recordingEnabled: Boolean = false,
    val httpEnabled: Boolean = true,
    val wssEnabled: Boolean = true,
    val entries: List<NetworkLogEntry> = emptyList()
)

/**
 * 网络日志页 ViewModel。
 *
 * 统一聚合总录制开关、协议开关和日志列表，
 * 便于在排查 K 线问题时快速切到“只看 HTTP”或“只看 WSS”。
 */
class NetworkLogViewModel(
    private val networkLogRepository: NetworkLogRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(NetworkLogUiState())
    val uiState: StateFlow<NetworkLogUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                networkLogRepository.observeRecordingSettings(),
                networkLogRepository.observeEntries()
            ) { recordingSettings, entries ->
                NetworkLogUiState(
                    recordingEnabled = recordingSettings.recordingEnabled,
                    httpEnabled = recordingSettings.httpEnabled,
                    wssEnabled = recordingSettings.wssEnabled,
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

    /**
     * 切换 HTTP 协议日志录制。
     */
    fun setHttpEnabled(enabled: Boolean) {
        networkLogRepository.setProtocolEnabled(NetworkLogProtocol.HTTP, enabled)
    }

    /**
     * 切换 WSS 协议日志录制。
     */
    fun setWssEnabled(enabled: Boolean) {
        networkLogRepository.setProtocolEnabled(NetworkLogProtocol.WSS, enabled)
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
