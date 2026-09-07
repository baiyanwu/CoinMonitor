package io.baiyanwu.coinmonitor.domain.repository

import io.baiyanwu.coinmonitor.domain.model.OkxWalletCredentials
import io.baiyanwu.coinmonitor.domain.model.WalletPortfolioSnapshot
import io.baiyanwu.coinmonitor.domain.model.WalletTotalResult
import kotlinx.coroutines.flow.Flow

interface OkxWalletCredentialsRepository {
    fun observeCredentials(): Flow<OkxWalletCredentials>
    fun getCredentials(): OkxWalletCredentials
    fun isSecureStorageAvailable(): Boolean
    suspend fun save(credentials: OkxWalletCredentials)
    suspend fun clear()
}

interface WalletWatchPreferencesRepository {
    fun getLastAddress(): String?
    suspend fun saveLastAddress(address: String)
    fun getHiddenAssetIds(address: String): Set<String>
    suspend fun saveHiddenAssetIds(address: String, assetIds: Set<String>)
    fun getHiddenChainIndexes(address: String): Set<String>
    suspend fun saveHiddenChainIndexes(address: String, chainIndexes: Set<String>)
    fun getHideSmallAssets(address: String): Boolean
    suspend fun saveHideSmallAssets(address: String, hide: Boolean)
    fun getIncludeRiskAssets(address: String): Boolean
    suspend fun saveIncludeRiskAssets(address: String, include: Boolean)
}

interface WalletPortfolioRepository {
    suspend fun load(address: String, includeRiskInTotal: Boolean): WalletPortfolioSnapshot
    suspend fun loadTotal(address: String, includeRiskInTotal: Boolean): WalletTotalResult
}

data class CachedWalletPortfolio(
    val snapshot: WalletPortfolioSnapshot,
    val includeRiskInTotal: Boolean
)

interface WalletPortfolioCacheRepository {
    suspend fun load(address: String): CachedWalletPortfolio?
    suspend fun save(snapshot: WalletPortfolioSnapshot, includeRiskInTotal: Boolean)
}
