package io.baiyanwu.coinmonitor.ui.components

import android.content.Context
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.size.Precision
import coil3.size.Scale
import io.baiyanwu.coinmonitor.domain.model.MarketType
import io.baiyanwu.coinmonitor.domain.model.WatchItem

/** 首页与两个悬浮窗共用来源策略，链标不能占用代币主图。 */
internal data class WatchItemIconModel(
    val symbol: String,
    val tokenIconUrl: String?,
    val allowSymbolLookup: Boolean,
    val chainIndex: String?
)

internal fun watchItemIconModel(item: WatchItem) = WatchItemIconModel(
    symbol = item.baseSymbol,
    tokenIconUrl = item.iconUrl?.trim()?.takeIf(String::isNotBlank),
    allowSymbolLookup = item.marketType != MarketType.ONCHAIN_TOKEN,
    chainIndex = item.chainIndex?.trim()?.takeIf { item.marketType == MarketType.ONCHAIN_TOKEN && it.isNotBlank() }
)

/** 两种宿主都通过 Coil 单例加载，使用相同的尺寸/解码约束和缓存键规则。 */
internal fun coinIconRequest(context: Context, data: Any, sizePixels: Int, scale: Scale = Scale.FIT): ImageRequest =
    ImageRequest.Builder(context).data(data).size(sizePixels.coerceAtLeast(1))
        .precision(Precision.EXACT).scale(scale).allowHardware(false).crossfade(false).build()
