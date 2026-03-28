package io.baiyanwu.coinmonitor.data.refresh

import io.baiyanwu.coinmonitor.domain.model.WatchItem

/**
 * 全局行情刷新引擎的统一抽象。
 *
 * 当前先落轮询实现，后续切到交易所或链上的 WSS 时，只需要新增新的引擎实现并保持这一层契约不变，
 * 首页和悬浮窗都不需要再改调用方式。
 */
interface QuoteRefreshEngine {
    fun updateConfig(config: QuoteRefreshConfig)

    suspend fun refreshNow()

    fun stop()
}

/**
 * 刷新引擎的运行配置。
 *
 * enabled 由上层协调器统一计算，表示当前是否应该持续保持行情同步；
 * items 和 refreshIntervalMillis 则是具体的数据范围和刷新节奏。
 */
data class QuoteRefreshConfig(
    val enabled: Boolean,
    val items: List<WatchItem>,
    val refreshIntervalMillis: Long
)
