package io.baiyanwu.coinmonitor.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.domain.model.OkxApiCredentials
import io.baiyanwu.coinmonitor.domain.repository.OkxCredentialsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ThirdPartyApiSettingsUiState(
    val enabled: Boolean = false,
    val apiKey: String = "",
    val secretKey: String = "",
    val passphrase: String = "",
    val savedFlag: Boolean = false,
    val clearedFlag: Boolean = false
) {
    val isReadyToEnable: Boolean
        get() = apiKey.isNotBlank() && secretKey.isNotBlank() && passphrase.isNotBlank()
}

class ThirdPartyApiSettingsViewModel(
    private val okxCredentialsRepository: OkxCredentialsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ThirdPartyApiSettingsUiState())
    val uiState: StateFlow<ThirdPartyApiSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            okxCredentialsRepository.observeCredentials().collect { credentials ->
                _uiState.value = credentials.toUiState()
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        _uiState.update { it.copy(enabled = enabled, savedFlag = false, clearedFlag = false) }
    }

    fun updateApiKey(value: String) {
        _uiState.update { it.copy(apiKey = value, savedFlag = false, clearedFlag = false) }
    }

    fun updateSecretKey(value: String) {
        _uiState.update { it.copy(secretKey = value, savedFlag = false, clearedFlag = false) }
    }

    fun updatePassphrase(value: String) {
        _uiState.update { it.copy(passphrase = value, savedFlag = false, clearedFlag = false) }
    }

    fun saveCredentials() {
        val snapshot = _uiState.value
        viewModelScope.launch {
            okxCredentialsRepository.saveCredentials(
                enabled = snapshot.enabled,
                apiKey = snapshot.apiKey,
                secretKey = snapshot.secretKey,
                passphrase = snapshot.passphrase
            )
            _uiState.update { it.copy(savedFlag = true, clearedFlag = false) }
        }
    }

    fun clearCredentials() {
        viewModelScope.launch {
            okxCredentialsRepository.clearCredentials()
            _uiState.update { it.copy(clearedFlag = true, savedFlag = false) }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ThirdPartyApiSettingsViewModel(
                    okxCredentialsRepository = container.okxCredentialsRepository
                )
            }
        }
    }
}

private fun OkxApiCredentials.toUiState(): ThirdPartyApiSettingsUiState {
    return ThirdPartyApiSettingsUiState(
        enabled = enabled,
        apiKey = apiKey,
        secretKey = secretKey,
        passphrase = passphrase
    )
}

