package io.baiyanwu.coinmonitor.data

import android.content.Context
import androidx.room.Room
import io.baiyanwu.coinmonitor.data.local.CoinMonitorDatabase
import io.baiyanwu.coinmonitor.data.network.NetworkFactory
import io.baiyanwu.coinmonitor.data.refresh.GlobalQuoteRefreshCoordinator
import io.baiyanwu.coinmonitor.data.repository.DefaultAiChatRepository
import io.baiyanwu.coinmonitor.data.repository.DefaultAiConfigRepository
import io.baiyanwu.coinmonitor.data.repository.DefaultAppPreferencesRepository
import io.baiyanwu.coinmonitor.data.repository.DefaultMarketKlineRepository
import io.baiyanwu.coinmonitor.data.repository.DefaultMarketQuoteRepository
import io.baiyanwu.coinmonitor.data.repository.DefaultMarketSearchRepository
import io.baiyanwu.coinmonitor.data.repository.DefaultNetworkLogRepository
import io.baiyanwu.coinmonitor.data.repository.DefaultOkxCredentialsRepository
import io.baiyanwu.coinmonitor.data.repository.DefaultOverlayRepository
import io.baiyanwu.coinmonitor.data.repository.DefaultWatchlistRepository
import io.baiyanwu.coinmonitor.data.repository.InMemoryQuoteRepository
import io.baiyanwu.coinmonitor.domain.repository.AppPreferencesRepository
import io.baiyanwu.coinmonitor.domain.repository.AiChatRepository
import io.baiyanwu.coinmonitor.domain.repository.AiConfigRepository
import io.baiyanwu.coinmonitor.domain.repository.MarketKlineRepository
import io.baiyanwu.coinmonitor.domain.repository.MarketQuoteRepository
import io.baiyanwu.coinmonitor.domain.repository.MarketSearchRepository
import io.baiyanwu.coinmonitor.domain.repository.NetworkLogRepository
import io.baiyanwu.coinmonitor.domain.repository.OkxCredentialsRepository
import io.baiyanwu.coinmonitor.domain.repository.OverlayRepository
import io.baiyanwu.coinmonitor.domain.repository.QuoteRepository
import io.baiyanwu.coinmonitor.domain.repository.WatchlistRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val klineSelectionStore = KlineSelectionStore()

    private val database = Room.databaseBuilder(
        appContext,
        CoinMonitorDatabase::class.java,
        "coin_monitor.db"
    ).addMigrations(
        CoinMonitorDatabase.MIGRATION_4_5,
        CoinMonitorDatabase.MIGRATION_5_6,
        CoinMonitorDatabase.MIGRATION_6_7
    ).build()

    val networkLogRepository: NetworkLogRepository = DefaultNetworkLogRepository()

    private val networkFactory = NetworkFactory(
        networkLogRepository = networkLogRepository
    )
    val appPreferencesRepository: AppPreferencesRepository = DefaultAppPreferencesRepository(
        context = appContext
    )

    val quoteRepository: QuoteRepository = InMemoryQuoteRepository()

    val watchlistRepository: WatchlistRepository = DefaultWatchlistRepository(
        database = database
    )

    val overlayRepository: OverlayRepository = DefaultOverlayRepository(
        context = appContext,
        overlaySettingsDao = database.overlaySettingsDao(),
        watchItemDao = database.watchItemDao()
    )

    val okxCredentialsRepository: OkxCredentialsRepository = DefaultOkxCredentialsRepository(
        context = appContext
    )

    val aiConfigRepository: AiConfigRepository = DefaultAiConfigRepository(
        context = appContext
    )

    val marketSearchRepository: MarketSearchRepository = DefaultMarketSearchRepository(
        alphaApi = networkFactory.alphaApi,
        binanceApi = networkFactory.binanceApi,
        okxApi = networkFactory.okxApi,
        okxOnChainApi = networkFactory.okxOnChainApi,
        okxCredentialsProvider = { okxCredentialsRepository.getCredentials() }
    )

    val marketQuoteRepository: MarketQuoteRepository = DefaultMarketQuoteRepository(
        alphaApi = networkFactory.alphaApi,
        binanceApi = networkFactory.binanceApi,
        okxApi = networkFactory.okxApi,
        okxOnChainApi = networkFactory.okxOnChainApi,
        okxCredentialsProvider = { okxCredentialsRepository.getCredentials() }
    )

    val marketKlineRepository: MarketKlineRepository = DefaultMarketKlineRepository(
        alphaApi = networkFactory.alphaApi,
        binanceApi = networkFactory.binanceApi,
        okxApi = networkFactory.okxApi,
        okxOnChainApi = networkFactory.okxOnChainApi,
        okxCredentialsProvider = { okxCredentialsRepository.getCredentials() }
    )

    val aiChatRepository: AiChatRepository = DefaultAiChatRepository(
        aiConfigRepository = aiConfigRepository
    )

    val globalQuoteRefreshCoordinator = GlobalQuoteRefreshCoordinator(
        scope = appScope,
        watchlistRepository = watchlistRepository,
        quoteRepository = quoteRepository,
        appPreferencesRepository = appPreferencesRepository,
        marketQuoteRepository = marketQuoteRepository,
        okxCredentialsProvider = { okxCredentialsRepository.getCredentials() },
        networkLogRepository = networkLogRepository
    )
}
