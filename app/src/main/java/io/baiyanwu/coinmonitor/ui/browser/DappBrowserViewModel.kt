package io.baiyanwu.coinmonitor.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.util.Log
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.data.repository.DappBrowserRepository
import io.baiyanwu.coinmonitor.data.repository.DappEvmTransaction
import io.baiyanwu.coinmonitor.data.repository.DappSignatureRequest
import io.baiyanwu.coinmonitor.data.repository.DappSigningRepository
import io.baiyanwu.coinmonitor.data.repository.DappWalletControlRepository
import io.baiyanwu.coinmonitor.data.repository.DappWalletControlRequest
import io.baiyanwu.coinmonitor.domain.model.WalletNetwork
import io.baiyanwu.coinmonitor.domain.model.WalletNetworkConfiguration
import io.baiyanwu.coinmonitor.domain.repository.WalletNetworkSettingsRepository
import io.baiyanwu.coinmonitor.domain.repository.WalletVaultRepository
import io.baiyanwu.coinmonitor.domain.repository.WalletVaultState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.concurrent.atomic.AtomicBoolean

data class DappConnectionApproval(
    val request: DappBridgeRequest,
    val walletName: String,
    val address: String,
    val network: WalletNetwork
)

data class DappTransactionApproval(
    val request: DappBridgeRequest,
    val walletId: String,
    val walletName: String,
    val transaction: DappEvmTransaction,
    val broadcasting: Boolean = false,
    val errorMessage: String? = null
)

data class DappSignatureApproval(
    val request: DappBridgeRequest,
    val walletId: String,
    val walletName: String,
    val signatureRequest: DappSignatureRequest,
    val signing: Boolean = false,
    val errorMessage: String? = null
)

data class DappWalletControlApproval(
    val request: DappBridgeRequest,
    val controlRequest: DappWalletControlRequest,
    val applying: Boolean = false,
    val errorMessage: String? = null
)

data class DappBrowserUiState(
    val vault: WalletVaultState = WalletVaultState(),
    val networks: WalletNetworkConfiguration = WalletNetworkConfiguration(),
    val selectedNetwork: WalletNetwork = WalletNetwork.ETHEREUM,
    val pendingConnection: DappConnectionApproval? = null,
    val pendingTransaction: DappTransactionApproval? = null,
    val pendingSignature: DappSignatureApproval? = null,
    val pendingWalletControl: DappWalletControlApproval? = null
)

sealed interface DappBridgeCommand {
    data class Result(val id: Long, val origin: String, val value: JsonElement) : DappBridgeCommand
    data class Error(val id: Long, val origin: String, val code: Int, val message: String) : DappBridgeCommand
    data class ChainChanged(val chainIdHex: String) : DappBridgeCommand
}

class DappBrowserViewModel(
    private val vaultRepository: WalletVaultRepository,
    private val networkSettingsRepository: WalletNetworkSettingsRepository,
    private val browserRepository: DappBrowserRepository,
    private val signingRepository: DappSigningRepository,
    private val walletControlRepository: DappWalletControlRepository
) : ViewModel() {
    private val initialNetworks = networkSettingsRepository.get()
    private val initialNetwork = initialNetworks.enabledNetworks.firstOrNull {
        it.id == WalletNetwork.ETHEREUM.id && it.isEvm
    } ?: initialNetworks.enabledNetworks.firstOrNull(WalletNetwork::isEvm)
        ?: WalletNetwork.ETHEREUM
    private val selectedNetworkId = MutableStateFlow(initialNetwork.id)
    private val pendingConnection = MutableStateFlow<DappConnectionApproval?>(null)
    private val pendingTransaction = MutableStateFlow<DappTransactionApproval?>(null)
    private val pendingSignature = MutableStateFlow<DappSignatureApproval?>(null)
    private val pendingWalletControl = MutableStateFlow<DappWalletControlApproval?>(null)
    private val sensitiveRequestInFlight = AtomicBoolean(false)
    val commands = MutableSharedFlow<DappBridgeCommand>(extraBufferCapacity = 16)

    init {
        val vault = vaultRepository.currentState()
        Log.i(
            LOG_TAG,
            "Browser wallet state unlocked=${vault.unlocked} " +
                "walletAvailable=${vault.activeWallet != null}"
        )
    }

    private val sensitiveApprovals = combine(
        pendingTransaction,
        pendingSignature,
        pendingWalletControl
    ) { transaction, signature, walletControl ->
        Triple(transaction, signature, walletControl)
    }

    val uiState: StateFlow<DappBrowserUiState> = combine(
        vaultRepository.observeState(),
        networkSettingsRepository.observe(),
        selectedNetworkId,
        pendingConnection,
        sensitiveApprovals
    ) { vault, networks, selectedId, connection, approvals ->
        val evmNetworks = networks.enabledNetworks.filter(WalletNetwork::isEvm)
        val selected = evmNetworks.firstOrNull { it.id == selectedId }
            ?: evmNetworks.firstOrNull { it.id == WalletNetwork.ETHEREUM.id }
            ?: evmNetworks.firstOrNull()
            ?: WalletNetwork.ETHEREUM
        DappBrowserUiState(
            vault = vault,
            networks = networks,
            selectedNetwork = selected,
            pendingConnection = connection,
            pendingTransaction = approvals.first,
            pendingSignature = approvals.second,
            pendingWalletControl = approvals.third
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        DappBrowserUiState(
            vault = vaultRepository.currentState(),
            networks = initialNetworks,
            selectedNetwork = initialNetwork
        )
    )

    fun handleBridgeMessage(raw: String, origin: String) {
        val request = runCatching { DappProviderProtocol.parse(raw, origin) }.getOrElse {
            Log.w(LOG_TAG, "Ignored malformed provider request")
            return
        }
        Log.i(LOG_TAG, "Provider request method=${request.method} origin=${request.origin}")
        when (val action = DappProviderProtocol.route(request)) {
            DappProviderAction.RequestAccounts -> requestConnection(request)
            DappProviderAction.ForwardRpc -> forwardRpc(request)
            DappProviderAction.SwitchChain -> switchChain(request)
            DappProviderAction.AddChain -> requestAddChain(request)
            DappProviderAction.WatchAsset -> requestWatchAsset(request)
            DappProviderAction.SendTransaction -> requestTransaction(request)
            is DappProviderAction.Sign -> requestSignature(request, action.method)
            is DappProviderAction.Unsupported ->
                emitError(request, 4200, "Unsupported wallet method: ${action.method}")
        }
    }

    fun approveConnection() {
        val approval = pendingConnection.value ?: return
        Log.i(LOG_TAG, "Approving wallet connection method=${approval.request.method}")
        pendingConnection.value = null
        val result = if (approval.request.method == "wallet_requestPermissions") {
            buildJsonArray {
                add(
                    kotlinx.serialization.json.buildJsonObject {
                        put("parentCapability", "eth_accounts")
                        put(
                            "caveats",
                            buildJsonArray {
                                add(
                                    kotlinx.serialization.json.buildJsonObject {
                                        put("type", "restrictReturnedAccounts")
                                        put("value", buildJsonArray { add(JsonPrimitive(approval.address)) })
                                    }
                                )
                            }
                        )
                    }
                )
            }
        } else {
            buildJsonArray { add(JsonPrimitive(approval.address)) }
        }
        commands.tryEmit(
            DappBridgeCommand.Result(
                approval.request.id,
                approval.request.origin,
                result
            )
        )
    }

    fun rejectConnection(message: String) {
        val approval = pendingConnection.value ?: return
        pendingConnection.value = null
        commands.tryEmit(
            DappBridgeCommand.Error(approval.request.id, approval.request.origin, 4001, message)
        )
    }

    fun approveTransaction(password: String) {
        startTransactionApproval("钱包密码不正确。") {
            vaultRepository.unlock(password.toCharArray())
        }
    }

    fun approveTransactionWithDerivedKey(derivedKey: ByteArray) {
        val accepted = startTransactionApproval("生物识别凭证已失效。") {
            unlockWithDerivedKeyAndErase(derivedKey)
        }
        if (!accepted) derivedKey.fill(0)
    }

    private fun startTransactionApproval(
        authenticationFailureMessage: String,
        unlock: suspend () -> Boolean
    ): Boolean {
        val approval = pendingTransaction.value ?: return false
        if (approval.broadcasting) return false
        pendingTransaction.value = approval.copy(broadcasting = true, errorMessage = null)
        viewModelScope.launch {
            runCatching {
                require(unlock()) { authenticationFailureMessage }
                val currentWallet = vaultRepository.currentState().activeWallet
                require(currentWallet?.id == approval.walletId) { "活动钱包已改变，请重新发起交易。" }
                val key = vaultRepository.privateKeyFor(approval.walletId, approval.transaction.network)
                try {
                    browserRepository.signAndBroadcast(approval.transaction, key)
                } finally {
                    key.fill(0)
                }
            }.onSuccess { hash ->
                pendingTransaction.value = null
                sensitiveRequestInFlight.set(false)
                commands.emit(
                    DappBridgeCommand.Result(
                        approval.request.id,
                        approval.request.origin,
                        JsonPrimitive(hash)
                    )
                )
            }.onFailure { error ->
                pendingTransaction.value = approval.copy(
                    broadcasting = false,
                    errorMessage = error.message ?: "交易签名或广播失败。"
                )
            }
        }
        return true
    }

    fun rejectTransaction(message: String) {
        val approval = pendingTransaction.value ?: return
        if (approval.broadcasting) return
        pendingTransaction.value = null
        sensitiveRequestInFlight.set(false)
        commands.tryEmit(
            DappBridgeCommand.Error(approval.request.id, approval.request.origin, 4001, message)
        )
    }

    fun approveSignature(password: String) {
        startSignatureApproval("钱包密码不正确。") {
            vaultRepository.unlock(password.toCharArray())
        }
    }

    fun approveSignatureWithDerivedKey(derivedKey: ByteArray) {
        val accepted = startSignatureApproval("生物识别凭证已失效。") {
            unlockWithDerivedKeyAndErase(derivedKey)
        }
        if (!accepted) derivedKey.fill(0)
    }

    private fun startSignatureApproval(
        authenticationFailureMessage: String,
        unlock: suspend () -> Boolean
    ): Boolean {
        val approval = pendingSignature.value ?: return false
        if (approval.signing) return false
        pendingSignature.value = approval.copy(signing = true, errorMessage = null)
        viewModelScope.launch {
            runCatching {
                require(unlock()) { authenticationFailureMessage }
                val currentWallet = vaultRepository.currentState().activeWallet
                require(currentWallet?.id == approval.walletId) { "活动钱包已改变，请重新发起签名。" }
                val key = vaultRepository.privateKeyFor(
                    approval.walletId,
                    approval.signatureRequest.network
                )
                try {
                    signingRepository.sign(approval.signatureRequest, key)
                } finally {
                    key.fill(0)
                }
            }.onSuccess { signature ->
                pendingSignature.value = null
                sensitiveRequestInFlight.set(false)
                commands.emit(
                    DappBridgeCommand.Result(
                        approval.request.id,
                        approval.request.origin,
                        JsonPrimitive(signature)
                    )
                )
            }.onFailure { error ->
                pendingSignature.value = approval.copy(
                    signing = false,
                    errorMessage = error.message ?: "消息签名失败。"
                )
            }
        }
        return true
    }

    fun rejectSignature(message: String) {
        val approval = pendingSignature.value ?: return
        if (approval.signing) return
        pendingSignature.value = null
        sensitiveRequestInFlight.set(false)
        commands.tryEmit(
            DappBridgeCommand.Error(approval.request.id, approval.request.origin, 4001, message)
        )
    }

    fun approveWalletControl() {
        val approval = pendingWalletControl.value ?: return
        if (approval.applying) return
        pendingWalletControl.value = approval.copy(applying = true, errorMessage = null)
        viewModelScope.launch {
            runCatching {
                when (val control = approval.controlRequest) {
                    is DappWalletControlRequest.AddChain -> walletControlRepository.addChain(control)
                    is DappWalletControlRequest.WatchAsset -> {
                        walletControlRepository.watchAsset(control)
                        null
                    }
                }
            }.onSuccess { addedNetwork ->
                pendingWalletControl.value = null
                sensitiveRequestInFlight.set(false)
                when (approval.controlRequest) {
                    is DappWalletControlRequest.AddChain -> {
                        val network = requireNotNull(addedNetwork)
                        selectedNetworkId.value = network.id
                        commands.emit(
                            DappBridgeCommand.Result(
                                approval.request.id,
                                approval.request.origin,
                                JsonNull
                            )
                        )
                        commands.emit(DappBridgeCommand.ChainChanged("0x${network.chainId!!.toString(16)}"))
                    }
                    is DappWalletControlRequest.WatchAsset ->
                        commands.emit(
                            DappBridgeCommand.Result(
                                approval.request.id,
                                approval.request.origin,
                                JsonPrimitive(true)
                            )
                        )
                }
            }.onFailure { error ->
                pendingWalletControl.value = approval.copy(
                    applying = false,
                    errorMessage = error.message ?: "钱包设置更新失败。"
                )
            }
        }
    }

    fun rejectWalletControl(message: String) {
        val approval = pendingWalletControl.value ?: return
        if (approval.applying) return
        pendingWalletControl.value = null
        sensitiveRequestInFlight.set(false)
        commands.tryEmit(
            DappBridgeCommand.Error(approval.request.id, approval.request.origin, 4001, message)
        )
    }

    fun selectNetwork(network: WalletNetwork) {
        val enabled = uiState.value.networks.enabledNetworks.any { it.id == network.id && it.isEvm }
        val chainId = network.chainId
        if (!enabled || chainId == null || selectedNetworkId.value == network.id) return
        selectedNetworkId.value = network.id
        commands.tryEmit(DappBridgeCommand.ChainChanged("0x${chainId.toString(16)}"))
    }

    fun unlockWallet(password: String) {
        viewModelScope.launch {
            val unlocked = runCatching { vaultRepository.unlock(password.toCharArray()) }
                .getOrDefault(false)
            Log.i(LOG_TAG, "Browser password unlock result=$unlocked")
        }
    }

    fun unlockWalletWithDerivedKey(derivedKey: ByteArray) {
        viewModelScope.launch {
            val unlocked = try {
                vaultRepository.unlockWithDerivedKey(derivedKey)
            } finally {
                derivedKey.fill(0)
            }
            Log.i(LOG_TAG, "Browser biometric unlock result=$unlocked")
        }
    }

    private fun requestConnection(request: DappBridgeRequest) {
        val state = uiState.value
        val wallet = state.vault.activeWallet
        val address = wallet?.addressFor(state.selectedNetwork)
        Log.i(
            LOG_TAG,
            "Preparing wallet connection unlocked=${state.vault.unlocked} " +
                "walletAvailable=${wallet != null} addressAvailable=${address != null}"
        )
        when {
            !state.vault.unlocked -> emitError(request, 4100, "Wallet is locked.")
            wallet == null || address == null -> emitError(request, 4100, "The active wallet does not support EVM.")
            else -> pendingConnection.value = DappConnectionApproval(
                request = request,
                walletName = wallet.name,
                address = address,
                network = state.selectedNetwork
            )
        }
    }

    private fun requestTransaction(request: DappBridgeRequest) {
        if (!sensitiveRequestInFlight.compareAndSet(false, true)) {
            emitError(request, -32002, "A wallet confirmation request is already pending.")
            return
        }
        val state = uiState.value
        val wallet = state.vault.activeWallet
        val address = wallet?.addressFor(state.selectedNetwork)
        val params = request.params as? JsonObject
        if (!state.vault.unlocked || wallet == null || address == null || params == null) {
            sensitiveRequestInFlight.set(false)
            emitError(request, 4100, "Wallet is unavailable or the transaction is malformed.")
            return
        }
        viewModelScope.launch {
            runCatching {
                browserRepository.prepareTransaction(state.selectedNetwork, address, params)
            }.onSuccess { transaction ->
                pendingTransaction.value = DappTransactionApproval(
                    request = request,
                    walletId = wallet.id,
                    walletName = wallet.name,
                    transaction = transaction
                )
            }.onFailure { error ->
                sensitiveRequestInFlight.set(false)
                commands.emit(
                    DappBridgeCommand.Error(
                        request.id,
                        request.origin,
                        -32000,
                        error.message ?: "Unable to prepare transaction."
                    )
                )
            }
        }
    }

    private fun requestSignature(request: DappBridgeRequest, method: DappSignatureMethod) {
        if (!sensitiveRequestInFlight.compareAndSet(false, true)) {
            emitError(request, -32002, "A wallet confirmation request is already pending.")
            return
        }
        val state = uiState.value
        val wallet = state.vault.activeWallet
        val address = wallet?.addressFor(state.selectedNetwork)
        val params = request.params as? JsonObject
        if (!state.vault.unlocked || wallet == null || address == null || params == null) {
            sensitiveRequestInFlight.set(false)
            emitError(request, 4100, "Wallet is unavailable or the signature request is malformed.")
            return
        }
        runCatching {
            signingRepository.prepare(method, state.selectedNetwork, address, params)
        }.onSuccess { signatureRequest ->
            pendingSignature.value = DappSignatureApproval(
                request = request,
                walletId = wallet.id,
                walletName = wallet.name,
                signatureRequest = signatureRequest
            )
        }.onFailure { error ->
            sensitiveRequestInFlight.set(false)
            emitError(request, -32602, error.message ?: "Unable to prepare signature request.")
        }
    }

    private fun requestAddChain(request: DappBridgeRequest) {
        requestWalletControl(request) { params -> walletControlRepository.prepareAddChain(params) }
    }

    private fun requestWatchAsset(request: DappBridgeRequest) {
        val state = uiState.value
        val wallet = state.vault.activeWallet
        if (!state.vault.unlocked || wallet == null) {
            emitError(request, 4100, "Wallet is unavailable.")
            return
        }
        requestWalletControl(request) { params ->
            walletControlRepository.prepareWatchAsset(wallet.id, state.selectedNetwork, params)
        }
    }

    private fun requestWalletControl(
        request: DappBridgeRequest,
        prepare: (JsonObject) -> DappWalletControlRequest
    ) {
        if (!sensitiveRequestInFlight.compareAndSet(false, true)) {
            emitError(request, -32002, "A wallet confirmation request is already pending.")
            return
        }
        val params = request.params as? JsonObject
        if (params == null) {
            sensitiveRequestInFlight.set(false)
            emitError(request, -32602, "Wallet control request is malformed.")
            return
        }
        runCatching { prepare(params) }
            .onSuccess { control ->
                pendingWalletControl.value = DappWalletControlApproval(request, control)
            }
            .onFailure { error ->
                sensitiveRequestInFlight.set(false)
                emitError(request, -32602, error.message ?: "Wallet control request is invalid.")
            }
    }

    private fun forwardRpc(request: DappBridgeRequest) {
        val payload = request.params as? JsonObject
        val method = payload?.get("method")?.jsonPrimitive?.content
        if (method.isNullOrBlank()) {
            emitError(request, -32602, "Invalid RPC request.")
            return
        }
        if (method !in READ_ONLY_RPC_METHODS) {
            emitError(request, 4200, "The requested RPC method is not available in read-only browser mode.")
            return
        }
        val params = payload["params"]?.let { it as? JsonArray } ?: JsonArray(emptyList())
        val network = uiState.value.selectedNetwork
        viewModelScope.launch {
            runCatching { browserRepository.rpcCall(network, method, params) }
                .onSuccess { commands.emit(DappBridgeCommand.Result(request.id, request.origin, it)) }
                .onFailure {
                    commands.emit(
                        DappBridgeCommand.Error(
                            request.id,
                            request.origin,
                            -32000,
                            it.message ?: "RPC request failed."
                        )
                    )
                }
        }
    }

    private fun switchChain(request: DappBridgeRequest) {
        val chainId = (request.params as? JsonObject)?.get("chainId")?.jsonPrimitive?.content
            ?.removePrefix("0x")?.toLongOrNull(16)
        val network = uiState.value.networks.enabledNetworks.firstOrNull {
            it.isEvm && it.chainId == chainId
        }
        if (network == null) {
            emitError(request, 4902, "The requested network is not enabled in CoinMonitor.")
            return
        }
        selectedNetworkId.value = network.id
        commands.tryEmit(DappBridgeCommand.Result(request.id, request.origin, JsonNull))
        commands.tryEmit(DappBridgeCommand.ChainChanged("0x${network.chainId!!.toString(16)}"))
    }

    private fun emitError(request: DappBridgeRequest, code: Int, message: String) {
        commands.tryEmit(DappBridgeCommand.Error(request.id, request.origin, code, message))
    }

    private suspend fun unlockWithDerivedKeyAndErase(derivedKey: ByteArray): Boolean = try {
        vaultRepository.unlockWithDerivedKey(derivedKey)
    } finally {
        derivedKey.fill(0)
    }

    companion object {
        private const val LOG_TAG = "DappBrowser"
        private val READ_ONLY_RPC_METHODS = setOf(
            "eth_blockNumber",
            "eth_call",
            "eth_chainId",
            "eth_estimateGas",
            "eth_feeHistory",
            "eth_gasPrice",
            "eth_getBalance",
            "eth_getBlockByHash",
            "eth_getBlockByNumber",
            "eth_getBlockTransactionCountByHash",
            "eth_getBlockTransactionCountByNumber",
            "eth_getCode",
            "eth_getLogs",
            "eth_getStorageAt",
            "eth_getTransactionByBlockHashAndIndex",
            "eth_getTransactionByBlockNumberAndIndex",
            "eth_getTransactionByHash",
            "eth_getTransactionCount",
            "eth_getTransactionReceipt",
            "eth_maxPriorityFeePerGas",
            "eth_syncing",
            "net_version",
            "web3_clientVersion"
        )

        fun factory(container: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = DappBrowserViewModel(
                vaultRepository = container.walletVaultRepository,
                networkSettingsRepository = container.walletNetworkSettingsRepository,
                browserRepository = container.dappBrowserRepository,
                signingRepository = container.dappSigningRepository,
                walletControlRepository = container.dappWalletControlRepository
            ) as T
        }
    }
}
