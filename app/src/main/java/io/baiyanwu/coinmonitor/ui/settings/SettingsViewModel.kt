package io.baiyanwu.coinmonitor.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.domain.model.AppLanguage
import io.baiyanwu.coinmonitor.domain.model.AppPreferences
import io.baiyanwu.coinmonitor.domain.model.AppThemeMode
import io.baiyanwu.coinmonitor.domain.model.RefreshIntervalMode
import io.baiyanwu.coinmonitor.domain.repository.AppPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val preferences: AppPreferences = AppPreferences()
)

class SettingsViewModel(
    private val appPreferencesRepository: AppPreferencesRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        SettingsUiState(preferences = appPreferencesRepository.getPreferences())
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            appPreferencesRepository.observePreferences().collect { preferences ->
                _uiState.update { it.copy(preferences = preferences) }
            }
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            appPreferencesRepository.setThemeMode(mode)
        }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch {
            appPreferencesRepository.setLanguage(language)
        }
    }

    fun setRefreshIntervalSeconds(seconds: Int) {
        viewModelScope.launch {
            appPreferencesRepository.setRefreshIntervalSeconds(seconds)
        }
    }

    fun setRefreshIntervalMode(mode: RefreshIntervalMode) {
        viewModelScope.launch {
            appPreferencesRepository.setRefreshIntervalMode(mode)
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    appPreferencesRepository = container.appPreferencesRepository
                )
            }
        }
    }
}
