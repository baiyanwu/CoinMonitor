package io.coinbar.tokenmonitor.ui

import androidx.compose.ui.graphics.Color
import io.coinbar.tokenmonitor.domain.model.WatchItem
import io.coinbar.tokenmonitor.ui.theme.TokenMonitorColors

/**
 * 统一封装价格短时跳动的颜色状态。
 * 颜色不再直接依赖 current/previous 的一次性比较，而是依赖持久化后的趋势方向，
 * 这样同价重复刷新时也能保持上一轮的红绿状态。
 */
fun WatchItem.resolveLivePriceColor(colors: TokenMonitorColors, defaultColor: Color): Color {
    return when (liveTrend) {
        io.coinbar.tokenmonitor.domain.model.LivePriceTrend.UP -> colors.positive
        io.coinbar.tokenmonitor.domain.model.LivePriceTrend.DOWN -> colors.negative
        io.coinbar.tokenmonitor.domain.model.LivePriceTrend.NEUTRAL -> defaultColor
    }
}

fun WatchItem.resolveChangeColor(colors: TokenMonitorColors, defaultColor: Color): Color {
    return when {
        (change24hPercent ?: 0.0) > 0 -> colors.positive
        (change24hPercent ?: 0.0) < 0 -> colors.negative
        else -> defaultColor
    }
}
