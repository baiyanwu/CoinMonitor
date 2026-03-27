package io.baiyanwu.coinmonitor.ui

import androidx.compose.ui.graphics.Color
import io.baiyanwu.coinmonitor.domain.model.LivePriceTrend
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorColors

/**
 * 统一封装价格短时跳动的颜色状态。
 * 颜色不再直接依赖 current/previous 的一次性比较，而是依赖持久化后的趋势方向，
 * 这样同价重复刷新时也能保持上一轮的红绿状态。
 */
fun WatchItem.resolveLivePriceColor(colors: CoinMonitorColors, defaultColor: Color): Color {
    return when (liveTrend) {
        LivePriceTrend.UP -> colors.positive
        LivePriceTrend.DOWN -> colors.negative
        LivePriceTrend.NEUTRAL -> defaultColor
    }
}

fun WatchItem.resolveChangeColor(colors: CoinMonitorColors, defaultColor: Color): Color {
    return when {
        (change24hPercent ?: 0.0) > 0 -> colors.positive
        (change24hPercent ?: 0.0) < 0 -> colors.negative
        else -> defaultColor
    }
}
