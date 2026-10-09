package io.baiyanwu.coinmonitor.ui.wallet

import io.baiyanwu.coinmonitor.domain.model.SelfCustodyAsset
import io.baiyanwu.coinmonitor.domain.model.WalletNetwork
import io.baiyanwu.coinmonitor.domain.model.WalletProfile
import java.math.BigDecimal

internal data class WalletHomeSummary(
    val totalUsd: BigDecimal?,
    val assetCount: Int,
    val networkCount: Int,
    val unpricedAssetCount: Int,
    val updatedAtMillis: Long?
)

/** 沿用显示过滤规则，汇总所有网络；未知估值不伪装为零，刷新失败仍显示旧快照。 */
internal fun WalletUiState.homeSummary(): WalletHomeSummary {
    val assets = visiblePortfolioAssets
    val priced = assets.mapNotNull(SelfCustodyAsset::valueUsd)
    return WalletHomeSummary(
        totalUsd = when {
            portfolio == null -> null
            assets.isNotEmpty() && priced.isEmpty() -> null
            else -> priced.fold(BigDecimal.ZERO, BigDecimal::add)
        },
        assetCount = assets.size,
        networkCount = networkConfiguration.enabledNetworks.count { activeWallet?.supports(it) == true },
        unpricedAssetCount = assets.size - priced.size,
        updatedAtMillis = portfolio?.updatedAtMillis
    )
}

/** 指定网络仅展示该网络对应地址；全网络时优先 EVM，缺少 EVM 时回退 Solana。 */
internal fun WalletProfile.homeAddress(selectedNetwork: WalletNetwork?): String? =
    if (selectedNetwork == null) addressFor(WalletNetwork.ETHEREUM) ?: addressFor(WalletNetwork.SOLANA)
    else addressFor(selectedNetwork)
