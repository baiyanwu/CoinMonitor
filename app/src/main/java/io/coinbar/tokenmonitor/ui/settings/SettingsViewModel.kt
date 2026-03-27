package io.coinbar.tokenmonitor.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.coinbar.tokenmonitor.data.AppContainer
import io.coinbar.tokenmonitor.domain.model.AppLanguage
import io.coinbar.tokenmonitor.domain.model.AppPreferences
import io.coinbar.tokenmonitor.domain.model.AppThemeMode
import io.coinbar.tokenmonitor.domain.repository.AppPreferencesRepository
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
