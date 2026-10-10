package io.baiyanwu.coinmonitor.clipboard

import io.baiyanwu.coinmonitor.domain.model.WalletAsset
import io.baiyanwu.coinmonitor.domain.model.WalletPortfolioSnapshot
import java.math.BigDecimal

internal interface ClipboardWalletDataSource {
    fun isConfigured(): Boolean
    suspend fun load(address: String): WalletPortfolioSnapshot
}

internal data class ClipboardWalletPreview(val snapshot: WalletPortfolioSnapshot, val chainIndex: String) {
    val assets: List<WalletAsset> get() = snapshot.assets.filter { it.chainIndex == chainIndex && !it.isRiskToken }
    val value: BigDecimal get() = assets.mapNotNull { it.holdingValueUsd }.fold(BigDecimal.ZERO, BigDecimal::add)
    val partial: Boolean get() = assets.any { it.holdingValueUsd == null }
}
