package io.baiyanwu.coinmonitor.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.baiyanwu.coinmonitor.data.local.CoinMonitorDatabase
import io.baiyanwu.coinmonitor.data.local.WatchItemEntity
import io.baiyanwu.coinmonitor.domain.model.ExchangeSource
import io.baiyanwu.coinmonitor.domain.model.MarketType
import io.baiyanwu.coinmonitor.domain.model.OverlaySettings
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OverlayRepositoryPersistenceTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: CoinMonitorDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            context,
            CoinMonitorDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun toggle_closesClearsOrderAndReopenAppendsToTail() = runBlocking {
        val repository = overlayRepository()
        val dao = database.watchItemDao()
        dao.upsert(entity("exchange", addedAt = 1L))
        dao.upsert(entity("onchain", marketType = MarketType.ONCHAIN_TOKEN, addedAt = 2L))
        dao.upsert(entity("futures", marketType = MarketType.CEX_USDT_FUTURES, addedAt = 3L))

        repository.toggleItem("exchange")
        repository.toggleItem("onchain")
        repository.toggleItem("futures")
        repository.moveOverlayItem("futures", "exchange")

        assertEquals(
            listOf("futures", "exchange", "onchain"),
            dao.getOverlayItems().map { it.id }
        )

        repository.toggleItem("exchange")
        val disabled = dao.findById("exchange")!!
        assertFalse(disabled.overlaySelected)
        assertNull(disabled.overlayOrder)

        repository.toggleItem("exchange")
        assertEquals(
            listOf("futures", "onchain", "exchange"),
            dao.getOverlayItems().map { it.id }
        )
    }

    @Test
    fun toggle_rejectsEleventhSelectedItemWithoutPartiallyUpdatingIt() = runBlocking {
        val repository = overlayRepository()
        val dao = database.watchItemDao()
        repeat(OverlaySettings.MAX_SELECTABLE_ITEMS + 1) { index ->
            dao.upsert(entity("item-$index", addedAt = index.toLong()))
        }
        repeat(OverlaySettings.MAX_SELECTABLE_ITEMS) { index ->
            repository.toggleItem("item-$index")
        }

        val result = runCatching {
            repository.toggleItem("item-${OverlaySettings.MAX_SELECTABLE_ITEMS}")
        }

        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals(OverlaySettings.MAX_SELECTABLE_ITEMS, dao.getOverlayItems().size)
        val rejected = dao.findById("item-${OverlaySettings.MAX_SELECTABLE_ITEMS}")!!
        assertFalse(rejected.overlaySelected)
        assertNull(rejected.overlayOrder)
    }

    @Test
    fun homeAndOverlayReordersDoNotModifyEachOthersFields() = runBlocking {
        val overlayRepository = overlayRepository()
        val watchlistRepository = DefaultWatchlistRepository(database)
        val dao = database.watchItemDao()
        dao.upsert(
            entity(
                id = "exchange",
                selected = true,
                overlayOrder = 1024L,
                homeOrder = 10L,
                addedAt = 1L
            )
        )
        dao.upsert(
            entity(
                id = "onchain",
                marketType = MarketType.ONCHAIN_TOKEN,
                selected = true,
                overlayOrder = 2048L,
                homeOrder = 20L,
                addedAt = 2L
            )
        )

        watchlistRepository.setHomePinned("onchain", true)
        watchlistRepository.moveHomeItem("exchange", null)
        assertEquals(
            listOf("exchange", "onchain"),
            dao.getOverlayItems().map { it.id }
        )

        val homeStateBeforeOverlayMove = dao.getWatchItems().associate { item ->
            item.id to Triple(item.homePinned, item.homeOrder, item.homePinnedOrder)
        }
        overlayRepository.moveOverlayItem("onchain", "exchange")
        val homeStateAfterOverlayMove = dao.getWatchItems().associate { item ->
            item.id to Triple(item.homePinned, item.homeOrder, item.homePinnedOrder)
        }

        assertEquals(listOf("onchain", "exchange"), dao.getOverlayItems().map { it.id })
        assertEquals(homeStateBeforeOverlayMove, homeStateAfterOverlayMove)
    }

    @Test
    fun repositoryRecreationAndDeleteReaddDoNotLeakOldOrder() = runBlocking {
        val firstRepository = overlayRepository()
        val watchlistRepository = DefaultWatchlistRepository(database)
        val dao = database.watchItemDao()
        dao.upsert(entity("first", selected = true, overlayOrder = 1024L, addedAt = 1L))
        dao.upsert(entity("second", selected = true, overlayOrder = 2048L, addedAt = 2L))

        firstRepository.moveOverlayItem("second", "first")
        val recreatedRepository = overlayRepository()
        assertEquals(
            listOf("second", "first"),
            recreatedRepository.observeOverlayItems().first().map { it.id }
        )

        watchlistRepository.remove("second")
        watchlistRepository.add(
            WatchItem(
                id = "second",
                symbol = "SECOND",
                name = "SECOND",
                exchangeSource = ExchangeSource.BINANCE,
                addedAt = 3L
            )
        )
        val readded = dao.findById("second")!!
        assertFalse(readded.overlaySelected)
        assertNull(readded.overlayOrder)

        recreatedRepository.toggleItem("second")
        assertEquals(
            listOf("first", "second"),
            dao.getOverlayItems().map { it.id }
        )
    }

    private fun overlayRepository(): DefaultOverlayRepository {
        return DefaultOverlayRepository(
            context = context,
            overlayPreferences = UnusedPreferencesDataStore,
            database = database
        )
    }

    private fun entity(
        id: String,
        marketType: MarketType = MarketType.CEX_SPOT,
        selected: Boolean = false,
        overlayOrder: Long? = null,
        homeOrder: Long = 0L,
        addedAt: Long
    ): WatchItemEntity {
        return WatchItemEntity(
            id = id,
            symbol = id.uppercase(),
            name = id,
            source = if (marketType == MarketType.ONCHAIN_TOKEN) {
                ExchangeSource.ONCHAIN.name
            } else {
                ExchangeSource.BINANCE.name
            },
            marketType = marketType.name,
            chainFamily = null,
            chainIndex = null,
            tokenAddress = null,
            poolAddress = null,
            poolTokenSide = null,
            iconUrl = null,
            overlaySelected = selected,
            overlayOrder = overlayOrder,
            addedAt = addedAt,
            homePinned = false,
            homeOrder = homeOrder,
            homePinnedOrder = null,
            lastPrice = null,
            previousPrice = null,
            liveTrend = "NEUTRAL",
            change24hPercent = null,
            lastUpdatedAt = null
        )
    }

    private object UnusedPreferencesDataStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flowOf(emptyPreferences())

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            return transform(emptyPreferences())
        }
    }
}
