package io.baiyanwu.coinmonitor.ui.kline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.domain.model.KlineIndicatorSettings
import io.baiyanwu.coinmonitor.domain.repository.AppPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * K 线指标配置页状态。
 */
data class KlineIndicatorSettingsUiState(
    val indicatorSettings: KlineIndicatorSettings = KlineIndicatorSettings()
)

/**
 * K 线指标配置页 ViewModel。
 *
 * 主副图指标统一落到应用偏好中，K 线主页面和配置页都观察同一份数据，
 * 可以避免页面间手动回传结果。
 */
class KlineIndicatorSettingsViewModel(
    private val appPreferencesRepository: AppPreferencesRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        KlineIndicatorSettingsUiState(
            indicatorSettings = appPreferencesRepository.getPreferences().klineIndicatorSettings
        )
    )
    val uiState: StateFlow<KlineIndicatorSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            appPreferencesRepository.observePreferences().collect { preferences ->
                _uiState.update {
                    it.copy(
                        indicatorSettings = preferences.klineIndicatorSettings
                    )
                }
            }
        }
    }

    /**
     * 保存整套指标配置。
     */
    fun saveIndicatorSettings(settings: KlineIndicatorSettings) {
        viewModelScope.launch {
            appPreferencesRepository.setKlineIndicatorSettings(settings)
        }
    }

    companion object {

        /**
         * 配置页 ViewModel 工厂。
         */
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                KlineIndicatorSettingsViewModel(
                    appPreferencesRepository = container.appPreferencesRepository
                )
            }
        }
    }
}
