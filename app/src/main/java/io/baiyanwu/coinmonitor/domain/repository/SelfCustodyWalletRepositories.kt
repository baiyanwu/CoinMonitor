package io.baiyanwu.coinmonitor.domain.repository

import io.baiyanwu.coinmonitor.domain.model.WalletActivity
import io.baiyanwu.coinmonitor.domain.model.WalletNetwork
import io.baiyanwu.coinmonitor.domain.model.WalletNetworkConfiguration
import io.baiyanwu.coinmonitor.domain.model.WalletPortfolio
import io.baiyanwu.coinmonitor.domain.model.WalletPrivateKeyType
import io.baiyanwu.coinmonitor.domain.model.WalletProfile
import java.math.BigDecimal
import kotlinx.coroutines.flow.Flow

data class WalletVaultState(
    val initialized: Boolean = false,
    val secureStorageAvailable: Boolean = true,
    val unlocked: Boolean = false,
    val wallets: List<WalletProfile> = emptyList(),
    val activeWalletId: String? = null
) {
    val activeWallet: WalletProfile? get() = wallets.firstOrNull { it.id == activeWalletId }
}

interface WalletVaultRepository {
    fun observeState(): Flow<WalletVaultState>
    fun currentState(): WalletVaultState
    suspend fun createVault(password: CharArray)
    suspend fun unlock(password: CharArray): Boolean
    suspend fun unlockWithDerivedKey(derivedKey: ByteArray): Boolean
    fun lock()
    fun currentDerivedKeyCopy(): ByteArray?
    suspend fun createMnemonicWallet(name: String? = null): Pair<WalletProfile, String>
    suspend fun importMnemonicWallet(mnemonic: String, name: String? = null): WalletProfile
    suspend fun importPrivateKeyWallet(privateKey: String, type: WalletPrivateKeyType, name: String? = null): WalletProfile
    suspend fun renameWallet(walletId: String, name: String)
    suspend fun deleteWallet(walletId: String)
    suspend fun selectWallet(walletId: String)
    suspend fun markBackedUp(walletId: String)
    suspend fun revealSecret(walletId: String): String
    suspend fun privateKeyFor(walletId: String, network: WalletNetwork): ByteArray
    suspend fun resetVault()
}

interface WalletNetworkSettingsRepository {
    fun observe(): Flow<WalletNetworkConfiguration>
    fun get(): WalletNetworkConfiguration
    fun isSecureStorageAvailable(): Boolean
    suspend fun save(configuration: WalletNetworkConfiguration)
    suspend fun saveAlchemyApiKey(apiKey: String)
    suspend fun saveCustomRpc(network: WalletNetwork, url: String)
    suspend fun clearCustomRpc(network: WalletNetwork)
    suspend fun validateRpc(network: WalletNetwork, url: String): Result<Unit>
    suspend fun refreshNetworkCatalog(): Result<List<WalletNetwork>>
    suspend fun setNetworkEnabled(networkId: String, enabled: Boolean)
    suspend fun addCustomEvmNetwork(
        name: String,
        chainId: Long,
        symbol: String,
        explorerUrl: String,
        rpcUrl: String
    ): WalletNetwork
    suspend fun removeCustomEvmNetwork(networkId: String)
}

interface SelfCustodyWalletRepository {
    suspend fun loadPortfolio(wallet: WalletProfile): WalletPortfolio
    suspend fun estimateTransfer(request: WalletTransferRequest): WalletTransferEstimate
    suspend fun signAndBroadcast(estimate: WalletTransferEstimate, privateKey: ByteArray): WalletActivity
    suspend fun refreshActivityStatus(activity: WalletActivity): WalletActivity
    suspend fun clearLocalData(walletIds: Collection<String>)
}

data class WalletTransferRequest(
    val wallet: WalletProfile,
    val network: WalletNetwork,
    val assetId: String,
    val tokenAddress: String?,
    val tokenDecimals: Int,
    val symbol: String,
    val recipient: String,
    val amount: String,
    val sendMaximum: Boolean = false
)

data class WalletTransferEstimate(
    val request: WalletTransferRequest,
    val amountAtomic: String,
    val feeAtomic: String,
    val fee: BigDecimal,
    val nonceOrBlockhash: String,
    val gasLimit: String? = null,
    val gasPrice: String? = null,
    val senderTokenAddress: String? = null,
    val recipientTokenAddress: String? = null,
    val recipientTokenAccountExists: Boolean = true
)
