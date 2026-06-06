package io.baiyanwu.coinmonitor.data.repository

import io.baiyanwu.coinmonitor.domain.model.LivePriceTrend
import io.baiyanwu.coinmonitor.domain.model.MarketQuote
import io.baiyanwu.coinmonitor.domain.model.QuoteState
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.domain.model.toQuoteStateOrNull
import io.baiyanwu.coinmonitor.domain.repository.QuoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

class InMemoryQuoteRepository : QuoteRepository {
    private val _quotes = MutableStateFlow<Map<String, QuoteState>>(emptyMap())
    private val quoteFlows = ConcurrentHashMap<String, MutableStateFlow<QuoteState?>>()
    override val quotes: StateFlow<Map<String, QuoteState>> = _quotes.asStateFlow()

    override fun observeQuote(id: String): Flow<QuoteState?> {
        return quoteFlows.getOrPut(id) { MutableStateFlow(_quotes.value[id]) }.asStateFlow()
    }

    override fun seedFromWatchItems(items: List<WatchItem>) {
        _quotes.update { current ->
            if (items.isEmpty()) return@update current
            buildMap {
                current.forEach { (id, quote) ->
                    put(id, quote)
                }
                items.forEach { item ->
                    if (containsKey(item.id)) return@forEach
                    item.toQuoteStateOrNull()?.let { quote ->
                        put(item.id, quote)
                        quoteFlows.getOrPut(item.id) { MutableStateFlow(quote) }.value = quote
                    }
                }
            }
        }
    }

    override fun retainOnly(ids: Set<String>) {
        _quotes.update { current ->
            if (current.isEmpty()) return@update current
            current.filterKeys { it in ids }
        }
        quoteFlows.keys
            .filter { it !in ids }
            .forEach { removedId ->
                quoteFlows.remove(removedId)
            }
    }

    override fun applyQuotes(quotes: List<MarketQuote>) {
        if (quotes.isEmpty()) return
        val now = System.currentTimeMillis()
        _quotes.update { current ->
            buildMap {
                current.forEach { (id, quote) ->
                    put(id, quote)
                }
                quotes.forEach { quote ->
                    val existing = get(quote.id)
                    val trend = when {
                        existing == null -> LivePriceTrend.NEUTRAL
                        quote.priceUsd > existing.lastPrice -> LivePriceTrend.UP
                        quote.priceUsd < existing.lastPrice -> LivePriceTrend.DOWN
                        else -> existing.liveTrend
                    }
                    put(
                        quote.id,
                        QuoteState(
                            lastPrice = quote.priceUsd,
                            previousPrice = existing?.lastPrice,
                            liveTrend = trend,
                            change24hPercent = quote.change24hPercent,
                            lastUpdatedAt = now
                        )
                    )
                    quoteFlows.getOrPut(quote.id) { MutableStateFlow(null) }.value = get(quote.id)
                }
            }
        }
    }

    override fun getQuote(id: String): QuoteState? = quotes.value[id]

    override fun getQuotes(ids: Collection<String>): Map<String, QuoteState> {
        val current = quotes.value
        return ids.mapNotNull { id ->
            current[id]?.let { quote -> id to quote }
        }.toMap()
    }
}
