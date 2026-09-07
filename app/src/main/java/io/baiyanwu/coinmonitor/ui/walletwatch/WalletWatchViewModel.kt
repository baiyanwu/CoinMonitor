package io.baiyanwu.coinmonitor.ui.walletwatch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.domain.model.WalletAddressParser
import io.baiyanwu.coinmonitor.domain.model.WalletAsset
import io.baiyanwu.coinmonitor.domain.model.WalletPortfolioSnapshot
import io.baiyanwu.coinmonitor.domain.model.WalletTotalResult
import io.baiyanwu.coinmonitor.domain.repository.OkxWalletCredentialsRepository
import io.baiyanwu.coinmonitor.domain.repository.WalletPortfolioCacheRepository
import io.baiyanwu.coinmonitor.domain.repository.WalletPortfolioRepository
import io.baiyanwu.coinmonitor.domain.repository.WalletWatchPreferencesRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WalletWatchUiState(
    val address: String = "",
    val credentialsReady: Boolean = false,
    val secureStorageAvailable: Boolean = true,
    val snapshot: WalletPortfolioSnapshot? = null,
    val isInitialLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val includeRiskInTotal: Boolean = false,
    val hideAssetsBelowOneUsd: Boolean = true,
    val hiddenAssetIds: Set<String> = emptySet(),
    val hiddenChainIndexes: Set<String> = emptySet(),
    val selectedChainIndex: String? = null,
    val addressError: Boolean = false,
    val errorMessage: String? = null
)

class WalletWatchViewModel(
    private val credentialsRepository: OkxWalletCredentialsRepository,
    private val portfolioRepository: WalletPortfolioRepository,
    private val portfolioCacheRepository: WalletPortfolioCacheRepository,
    private val preferencesRepository: WalletWatchPreferencesRepository
) : ViewModel() {
    private val initialAddress = preferencesRepository.getLastAddress().orEmpty()
    private val initialCredentialsReady = credentialsRepository.getCredentials().isReady
    private val _uiState = MutableStateFlow(WalletWatchUiState(
        credentialsReady = initialCredentialsReady,
        secureStorageAvailable = credentialsRepository.isSecureStorageAvailable(),
        address = initialAddress,
        includeRiskInTotal = initialAddress.takeIf(String::isNotBlank)
            ?.let(preferencesRepository::getIncludeRiskAssets) ?: false,
        hideAssetsBelowOneUsd = initialAddress.takeIf(String::isNotBlank)
            ?.let(preferencesRepository::getHideSmallAssets) ?: true,
        isInitialLoading = initialCredentialsReady && initialAddress.isNotBlank()
    ))
    val uiState: StateFlow<WalletWatchUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            credentialsRepository.observeCredentials().collect { credentials ->
                val becameReady = !_uiState.value.credentialsReady && credentials.isReady
                _uiState.update { it.copy(credentialsReady = credentials.isReady) }
                if (becameReady && _uiState.value.address.isNotBlank() && _uiState.value.snapshot == null) restoreCacheAndRefresh()
            }
        }
        if (_uiState.value.credentialsReady && _uiState.value.address.isNotBlank()) restoreCacheAndRefresh()
    }

    fun updateAddress(value: String) {
        _uiState.update { it.copy(address = value, addressError = false, errorMessage = null) }
    }

    fun query(refresh: Boolean = false) {
        val address = _uiState.value.address.trim()
        if (WalletAddressParser.classify(address) == null) {
            _uiState.update { it.copy(addressError = true, errorMessage = null) }
            return
        }
        if (!_uiState.value.credentialsReady) {
            viewModelScope.launch { preferencesRepository.saveLastAddress(address) }
            return
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            preferencesRepository.saveLastAddress(address)
            val includeRiskAssets = preferencesRepository.getIncludeRiskAssets(address)
            val hideSmallAssets = preferencesRepository.getHideSmallAssets(address)
            _uiState.update {
                it.copy(
                    address = address,
                    includeRiskInTotal = includeRiskAssets,
                    hideAssetsBelowOneUsd = hideSmallAssets,
                    addressError = false,
                    errorMessage = null,
                    isInitialLoading = !refresh && it.snapshot == null,
                    isRefreshing = refresh || it.snapshot != null
                )
            }
            runCatching { portfolioRepository.load(address, includeRiskAssets) }
                .onSuccess { snapshot ->
                    val hiddenChains = preferencesRepository.getHiddenChainIndexes(snapshot.address)
                    _uiState.update { current ->
                        val keepCurrentChain = current.snapshot?.address == snapshot.address &&
                            current.selectedChainIndex !in hiddenChains &&
                            snapshot.assets.any { it.chainIndex == current.selectedChainIndex }
                        current.copy(
                            snapshot = snapshot,
                            hiddenAssetIds = preferencesRepository.getHiddenAssetIds(snapshot.address),
                            hiddenChainIndexes = hiddenChains,
                            selectedChainIndex = if (keepCurrentChain) current.selectedChainIndex
                            else defaultWalletChainIndex(snapshot.assets.filter { it.chainIndex !in hiddenChains }),
                            isInitialLoading = false,
                            isRefreshing = false
                        )
                    }
                    viewModelScope.launch { portfolioCacheRepository.save(snapshot, includeRiskAssets) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isInitialLoading = false, isRefreshing = false, errorMessage = friendlyError(error)) }
                }
        }
    }

    private fun restoreCacheAndRefresh() {
        val address = _uiState.value.address.trim()
        viewModelScope.launch {
            val cached = portfolioCacheRepository.load(address)
            if (cached != null && _uiState.value.snapshot == null && _uiState.value.address.trim() == address) {
                val hiddenChains = preferencesRepository.getHiddenChainIndexes(address)
                _uiState.update {
                    it.copy(
                        snapshot = cached.snapshot,
                        includeRiskInTotal = preferencesRepository.getIncludeRiskAssets(address),
                        hideAssetsBelowOneUsd = preferencesRepository.getHideSmallAssets(address),
                        hiddenAssetIds = preferencesRepository.getHiddenAssetIds(address),
                        hiddenChainIndexes = hiddenChains,
                        selectedChainIndex = defaultWalletChainIndex(
                            cached.snapshot.assets.filter { asset ->
                                asset.chainIndex !in hiddenChains
                            }
                        ),
                        isInitialLoading = false,
                        isRefreshing = true
                    )
                }
            }
            query(refresh = cached != null)
        }
    }

    fun retry() = query(refresh = _uiState.value.snapshot != null)
    fun refresh() { if (_uiState.value.address.isNotBlank() && !_uiState.value.isRefreshing) query(refresh = true) }

    fun setIncludeRiskInTotal(include: Boolean) {
        if (_uiState.value.includeRiskInTotal == include) return
        _uiState.update { it.copy(includeRiskInTotal = include, isRefreshing = it.snapshot != null, errorMessage = null) }
        val snapshot = _uiState.value.snapshot ?: return
        viewModelScope.launch {
            preferencesRepository.saveIncludeRiskAssets(snapshot.address, include)
            when (val result = portfolioRepository.loadTotal(snapshot.address, include)) {
                is WalletTotalResult.Success -> _uiState.update {
                    it.copy(snapshot = snapshot.copy(totalValueUsd = result.totalValueUsd, totalIsEstimated = false, updatedAtMillis = System.currentTimeMillis()), isRefreshing = false)
                }
                is WalletTotalResult.Failure -> {
                    val fallback = snapshot.assets.asSequence().filter { include || !it.isRiskToken }
                        .mapNotNull { it.holdingValueUsd }.fold(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)
                    _uiState.update { it.copy(snapshot = snapshot.copy(totalValueUsd = fallback, totalIsEstimated = true), isRefreshing = false, errorMessage = result.message) }
                }
            }
        }
    }

    fun setHideAssetsBelowOneUsd(hide: Boolean) {
        _uiState.update { it.copy(hideAssetsBelowOneUsd = hide) }
        _uiState.value.snapshot?.address?.let { address ->
            viewModelScope.launch { preferencesRepository.saveHideSmallAssets(address, hide) }
        }
    }

    fun hideAsset(assetId: String) = updateHiddenAssets { it + assetId }

    fun showAsset(assetId: String) = updateHiddenAssets { it - assetId }

    private fun updateHiddenAssets(transform: (Set<String>) -> Set<String>) {
        val address = _uiState.value.snapshot?.address ?: return
        val updated = transform(_uiState.value.hiddenAssetIds)
        _uiState.update { it.copy(hiddenAssetIds = updated) }
        viewModelScope.launch { preferencesRepository.saveHiddenAssetIds(address, updated) }
    }

    fun hideChain(chainIndex: String) = updateHiddenChains { it + chainIndex }

    fun showChain(chainIndex: String) = updateHiddenChains { it - chainIndex }

    private fun updateHiddenChains(transform: (Set<String>) -> Set<String>) {
        val snapshot = _uiState.value.snapshot ?: return
        val updated = transform(_uiState.value.hiddenChainIndexes)
        val currentSelection = _uiState.value.selectedChainIndex
        val selectedChain = if (currentSelection != null && currentSelection !in updated) currentSelection
        else defaultWalletChainIndex(snapshot.assets.filter { it.chainIndex !in updated })
        _uiState.update { it.copy(hiddenChainIndexes = updated, selectedChainIndex = selectedChain) }
        viewModelScope.launch { preferencesRepository.saveHiddenChainIndexes(snapshot.address, updated) }
    }

    fun selectChain(chainIndex: String) {
        if (_uiState.value.snapshot?.assets?.any { it.chainIndex == chainIndex } == true) {
            _uiState.update { it.copy(selectedChainIndex = chainIndex) }
        }
    }

    private fun friendlyError(error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            message.contains("timestamp", true) || message.contains("50102") -> "设备时间与 OKX 服务端偏差过大，请校准系统时间后重试。"
            message.contains("signature", true) || message.contains("sign", true) -> "OKX 请求签名失败，请检查 Secret Key 和设备时间。"
            message.contains("authority", true) || message.contains("50114") || message.contains("50113") -> "OKX 凭证无效或权限不足，请检查 API 设置。"
            else -> message.ifBlank { "钱包资产查询失败，请稍后重试。" }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                WalletWatchViewModel(
                    container.okxWalletCredentialsRepository,
                    container.walletPortfolioRepository,
                    container.walletPortfolioCacheRepository,
                    container.walletWatchPreferencesRepository
                )
            }
        }
    }
}

internal fun defaultWalletChainIndex(assets: List<WalletAsset>): String? =
    assets.groupBy(WalletAsset::chainIndex)
        .maxByOrNull { (_, chainAssets) ->
            chainAssets.mapNotNull(WalletAsset::holdingValueUsd)
                .fold(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)
        }
        ?.key
