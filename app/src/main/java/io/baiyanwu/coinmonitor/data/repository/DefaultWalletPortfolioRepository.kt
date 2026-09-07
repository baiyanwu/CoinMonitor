package io.baiyanwu.coinmonitor.data.repository

import io.baiyanwu.coinmonitor.data.network.OkxWalletClient
import io.baiyanwu.coinmonitor.domain.model.OkxWalletChainRegistry
import io.baiyanwu.coinmonitor.domain.model.WalletAddressParser
import io.baiyanwu.coinmonitor.domain.model.WalletAsset
import io.baiyanwu.coinmonitor.domain.model.WalletPortfolioSnapshot
import io.baiyanwu.coinmonitor.domain.model.WalletTotalResult
import io.baiyanwu.coinmonitor.domain.repository.WalletPortfolioRepository
import java.math.BigDecimal
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class DefaultWalletPortfolioRepository internal constructor(
    private val client: OkxWalletClient,
    private val nowMillis: () -> Long = System::currentTimeMillis
) : WalletPortfolioRepository {
    override suspend fun load(address: String, includeRiskInTotal: Boolean): WalletPortfolioSnapshot = coroutineScope {
        val normalized = address.trim()
        val kind = requireNotNull(WalletAddressParser.classify(normalized)) { "请输入有效的 EVM 或 Solana 地址" }
        val chains = resolveSupportedChains(kind)
        val assetsRequest = async { runCatching { client.getAllTokenBalances(normalized, chains) } }
        val totalRequest = async { runCatching { client.getTotalValue(normalized, chains, includeRiskInTotal) } }
        val assetRows = assetsRequest.await().getOrThrow()
        val assets = assetRows.mapNotNull { row ->
            val balance = row.balance.toBigDecimalOrNull() ?: return@mapNotNull null
            if (balance <= BigDecimal.ZERO) return@mapNotNull null
            val price = row.tokenPrice?.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }
            WalletAsset(
                chainIndex = row.chainIndex,
                chainName = OkxWalletChainRegistry.displayName(row.chainIndex),
                symbol = row.symbol,
                contractAddress = row.contractAddress,
                balance = balance,
                tokenPriceUsd = price,
                holdingValueUsd = price?.multiply(balance),
                isRiskToken = row.isRiskToken
            )
        }.sortedWith(WALLET_ASSET_COMPARATOR)
        val totalResult = totalRequest.await()
        val remoteTotal = totalResult.getOrNull()?.toBigDecimalOrNull()
        val fallback = assets.asSequence()
            .filter { includeRiskInTotal || !it.isRiskToken }
            .mapNotNull(WalletAsset::holdingValueUsd)
            .fold(BigDecimal.ZERO, BigDecimal::add)
        WalletPortfolioSnapshot(
            address = normalized,
            addressKind = kind,
            assets = assets,
            totalValueUsd = remoteTotal ?: fallback,
            totalIsEstimated = remoteTotal == null,
            updatedAtMillis = nowMillis()
        )
    }

    override suspend fun loadTotal(address: String, includeRiskInTotal: Boolean): WalletTotalResult {
        val kind = WalletAddressParser.classify(address) ?: return WalletTotalResult.Failure("请输入有效的 EVM 或 Solana 地址")
        return runCatching {
            val value = client.getTotalValue(address.trim(), resolveSupportedChains(kind), includeRiskInTotal)
            WalletTotalResult.Success(value.toBigDecimal())
        }.getOrElse { WalletTotalResult.Failure(it.message ?: "总资产估值刷新失败") }
    }

    private suspend fun resolveSupportedChains(kind: io.baiyanwu.coinmonitor.domain.model.WalletAddressKind): List<String> {
        val supported = client.getSupportedChainIndexes()
        val requested = OkxWalletChainRegistry.indexesFor(kind)
        val resolved = requested.filter(supported::contains)
        if (resolved.isEmpty()) throw IllegalStateException("OKX 当前不支持该地址类型的资产查询。")
        return resolved
    }

    companion object {
        internal val WALLET_ASSET_COMPARATOR = compareBy<WalletAsset> { it.isRiskToken }
            .thenBy { it.holdingValueUsd == null }
            .thenByDescending { it.holdingValueUsd ?: BigDecimal.ZERO }
            .thenBy { it.symbol.uppercase() }
            .thenBy { it.chainIndex }
    }
}
