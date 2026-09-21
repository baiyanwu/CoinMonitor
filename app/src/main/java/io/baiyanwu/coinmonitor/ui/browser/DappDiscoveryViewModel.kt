package io.baiyanwu.coinmonitor.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.data.repository.DappDiscoveryRepository
import io.baiyanwu.coinmonitor.domain.model.DappDefinition
import io.baiyanwu.coinmonitor.domain.model.DappSearchHistory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DappDiscoveryUiState(
    val catalog: List<DappDefinition> = emptyList(),
    val history: List<DappSearchHistory> = emptyList()
)

class DappDiscoveryViewModel(
    private val repository: DappDiscoveryRepository
) : ViewModel() {
    val uiState: StateFlow<DappDiscoveryUiState> = repository.observeSearchHistory()
        .map { history ->
            DappDiscoveryUiState(
                catalog = repository.catalog,
                history = history
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DappDiscoveryUiState(catalog = repository.catalog)
        )

    fun recordSearch(query: String) {
        viewModelScope.launch { repository.recordSearch(query) }
    }

    fun clearHistory() {
        viewModelScope.launch { repository.clearSearchHistory() }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DappDiscoveryViewModel(container.dappDiscoveryRepository) as T
                }
            }
    }
}
