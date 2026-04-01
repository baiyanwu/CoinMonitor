package io.baiyanwu.coinmonitor.data.repository

import io.baiyanwu.coinmonitor.data.local.dao.WatchItemDao
import io.baiyanwu.coinmonitor.data.local.toDomain
import io.baiyanwu.coinmonitor.data.local.toEntity
import io.baiyanwu.coinmonitor.domain.model.LivePriceTrend
import io.baiyanwu.coinmonitor.domain.model.MarketQuote
import io.baiyanwu.coinmonitor.domain.model.QuoteState
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.domain.repository.WatchlistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DefaultWatchlistRepository(
    private val watchItemDao: WatchItemDao
) : WatchlistRepository {
    override fun observeWatchlist(): Flow<List<WatchItem>> {
        return watchItemDao.observeWatchItems().map { rows -> rows.map { it.toDomain() } }
    }

    override suspend fun getWatchlist(): List<WatchItem> {
        return watchItemDao.getWatchItems().map { it.toDomain() }
    }

    override suspend fun add(item: WatchItem) {
        val existing = watchItemDao.findById(item.id)
        watchItemDao.upsert(
            item.copy(
                overlaySelected = existing?.overlaySelected ?: item.overlaySelected,
                lastPrice = existing?.lastPrice ?: item.lastPrice,
                previousPrice = existing?.previousPrice ?: item.previousPrice,
                liveTrend = existing?.liveTrend?.let(LivePriceTrend::valueOf) ?: item.liveTrend,
                change24hPercent = existing?.change24hPercent ?: item.change24hPercent,
                lastUpdatedAt = existing?.lastUpdatedAt ?: item.lastUpdatedAt
            ).toEntity()
        )
    }

    override suspend fun remove(id: String) {
        watchItemDao.deleteById(id)
    }

    override suspend fun updateQuotes(quotes: List<MarketQuote>) {
        if (quotes.isEmpty()) return
        val snapshot = quotes.associate { quote ->
            quote.id to QuoteState(
                lastPrice = quote.priceUsd,
                change24hPercent = quote.change24hPercent,
                lastUpdatedAt = System.currentTimeMillis()
            )
        }
        persistQuoteSnapshot(snapshot)
    }

    override suspend fun persistQuoteSnapshot(quotes: Map<String, QuoteState>) {
        if (quotes.isEmpty()) return
        val now = System.currentTimeMillis()
        val existingById = watchItemDao.getWatchItems().associateBy { it.id }
        quotes.forEach { (id, quote) ->
            val existing = existingById[id] ?: return@forEach
            val existingTrend = LivePriceTrend.valueOf(existing.liveTrend)
            val liveTrend = when {
                existing.lastPrice == null -> quote.liveTrend.takeIf { it != LivePriceTrend.NEUTRAL } ?: existingTrend
                quote.lastPrice > existing.lastPrice -> LivePriceTrend.UP
                quote.lastPrice < existing.lastPrice -> LivePriceTrend.DOWN
                else -> existingTrend
            }
            watchItemDao.updateQuote(
                id = id,
                lastPrice = quote.lastPrice,
                previousPrice = existing.lastPrice,
                liveTrend = liveTrend.name,
                change24hPercent = quote.change24hPercent,
                lastUpdatedAt = quote.lastUpdatedAt ?: now
            )
        }
    }
}
