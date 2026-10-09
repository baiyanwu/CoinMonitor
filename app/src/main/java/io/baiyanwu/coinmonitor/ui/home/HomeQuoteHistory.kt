package io.baiyanwu.coinmonitor.ui.home

import io.baiyanwu.coinmonitor.ui.components.CoinMonitorChartPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 首页会话内的真实报价样本；按币种独立发布，避免每次报价重组整个首页。 */
internal class HomeQuoteHistory(private val maximumSamples: Int = 32) {
    private data class Series(
        var key: String? = null,
        val samples: MutableStateFlow<List<CoinMonitorChartPoint>> = MutableStateFlow(emptyList())
    )
    private val series = mutableMapOf<String, Series>()
    private var retainedIds: Set<String>? = null

    init { require(maximumSamples >= 2) }

    fun observe(id: String): StateFlow<List<CoinMonitorChartPoint>> =
        series.getOrPut(id) { Series() }.samples.asStateFlow()

    fun record(id: String, seriesKey: String, price: Double?, timestampMillis: Long?) {
        if (retainedIds?.let { id !in it } == true) return
        if (price == null || !price.isFinite() || price <= 0 || timestampMillis == null || timestampMillis <= 0) return
        val entry = series.getOrPut(id) { Series() }
        if (entry.key != seriesKey) {
            entry.key = seriesKey
            entry.samples.value = emptyList()
        }
        val current = entry.samples.value
        val last = current.lastOrNull()
        if (last != null && timestampMillis < last.timestampMillis) return
        val sample = CoinMonitorChartPoint(timestampMillis, price)
        val updated = if (last?.timestampMillis == timestampMillis) current.dropLast(1) + sample else current + sample
        entry.samples.value = updated.takeLast(maximumSamples)
    }

    fun retainOnly(ids: Set<String>) {
        retainedIds = ids
        series.keys.filter { it !in ids }.forEach { removed ->
            series.remove(removed)?.samples?.value = emptyList()
        }
    }
}
