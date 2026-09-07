package io.baiyanwu.coinmonitor.data.repository

import android.content.Context
import io.baiyanwu.coinmonitor.domain.model.WalletAddressKind
import io.baiyanwu.coinmonitor.domain.model.WalletAsset
import io.baiyanwu.coinmonitor.domain.model.WalletPortfolioSnapshot
import io.baiyanwu.coinmonitor.domain.repository.CachedWalletPortfolio
import io.baiyanwu.coinmonitor.domain.repository.WalletPortfolioCacheRepository
import java.io.File
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DefaultWalletPortfolioCacheRepository(context: Context) : WalletPortfolioCacheRepository {
    private val cacheFile = File(context.applicationContext.filesDir, "wallet_watch/last_snapshot.json")
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun load(address: String): CachedWalletPortfolio? = withContext(Dispatchers.IO) {
        if (!cacheFile.exists()) return@withContext null
        runCatching {
            json.decodeFromString<WalletPortfolioCacheDto>(cacheFile.readText())
                .takeIf { sameWalletAddress(it.address, address) }
                ?.toDomain()
        }.getOrNull()
    }

    override suspend fun save(snapshot: WalletPortfolioSnapshot, includeRiskInTotal: Boolean) = withContext(Dispatchers.IO) {
        val targetDirectory = requireNotNull(cacheFile.parentFile).apply { mkdirs() }
        val temporaryFile = File(targetDirectory, "${cacheFile.name}.tmp")
        temporaryFile.writeText(json.encodeToString(snapshot.toCacheDto(includeRiskInTotal)))
        if (!temporaryFile.renameTo(cacheFile)) {
            temporaryFile.copyTo(cacheFile, overwrite = true)
            temporaryFile.delete()
        }
    }
}

@Serializable
internal data class WalletPortfolioCacheDto(
    val address: String,
    val addressKind: String,
    val assets: List<WalletAssetCacheDto>,
    val totalValueUsd: String,
    val totalIsEstimated: Boolean,
    val updatedAtMillis: Long,
    val includeRiskInTotal: Boolean
) {
    fun toDomain(): CachedWalletPortfolio = CachedWalletPortfolio(
        snapshot = WalletPortfolioSnapshot(
            address = address,
            addressKind = WalletAddressKind.valueOf(addressKind),
            assets = assets.map(WalletAssetCacheDto::toDomain),
            totalValueUsd = BigDecimal(totalValueUsd),
            totalIsEstimated = totalIsEstimated,
            updatedAtMillis = updatedAtMillis
        ),
        includeRiskInTotal = includeRiskInTotal
    )
}

@Serializable
internal data class WalletAssetCacheDto(
    val chainIndex: String,
    val chainName: String,
    val symbol: String,
    val contractAddress: String,
    val balance: String,
    val tokenPriceUsd: String?,
    val holdingValueUsd: String?,
    val isRiskToken: Boolean
) {
    fun toDomain(): WalletAsset = WalletAsset(
        chainIndex = chainIndex,
        chainName = chainName,
        symbol = symbol,
        contractAddress = contractAddress,
        balance = BigDecimal(balance),
        tokenPriceUsd = tokenPriceUsd?.let(::BigDecimal),
        holdingValueUsd = holdingValueUsd?.let(::BigDecimal),
        isRiskToken = isRiskToken
    )
}

internal fun WalletPortfolioSnapshot.toCacheDto(includeRiskInTotal: Boolean) = WalletPortfolioCacheDto(
    address = address,
    addressKind = addressKind.name,
    assets = assets.map { asset ->
        WalletAssetCacheDto(
            chainIndex = asset.chainIndex,
            chainName = asset.chainName,
            symbol = asset.symbol,
            contractAddress = asset.contractAddress,
            balance = asset.balance.toPlainString(),
            tokenPriceUsd = asset.tokenPriceUsd?.toPlainString(),
            holdingValueUsd = asset.holdingValueUsd?.toPlainString(),
            isRiskToken = asset.isRiskToken
        )
    },
    totalValueUsd = totalValueUsd.toPlainString(),
    totalIsEstimated = totalIsEstimated,
    updatedAtMillis = updatedAtMillis,
    includeRiskInTotal = includeRiskInTotal
)

private fun sameWalletAddress(first: String, second: String): Boolean =
    if (first.startsWith("0x", ignoreCase = true) && second.startsWith("0x", ignoreCase = true)) {
        first.equals(second, ignoreCase = true)
    } else {
        first == second
    }
