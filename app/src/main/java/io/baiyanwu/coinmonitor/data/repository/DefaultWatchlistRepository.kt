package io.baiyanwu.coinmonitor.data.repository

import androidx.room.withTransaction
import io.baiyanwu.coinmonitor.data.local.CoinMonitorDatabase
import io.baiyanwu.coinmonitor.data.local.dao.WatchItemDao
import io.baiyanwu.coinmonitor.data.local.toDomain
import io.baiyanwu.coinmonitor.data.local.toEntity
import io.baiyanwu.coinmonitor.domain.model.LivePriceTrend
import io.baiyanwu.coinmonitor.domain.model.MarketQuote
import io.baiyanwu.coinmonitor.domain.model.MarketType
import io.baiyanwu.coinmonitor.domain.model.QuoteState
import io.baiyanwu.coinmonitor.domain.model.PoolTokenSide
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.domain.model.onchainAddressesEqual
import io.baiyanwu.coinmonitor.domain.repository.WatchlistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DefaultWatchlistRepository(
    private val database: CoinMonitorDatabase
) : WatchlistRepository {
    private val watchItemDao: WatchItemDao = database.watchItemDao()

    override fun observeWatchlist(): Flow<List<WatchItem>> {
        return watchItemDao.observeWatchItems().map { rows -> rows.map { it.toDomain() } }
    }

    override fun observeHomeWatchlist(): Flow<List<WatchItem>> {
        return watchItemDao.observeHomeOrderedWatchItems().map { rows -> rows.map { it.toDomain() } }
    }

    override suspend fun getWatchlist(): List<WatchItem> {
        return watchItemDao.getWatchItems().map { it.toDomain() }
    }

    override suspend fun add(item: WatchItem) {
        database.withTransaction {
            val allItems = watchItemDao.getWatchItems()
            val existing = watchItemDao.findById(item.id)
                ?: allItems.firstOrNull { row -> row.toDomain().semanticKey == item.semanticKey }
            watchItemDao.upsert(
                item.copy(
                    id = existing?.id ?: item.id,
                    poolAddress = item.poolAddress ?: existing?.poolAddress,
                    poolTokenSide = item.poolTokenSide
                        ?: existing?.poolTokenSide?.let(PoolTokenSide::valueOf),
                    overlaySelected = existing?.overlaySelected ?: item.overlaySelected,
                    homePinned = existing?.homePinned ?: false,
                    homeOrder = existing?.homeOrder ?: WatchlistHomeOrderManager.nextNormalOrder(allItems),
                    homePinnedOrder = existing?.homePinnedOrder,
                    lastPrice = existing?.lastPrice ?: item.lastPrice,
                    previousPrice = existing?.previousPrice ?: item.previousPrice,
                    liveTrend = existing?.liveTrend?.let(LivePriceTrend::valueOf) ?: item.liveTrend,
                    change24hPercent = existing?.change24hPercent ?: item.change24hPercent,
                    lastUpdatedAt = existing?.lastUpdatedAt ?: item.lastUpdatedAt
                ).toEntity()
            )
        }
    }

    override suspend fun remove(id: String) {
        watchItemDao.deleteById(id)
    }

    override suspend fun setHomePinned(id: String, pinned: Boolean) {
        database.withTransaction {
            val allItems = watchItemDao.getWatchItems()
            val target = allItems.firstOrNull { it.id == id } ?: return@withTransaction
            if (target.homePinned == pinned) return@withTransaction
            val pinnedOrder = if (pinned) {
                WatchlistHomeOrderManager.nextPinnedOrder(allItems)
            } else {
                null
            }
            watchItemDao.updateHomePinnedState(
                id = id,
                pinned = pinned,
                homePinnedOrder = pinnedOrder
            )
        }
    }

    override suspend fun moveHomeItem(id: String, targetBeforeId: String?) {
        database.withTransaction {
            val allItems = watchItemDao.getWatchItems()
            val target = allItems.firstOrNull { it.id == id } ?: return@withTransaction
            if (target.homePinned) return@withTransaction
            WatchlistHomeOrderManager.reorderNormalGroup(
                items = allItems,
                itemId = id,
                targetBeforeId = targetBeforeId
            ).forEach { update ->
                watchItemDao.updateHomeOrder(id = update.id, homeOrder = update.order)
            }
        }
    }

    override suspend fun movePinnedHomeItem(id: String, targetBeforeId: String?) {
        database.withTransaction {
            val allItems = watchItemDao.getWatchItems()
            val target = allItems.firstOrNull { it.id == id } ?: return@withTransaction
            if (!target.homePinned) return@withTransaction
            WatchlistHomeOrderManager.reorderPinnedGroup(
                items = allItems,
                itemId = id,
                targetBeforeId = targetBeforeId
            ).forEach { update ->
                watchItemDao.updateHomePinnedOrder(id = update.id, homePinnedOrder = update.order)
            }
        }
    }

    override suspend fun updateQuotes(quotes: List<MarketQuote>) {
        if (quotes.isEmpty()) return
        database.withTransaction {
            val now = System.currentTimeMillis()
            val existingById = watchItemDao.getWatchItems().associateBy { it.id }
            quotes.forEach { quote ->
                val existing = existingById[quote.id] ?: return@forEach
                val current = existing.toDomain()
                val bindingChanged = if (current.marketType == MarketType.ONCHAIN_TOKEN) {
                    if (!shouldApplyOnchainQuote(current, quote)) return@forEach
                    val poolAddress = quote.poolAddress ?: return@forEach
                    val side = quote.poolTokenSide ?: return@forEach
                    val changed = !poolBindingsEqual(
                        item = current,
                        leftAddress = current.poolAddress,
                        leftSide = current.poolTokenSide,
                        rightAddress = poolAddress,
                        rightSide = side
                    )
                    if (changed) {
                        watchItemDao.updateOnchainPoolBinding(
                            id = quote.id,
                            poolAddress = poolAddress,
                            poolTokenSide = side.name
                        )
                    }
                    changed
                } else {
                    false
                }
                val resetTrend = quote.resetTrend || bindingChanged
                val previousPrice = existing.lastPrice.takeUnless { resetTrend }
                val existingTrend = LivePriceTrend.valueOf(existing.liveTrend)
                val liveTrend = when {
                    resetTrend -> LivePriceTrend.NEUTRAL
                    previousPrice == null -> existingTrend
                    quote.priceUsd > previousPrice -> LivePriceTrend.UP
                    quote.priceUsd < previousPrice -> LivePriceTrend.DOWN
                    else -> existingTrend
                }
                watchItemDao.updateQuote(
                    id = quote.id,
                    lastPrice = quote.priceUsd,
                    previousPrice = previousPrice,
                    liveTrend = liveTrend.name,
                    change24hPercent = quote.change24hPercent,
                    lastUpdatedAt = now
                )
            }
        }
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

    override suspend fun updateOnchainPoolBinding(
        id: String,
        poolAddress: String,
        side: PoolTokenSide
    ): Boolean {
        return watchItemDao.updateOnchainPoolBinding(
            id = id,
            poolAddress = poolAddress,
            poolTokenSide = side.name
        ) > 0
    }
}

internal fun shouldApplyOnchainQuote(current: WatchItem, quote: MarketQuote): Boolean {
    val quotePoolAddress = quote.poolAddress ?: return false
    val quoteSide = quote.poolTokenSide ?: return false
    if (poolBindingsEqual(
            item = current,
            leftAddress = current.poolAddress,
            leftSide = current.poolTokenSide,
            rightAddress = quotePoolAddress,
            rightSide = quoteSide
        )
    ) {
        return true
    }
    return poolBindingsEqual(
        item = current,
        leftAddress = current.poolAddress,
        leftSide = current.poolTokenSide,
        rightAddress = quote.requestedPoolAddress,
        rightSide = quote.requestedPoolTokenSide
    )
}

private fun poolBindingsEqual(
    item: WatchItem,
    leftAddress: String?,
    leftSide: PoolTokenSide?,
    rightAddress: String?,
    rightSide: PoolTokenSide?
): Boolean {
    if (leftAddress == null || rightAddress == null) {
        return leftAddress == null && rightAddress == null && leftSide == rightSide
    }
    val family = item.chainFamily ?: return false
    return leftSide == rightSide && onchainAddressesEqual(family, leftAddress, rightAddress)
}
