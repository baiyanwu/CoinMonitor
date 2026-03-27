package io.baiyanwu.coinmonitor.domain.model

/**
 * 记录最近一次有效价格跳动的方向。
 * 当新一轮价格与当前价格相同的时候，沿用上一轮方向，避免颜色闪一下就回默认态。
 */
enum class LivePriceTrend {
    NEUTRAL,
    UP,
    DOWN
}
