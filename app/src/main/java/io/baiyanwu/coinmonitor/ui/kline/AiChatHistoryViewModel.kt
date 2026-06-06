package io.baiyanwu.coinmonitor.ui.kline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.domain.model.AiChatSessionSummary
import io.baiyanwu.coinmonitor.domain.repository.AiChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class AiChatHistoryUiState(
    val sessions: List<AiChatSessionSummary> = emptyList()
)

class AiChatHistoryViewModel(
    private val aiChatRepository: AiChatRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(AiChatHistoryUiState())
    val uiState: StateFlow<AiChatHistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            aiChatRepository.observeSessionSummaries().collectLatest { sessions ->
                _uiState.value = AiChatHistoryUiState(sessions = sessions)
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AiChatHistoryViewModel(
                    aiChatRepository = container.aiChatRepository
                )
            }
        }
    }
}
