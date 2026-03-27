package io.coinbar.tokenmonitor.data

import android.content.Context
import androidx.room.Room
import io.coinbar.tokenmonitor.data.local.TokenMonitorDatabase
import io.coinbar.tokenmonitor.data.network.NetworkFactory
import io.coinbar.tokenmonitor.data.repository.DefaultAppPreferencesRepository
import io.coinbar.tokenmonitor.data.repository.DefaultMarketQuoteRepository
import io.coinbar.tokenmonitor.data.repository.DefaultMarketSearchRepository
import io.coinbar.tokenmonitor.data.repository.DefaultOverlayRepository
import io.coinbar.tokenmonitor.data.repository.DefaultWatchlistRepository
import io.coinbar.tokenmonitor.domain.repository.AppPreferencesRepository
import io.coinbar.tokenmonitor.domain.repository.MarketQuoteRepository
import io.coinbar.tokenmonitor.domain.repository.MarketSearchRepository
import io.coinbar.tokenmonitor.domain.repository.OverlayRepository
import io.coinbar.tokenmonitor.domain.repository.WatchlistRepository

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    private val database = Room.databaseBuilder(
        appContext,
        TokenMonitorDatabase::class.java,
        "token_monitor.db"
    ).fallbackToDestructiveMigration().build()

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
