package io.baiyanwu.coinmonitor.data.repository

import android.content.Context
import io.baiyanwu.coinmonitor.domain.model.*

class TrendingPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("trending_preferences", Context.MODE_PRIVATE)
    fun board(): TrendingBoard = TrendingBoard.entries.firstOrNull { it.name == preferences.getString("board", null) } ?: TrendingBoard.SHARED
    fun saveBoard(board: TrendingBoard) { preferences.edit().putString("board", board.name).apply() }
    fun selectedSources(): Set<TrendingSource> = preferences.getStringSet("sources", null)
        ?.mapNotNull { name -> TrendingSource.entries.firstOrNull { it.name == name } }?.toSet()
        ?.takeIf { it.size >= 2 } ?: TrendingSource.entries.toSet()
    fun saveSources(sources: Set<TrendingSource>) {
        require(sources.size >= 2)
        preferences.edit().putStringSet("sources", sources.map { it.name }.toSet()).apply()
    }
    fun load(source: TrendingSource): TrendingOptions = load(source.name, source)
    fun loadShared(): TrendingOptions = load("SHARED", TrendingSource.GMGN)
    private fun load(prefix: String, source: TrendingSource): TrendingOptions {
        fun value(field: String) = preferences.getString("${prefix}_$field", null)
        return TrendingOptions(
            chain = if (value("chain") == "all") null else TrendingChain.entries.firstOrNull { it.name == value("chain") } ?: TrendingChain.SOLANA,
            mode = TrendingMode.entries.firstOrNull { it.name == value("mode") } ?: TrendingMode.TRENDING,
            period = value("period")?.takeIf { it in TrendingOptions.periods(source) } ?: "5m",
            sort = TrendingSort.entries.firstOrNull { it.name == value("sort") } ?: TrendingSort.DEFAULT,
            ascending = value("ascending") == "true",
            value = TrendingValue.entries.firstOrNull { it.name == value("value") } ?: TrendingValue.MARKET_CAP,
            pumpChainIndex = value("pump_chain")?.takeUnless { it == "all" }
        )
    }
    fun save(source: TrendingSource, options: TrendingOptions) = save(source.name, options)
    fun saveShared(options: TrendingOptions) = save("SHARED", options)
    private fun save(prefix: String, options: TrendingOptions) {
        val fields = mapOf("chain" to (options.chain?.name ?: "all"), "mode" to options.mode.name,
            "period" to options.period, "sort" to options.sort.name,
            "pump_chain" to (options.pumpChainIndex ?: "all"), "ascending" to options.ascending.toString(), "value" to options.value.name)
        preferences.edit().apply { fields.forEach { (field, value) -> putString("${prefix}_$field", value) } }.apply()
    }
}
