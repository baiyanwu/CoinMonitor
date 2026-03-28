package io.baiyanwu.coinmonitor.data

import android.content.Context
import androidx.room.Room
import io.baiyanwu.coinmonitor.data.local.CoinMonitorDatabase
import io.baiyanwu.coinmonitor.data.network.NetworkFactory
import io.baiyanwu.coinmonitor.data.repository.DefaultAppPreferencesRepository
import io.baiyanwu.coinmonitor.data.repository.DefaultMarketQuoteRepository
import io.baiyanwu.coinmonitor.data.repository.DefaultMarketSearchRepository
import io.baiyanwu.coinmonitor.data.repository.DefaultOverlayRepository
import io.baiyanwu.coinmonitor.data.repository.DefaultWatchlistRepository
import io.baiyanwu.coinmonitor.domain.repository.AppPreferencesRepository
import io.baiyanwu.coinmonitor.domain.repository.MarketQuoteRepository
import io.baiyanwu.coinmonitor.domain.repository.MarketSearchRepository
import io.baiyanwu.coinmonitor.domain.repository.OverlayRepository
import io.baiyanwu.coinmonitor.domain.repository.WatchlistRepository

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    private val database = Room.databaseBuilder(
        appContext,
        CoinMonitorDatabase::class.java,
        "coin_monitor.db"
    ).addMigrations(
        CoinMonitorDatabase.MIGRATION_4_5
    ).build()

    private val networkFactory = NetworkFactory()

    val appPreferencesRepository: AppPreferencesRepository = DefaultAppPreferencesRepository(
        context = appContext
    )

    val watchlistRepository: WatchlistRepository = DefaultWatchlistRepository(
        watchItemDao = database.watchItemDao()
    )

    val overlayRepository: OverlayRepository = DefaultOverlayRepository(
        overlaySettingsDao = database.overlaySettingsDao(),
        watchItemDao = database.watchItemDao()
    )

    val marketSearchRepository: MarketSearchRepository = DefaultMarketSearchRepository(
        alphaApi = networkFactory.alphaApi,
        binanceApi = networkFactory.binanceApi,
        okxApi = networkFactory.okxApi
    )

    val marketQuoteRepository: MarketQuoteRepository = DefaultMarketQuoteRepository(
        alphaApi = networkFactory.alphaApi,
        binanceApi = networkFactory.binanceApi,
        okxApi = networkFactory.okxApi
    )
}
