package io.baiyanwu.coinmonitor.domain.repository

import io.baiyanwu.coinmonitor.domain.model.MarketQuote
import io.baiyanwu.coinmonitor.domain.model.QuoteState
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface QuoteRepository {
    val quotes: StateFlow<Map<String, QuoteState>>

    fun observeQuote(id: String): Flow<QuoteState?>

    fun seedFromWatchItems(items: List<WatchItem>)

    fun retainOnly(ids: Set<String>)

    fun applyQuotes(quotes: List<MarketQuote>)

    fun getQuote(id: String): QuoteState?

    fun getQuotes(ids: Collection<String>): Map<String, QuoteState>
}
