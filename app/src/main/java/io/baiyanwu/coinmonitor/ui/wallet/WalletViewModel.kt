package io.baiyanwu.coinmonitor.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.domain.model.SelfCustodyAsset
import io.baiyanwu.coinmonitor.domain.model.WalletNetwork
import io.baiyanwu.coinmonitor.domain.model.WalletNetworkConfiguration
import io.baiyanwu.coinmonitor.domain.model.WalletPortfolio
import io.baiyanwu.coinmonitor.domain.model.WalletPrivateKeyType
import io.baiyanwu.coinmonitor.domain.model.WalletProfile
import io.baiyanwu.coinmonitor.domain.repository.SelfCustodyWalletRepository
import io.baiyanwu.coinmonitor.domain.repository.WalletNetworkSettingsRepository
import io.baiyanwu.coinmonitor.domain.repository.WalletTransferEstimate
import io.baiyanwu.coinmonitor.domain.repository.WalletTransferRequest
import io.baiyanwu.coinmonitor.domain.repository.WalletVaultRepository
import io.baiyanwu.coinmonitor.domain.repository.WalletVaultState
import io.baiyanwu.coinmonitor.domain.repository.WalletWatchPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

enum class WalletContentTab { ASSETS, ACTIVITY }
enum class WalletPage { HOME, ADD, MANAGE, RECEIVE, SEND, BACKUP, SECURITY }
enum class WalletImportMode { MNEMONIC, PRIVATE_KEY }

data class WalletSendState(
    val asset: SelfCustodyAsset? = null,
    val recipient: String = "",
    val amount: String = "",
    val sendMaximum: Boolean = false,
    val estimating: Boolean = false,
    val estimate: WalletTransferEstimate? = null,
    val confirming: Boolean = false,
    val broadcasting: Boolean = false,
    val submittedHash: String? = null,
    val errorMessage: String? = null
)

data class WalletUiState(
    val vault: WalletVaultState = WalletVaultState(),
    val networkConfiguration: WalletNetworkConfiguration = WalletNetworkConfiguration(),
    val portfolio: WalletPortfolio? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val page: WalletPage = WalletPage.HOME,
    val selectedNetwork: WalletNetwork? = null,
    val contentTab: WalletContentTab = WalletContentTab.ASSETS,
    val hideAssetsBelowOneUsd: Boolean = true,
    val includeUnverifiedAssets: Boolean = false,
    val receiveNetwork: WalletNetwork? = null,
    val send: WalletSendState = WalletSendState(),
    val revealedSecret: String? = null,
    val pendingMnemonic: String? = null,
    val message: String? = null,
    val errorMessage: String? = null
) {
    val activeWallet: WalletProfile? get() = vault.activeWallet
    val visibleAssets: List<SelfCustodyAsset> get() = portfolio?.assets.orEmpty().filter {
        (selectedNetwork == null || it.network.id == selectedNetwork.id) && shouldShowAsset(it)
    }
    val visiblePortfolioAssets: List<SelfCustodyAsset> get() = portfolio?.assets.orEmpty().filter(::shouldShowAsset)

    private fun shouldShowAsset(asset: SelfCustodyAsset): Boolean =
        (!hideAssetsBelowOneUsd || (asset.valueUsd != null && asset.valueUsd >= java.math.BigDecimal.ONE)) &&
            (includeUnverifiedAssets || asset.verified)
}

class WalletViewModel(
    private val vaultRepository: WalletVaultRepository,
    private val networkSettingsRepository: WalletNetworkSettingsRepository,
    private val walletRepository: SelfCustodyWalletRepository,
    private val displayPreferencesRepository: WalletWatchPreferencesRepository
) : ViewModel() {
    private val initialVault = vaultRepository.currentState()
    private val initialDisplayKey = initialVault.activeWallet?.let(::displayPreferencesKey)
    private val _uiState = MutableStateFlow(
        WalletUiState(
            vault = initialVault,
            networkConfiguration = networkSettingsRepository.get(),
            hideAssetsBelowOneUsd = initialDisplayKey?.let(displayPreferencesRepository::getHideSmallAssets) ?: true,
            includeUnverifiedAssets = initialDisplayKey?.let(displayPreferencesRepository::getIncludeRiskAssets) ?: false
        )
    )
    val uiState: StateFlow<WalletUiState> = _uiState.asStateFlow()
    private var lastLoadedWalletId: String? = null
    private val broadcastInFlight = AtomicBoolean(false)

    init {
        viewModelScope.launch {
            vaultRepository.observeState().collect { vault ->
                _uiState.update { state ->
                    val walletChanged = state.activeWallet?.id != vault.activeWallet?.id
                    val displayKey = vault.activeWallet?.let(::displayPreferencesKey)
                    state.copy(
                        vault = vault,
                        portfolio = if (state.activeWallet?.id == vault.activeWallet?.id) state.portfolio else null,
                        hideAssetsBelowOneUsd = if (walletChanged && displayKey != null) {
                            displayPreferencesRepository.getHideSmallAssets(displayKey)
                        } else state.hideAssetsBelowOneUsd,
                        includeUnverifiedAssets = if (walletChanged && displayKey != null) {
                            displayPreferencesRepository.getIncludeRiskAssets(displayKey)
                        } else state.includeUnverifiedAssets,
                        page = if (!vault.unlocked) WalletPage.HOME else state.page,
                        revealedSecret = if (!vault.unlocked) null else state.revealedSecret
                    )
                }
                val active = vault.activeWallet
                if (vault.unlocked && active != null && lastLoadedWalletId != active.id) {
                    lastLoadedWalletId = active.id
                    loadPortfolio(initial = true)
                }
            }
        }
        viewModelScope.launch {
            networkSettingsRepository.observe().collect { config ->
                _uiState.update { state ->
                    state.copy(
                        networkConfiguration = config,
                        selectedNetwork = state.selectedNetwork?.let { selected ->
                            config.enabledNetworks.firstOrNull { it.id == selected.id }
                        },
                        receiveNetwork = state.receiveNetwork?.let { selected ->
                            config.enabledNetworks.firstOrNull { it.id == selected.id }
                        }
                    )
                }
            }
        }
    }

    fun createFirstWallet(password: String, confirmation: String, name: String?) {
        if (password != confirmation) return fail("两次输入的钱包密码不一致。")
        launchAction {
            vaultRepository.createVault(password.toCharArray())
            val (profile, mnemonic) = vaultRepository.createMnemonicWallet(name)
            lastLoadedWalletId = null
            _uiState.update { it.copy(pendingMnemonic = mnemonic, page = WalletPage.BACKUP, message = "${profile.name} 已创建") }
        }
    }

    fun importFirstWallet(
        password: String,
        confirmation: String,
        mode: WalletImportMode,
        secret: String,
        privateKeyType: WalletPrivateKeyType,
        name: String?
    ) {
        if (password != confirmation) return fail("两次输入的钱包密码不一致。")
        launchAction {
            vaultRepository.createVault(password.toCharArray())
            if (mode == WalletImportMode.MNEMONIC) {
                vaultRepository.importMnemonicWallet(secret, name)
            } else {
                vaultRepository.importPrivateKeyWallet(secret, privateKeyType, name)
            }
            lastLoadedWalletId = null
            _uiState.update { it.copy(page = WalletPage.HOME, message = "钱包已导入") }
        }
    }

    fun unlock(password: String) {
        viewModelScope.launch {
            val success = runCatching { vaultRepository.unlock(password.toCharArray()) }.getOrDefault(false)
            if (!success) fail("钱包密码不正确。")
        }
    }

    fun unlockWithDerivedKey(key: ByteArray) {
        viewModelScope.launch {
            val success = try {
                vaultRepository.unlockWithDerivedKey(key)
            } finally {
                key.fill(0)
            }
            if (!success) fail("生物识别凭证已失效，请使用钱包密码解锁。")
        }
    }

    fun verifyPassword(password: String, onVerified: (ByteArray) -> Unit) {
        viewModelScope.launch {
            val success = runCatching { vaultRepository.unlock(password.toCharArray()) }.getOrDefault(false)
            val key = if (success) vaultRepository.currentDerivedKeyCopy() else null
            if (key == null) fail("钱包密码不正确。") else onVerified(key)
        }
    }

    fun lock() = vaultRepository.lock()

    fun createAdditionalWallet(name: String?) = launchAction {
        val (_, mnemonic) = vaultRepository.createMnemonicWallet(name)
        lastLoadedWalletId = null
        _uiState.update { it.copy(pendingMnemonic = mnemonic, page = WalletPage.BACKUP) }
    }

    fun importAdditionalWallet(mode: WalletImportMode, secret: String, privateKeyType: WalletPrivateKeyType, name: String?) = launchAction {
        if (mode == WalletImportMode.MNEMONIC) vaultRepository.importMnemonicWallet(secret, name)
        else vaultRepository.importPrivateKeyWallet(secret, privateKeyType, name)
        lastLoadedWalletId = null
        _uiState.update { it.copy(page = WalletPage.HOME, message = "钱包已导入") }
    }

    fun selectWallet(id: String) = launchAction {
        vaultRepository.selectWallet(id)
        lastLoadedWalletId = null
        _uiState.update { it.copy(page = WalletPage.HOME, selectedNetwork = null, portfolio = null) }
    }

    fun renameWallet(id: String, name: String) = launchAction { vaultRepository.renameWallet(id, name) }

    fun deleteWallet(id: String, password: String) = launchAction {
        require(vaultRepository.unlock(password.toCharArray())) { "钱包密码不正确。" }
        walletRepository.clearLocalData(listOf(id))
        vaultRepository.deleteWallet(id)
        lastLoadedWalletId = null
        _uiState.update { it.copy(page = WalletPage.MANAGE, portfolio = null, message = "钱包已从本机删除") }
    }

    fun resetVault(password: String, onReset: () -> Unit = {}) = launchAction {
        require(vaultRepository.unlock(password.toCharArray())) { "钱包密码不正确。" }
        val walletIds = vaultRepository.currentState().wallets.map(WalletProfile::id)
        walletRepository.clearLocalData(walletIds)
        vaultRepository.resetVault()
        onReset()
        lastLoadedWalletId = null
        _uiState.value = WalletUiState(
            vault = vaultRepository.currentState(),
            networkConfiguration = networkSettingsRepository.get(),
            message = "本地钱包保险库已重置"
        )
    }

    fun refresh() = loadPortfolio(initial = false)

    private fun loadPortfolio(initial: Boolean) {
        val wallet = _uiState.value.activeWallet ?: return
        viewModelScope.launch {
            _uiState.update { if (initial) it.copy(loading = true, errorMessage = null) else it.copy(refreshing = true, errorMessage = null) }
            runCatching { walletRepository.loadPortfolio(wallet) }
                .onSuccess { portfolio ->
                    lastLoadedWalletId = wallet.id
                    _uiState.update { it.copy(portfolio = portfolio, loading = false, refreshing = false) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(loading = false, refreshing = false, errorMessage = error.message ?: "钱包资产读取失败") }
                }
        }
    }

    fun setPage(page: WalletPage) {
        _uiState.update { it.copy(page = page, errorMessage = null, message = null, revealedSecret = null) }
    }

    fun goHome() {
        _uiState.update {
            it.copy(page = WalletPage.HOME, send = WalletSendState(), receiveNetwork = null, revealedSecret = null)
        }
    }

    fun selectNetwork(network: WalletNetwork?) = _uiState.update { it.copy(selectedNetwork = network) }
    fun selectContentTab(tab: WalletContentTab) = _uiState.update { it.copy(contentTab = tab) }

    fun setHideAssetsBelowOneUsd(hide: Boolean) {
        _uiState.update { it.copy(hideAssetsBelowOneUsd = hide) }
        _uiState.value.activeWallet?.let { wallet ->
            viewModelScope.launch {
                displayPreferencesRepository.saveHideSmallAssets(displayPreferencesKey(wallet), hide)
            }
        }
    }

    fun setIncludeUnverifiedAssets(include: Boolean) {
        _uiState.update { it.copy(includeUnverifiedAssets = include) }
        _uiState.value.activeWallet?.let { wallet ->
            viewModelScope.launch {
                displayPreferencesRepository.saveIncludeRiskAssets(displayPreferencesKey(wallet), include)
            }
        }
    }

    fun removeNetwork(network: WalletNetwork) = launchAction {
        networkSettingsRepository.setNetworkEnabled(network.id, false)
        _uiState.update { state ->
            state.copy(
                selectedNetwork = state.selectedNetwork?.takeUnless { it.id == network.id },
                receiveNetwork = state.receiveNetwork?.takeUnless { it.id == network.id },
                portfolio = state.portfolio?.let { portfolio ->
                    portfolio.copy(
                        assets = portfolio.assets.filterNot { it.network.id == network.id },
                        activities = portfolio.activities.filterNot { it.network.id == network.id },
                        refreshFailures = portfolio.refreshFailures.copy(
                            assetIndex = portfolio.refreshFailures.assetIndex.filterNotTo(linkedSetOf()) { it.id == network.id },
                            nativeBalance = portfolio.refreshFailures.nativeBalance.filterNotTo(linkedSetOf()) { it.id == network.id },
                            activityIndex = portfolio.refreshFailures.activityIndex.filterNotTo(linkedSetOf()) { it.id == network.id }
                        )
                    )
                }
            )
        }
        loadPortfolio(initial = false)
    }

    fun openReceive(network: WalletNetwork) {
        require(_uiState.value.activeWallet?.supports(network) == true)
        _uiState.update { it.copy(page = WalletPage.RECEIVE, receiveNetwork = network) }
    }

    fun openSend(asset: SelfCustodyAsset? = null) {
        val selected = asset ?: _uiState.value.visibleAssets.firstOrNull()
        _uiState.update { it.copy(page = WalletPage.SEND, send = WalletSendState(asset = selected)) }
    }

    fun selectSendAsset(asset: SelfCustodyAsset) = _uiState.update { it.copy(send = WalletSendState(asset = asset)) }
    fun updateRecipient(value: String) = updateSend { it.copy(recipient = value, estimate = null, errorMessage = null) }
    fun updateAmount(value: String) = updateSend { it.copy(amount = value, sendMaximum = false, estimate = null, errorMessage = null) }
    fun setMaximum() = updateSend { state ->
        val asset = state.asset
        state.copy(amount = asset?.balance?.toPlainString().orEmpty(), sendMaximum = true, estimate = null, errorMessage = null)
    }

    fun estimateTransfer() {
        val request = buildTransferRequest() ?: return
        viewModelScope.launch {
            updateSend { it.copy(estimating = true, errorMessage = null) }
            runCatching { walletRepository.estimateTransfer(request) }
                .onSuccess { estimate -> updateSend { it.copy(estimating = false, estimate = estimate, confirming = true) } }
                .onFailure { error -> updateSend { it.copy(estimating = false, errorMessage = error.message ?: "手续费估算失败") } }
        }
    }

    fun dismissConfirmation() = updateSend { it.copy(confirming = false) }

    fun authorizeAndSend(password: String) {
        val request = buildTransferRequest() ?: return
        val estimate = _uiState.value.send.estimate ?: return fail("请重新估算网络费。")
        if (estimate.request != request) return fail("转账内容已变化，请重新估算网络费。")
        if (!broadcastInFlight.compareAndSet(false, true)) return
        viewModelScope.launch {
            updateSend { it.copy(broadcasting = true, errorMessage = null) }
            try {
                runCatching {
                    require(vaultRepository.unlock(password.toCharArray())) { "钱包密码不正确。" }
                    val key = vaultRepository.privateKeyFor(request.wallet.id, request.network)
                    try { walletRepository.signAndBroadcast(estimate, key) } finally { key.fill(0) }
                }.onSuccess { activity ->
                    updateSend { it.copy(broadcasting = false, confirming = false, submittedHash = activity.transactionHash) }
                    loadPortfolio(initial = false)
                }.onFailure { error ->
                    updateSend { it.copy(broadcasting = false, errorMessage = error.message ?: "交易发送失败") }
                }
            } finally {
                broadcastInFlight.set(false)
            }
        }
    }

    fun authorizeAndSendWithDerivedKey(derivedKey: ByteArray) {
        val request = buildTransferRequest() ?: return derivedKey.fill(0)
        val estimate = _uiState.value.send.estimate ?: return derivedKey.fill(0).also { fail("请重新估算网络费。") }
        if (estimate.request != request) return derivedKey.fill(0).also { fail("转账内容已变化，请重新估算网络费。") }
        if (!broadcastInFlight.compareAndSet(false, true)) return derivedKey.fill(0)
        viewModelScope.launch {
            updateSend { it.copy(broadcasting = true, errorMessage = null) }
            try {
                runCatching {
                    try {
                        require(vaultRepository.unlockWithDerivedKey(derivedKey)) { "生物识别凭证已失效。" }
                    } finally {
                        derivedKey.fill(0)
                    }
                    val key = vaultRepository.privateKeyFor(request.wallet.id, request.network)
                    try { walletRepository.signAndBroadcast(estimate, key) } finally { key.fill(0) }
                }.onSuccess { activity ->
                    updateSend { it.copy(broadcasting = false, confirming = false, submittedHash = activity.transactionHash) }
                    loadPortfolio(initial = false)
                }.onFailure { error -> updateSend { it.copy(broadcasting = false, errorMessage = error.message ?: "交易发送失败") } }
            } finally {
                broadcastInFlight.set(false)
            }
        }
    }

    fun revealSecret(password: String) {
        val wallet = _uiState.value.activeWallet ?: return
        viewModelScope.launch {
            runCatching {
                require(vaultRepository.unlock(password.toCharArray())) { "钱包密码不正确。" }
                vaultRepository.revealSecret(wallet.id)
            }.onSuccess { secret ->
                _uiState.update { it.copy(revealedSecret = secret, errorMessage = null) }
            }.onFailure { error -> fail(error.message ?: "验证失败") }
        }
    }

    fun verifyBackup(words: Map<Int, String>): Boolean {
        val mnemonic = _uiState.value.pendingMnemonic ?: _uiState.value.revealedSecret ?: return false
        val list = mnemonic.split(' ')
        val success = words.all { (index, word) -> list.getOrNull(index)?.equals(word.trim(), true) == true }
        if (success) {
            val walletId = _uiState.value.activeWallet?.id ?: return false
            launchAction {
                vaultRepository.markBackedUp(walletId)
                _uiState.update { it.copy(pendingMnemonic = null, revealedSecret = null, page = WalletPage.HOME, message = "助记词备份已确认") }
            }
        } else fail("助记词校验不正确。")
        return success
    }

    fun skipBackup() {
        _uiState.update { it.copy(pendingMnemonic = null, revealedSecret = null, page = WalletPage.HOME) }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null, errorMessage = null) }

    private fun buildTransferRequest(): WalletTransferRequest? {
        val state = _uiState.value
        val wallet = state.activeWallet ?: return null
        val asset = state.send.asset ?: return failAndNull("请选择资产。")
        if (state.send.recipient.isBlank()) return failAndNull("请输入收款地址。")
        if (state.send.amount.isBlank()) return failAndNull("请输入转账金额。")
        if (!state.send.sendMaximum && (state.send.amount.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO) > asset.balance) {
            return failAndNull("转账金额超过可用余额。")
        }
        return WalletTransferRequest(
            wallet = wallet,
            network = asset.network,
            assetId = asset.id,
            tokenAddress = asset.tokenAddress,
            tokenDecimals = asset.decimals,
            symbol = asset.symbol,
            recipient = state.send.recipient.trim(),
            amount = state.send.amount.trim(),
            sendMaximum = state.send.sendMaximum
        )
    }

    private fun updateSend(block: (WalletSendState) -> WalletSendState) = _uiState.update { it.copy(send = block(it.send)) }

    private fun launchAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }
                .onFailure { error -> fail(error.message ?: "钱包操作失败") }
        }
    }

    private fun fail(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    private fun <T> failAndNull(message: String): T? {
        fail(message)
        return null
    }

    private fun displayPreferencesKey(wallet: WalletProfile): String = "self-custody:${wallet.id}"

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                WalletViewModel(
                    vaultRepository = container.walletVaultRepository,
                    networkSettingsRepository = container.walletNetworkSettingsRepository,
                    walletRepository = container.selfCustodyWalletRepository,
                    displayPreferencesRepository = container.walletWatchPreferencesRepository
                )
            }
        }
    }
}
