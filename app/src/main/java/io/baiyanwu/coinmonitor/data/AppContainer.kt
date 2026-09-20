package io.baiyanwu.coinmonitor.data

import android.content.Context
import androidx.room.Room
import androidx.lifecycle.ProcessLifecycleOwner
import io.baiyanwu.coinmonitor.data.local.CoinMonitorDatabase
import io.baiyanwu.coinmonitor.data.ai.AppAnalysisHost
import io.baiyanwu.coinmonitor.data.ai.market.BinanceAnnouncementAdapter
import io.baiyanwu.coinmonitor.data.ai.market.OkxAnnouncementAdapter
import io.baiyanwu.coinmonitor.data.ai.OpenAiCompatibleStreamingClient
import io.baiyanwu.coinmonitor.data.ai.market.ProjectInfoAdapter
import io.baiyanwu.coinmonitor.data.refresh.GlobalQuoteRefreshCoordinator
import io.baiyanwu.coinmonitor.data.network.NetworkFactory
import io.baiyanwu.coinmonitor.data.update.GitHubReleaseUpdateChecker
import io.baiyanwu.coinmonitor.data.repository.DefaultAiChatRepository
import io.baiyanwu.coinmonitor.data.repository.DefaultAiConfigRepository
import io.baiyanwu.coinmonitor.data.repository.DefaultAppPreferencesRepository
import io.baiyanwu.coinmonitor.data.repository.DefaultMarketKlineRepository
import io.baiyanwu.coinmonitor.data.repository.DefaultMarketQuoteRepository
import io.baiyanwu.coinmonitor.data.repository.DefaultMarketSearchRepository
import io.baiyanwu.coinmonitor.data.repository.DexScreenerOnchainMarketProvider
import io.baiyanwu.coinmonitor.data.repository.OkxDexOnchainMarketProvider
import io.baiyanwu.coinmonitor.data.repository.OnchainProviderRouter
import io.baiyanwu.coinmonitor.data.repository.DefaultNetworkLogRepository
import io.baiyanwu.coinmonitor.data.repository.DefaultOkxWalletCredentialsRepository
import io.baiyanwu.coinmonitor.data.repository.DefaultOverlayRepository
import io.baiyanwu.coinmonitor.data.repository.DefaultWalletPortfolioRepository
import io.baiyanwu.coinmonitor.data.repository.DefaultWalletPortfolioCacheRepository
import io.baiyanwu.coinmonitor.data.repository.DefaultWalletNetworkSettingsRepository
import io.baiyanwu.coinmonitor.data.repository.DefaultWalletVaultRepository
import io.baiyanwu.coinmonitor.data.repository.DefaultSelfCustodyWalletRepository
import io.baiyanwu.coinmonitor.data.repository.DefaultWalletWatchPreferencesRepository
import io.baiyanwu.coinmonitor.data.repository.DefaultWatchlistRepository
import io.baiyanwu.coinmonitor.data.repository.InMemoryQuoteRepository
import io.baiyanwu.coinmonitor.data.repository.migrateLegacyOnchainSettings
import io.baiyanwu.coinmonitor.data.repository.createOverlayPreferencesDataStore
import io.baiyanwu.coinmonitor.data.repository.migrateLegacyOverlaySettings
import io.baiyanwu.coinmonitor.data.repository.migrateLegacyOverlaySettingsBeforeRoomOpen
import io.baiyanwu.coinmonitor.domain.repository.AppPreferencesRepository
import io.baiyanwu.coinmonitor.domain.repository.AiChatRepository
import io.baiyanwu.coinmonitor.domain.repository.AiConfigRepository
import io.baiyanwu.coinmonitor.domain.repository.MarketKlineRepository
import io.baiyanwu.coinmonitor.domain.repository.MarketQuoteRepository
import io.baiyanwu.coinmonitor.domain.repository.MarketSearchRepository
import io.baiyanwu.coinmonitor.domain.repository.NetworkLogRepository
import io.baiyanwu.coinmonitor.domain.repository.OverlayRepository
import io.baiyanwu.coinmonitor.domain.repository.QuoteRepository
import io.baiyanwu.coinmonitor.domain.repository.WatchlistRepository
import io.baiyanwu.coinmonitor.lib.agents.AnalysisService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.concurrent.TimeUnit
import io.baiyanwu.coinmonitor.data.wallet.WalletSessionLockObserver

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    init {
        migrateLegacyOnchainSettings(appContext)
        migrateLegacyOverlaySettingsBeforeRoomOpen(appContext)
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val klineSelectionStore = KlineSelectionStore()
    val aiChatSessionSelectionStore = AiChatSessionSelectionStore()

    private val database = Room.databaseBuilder(
        appContext,
        CoinMonitorDatabase::class.java,
        "coin_monitor.db"
    ).addMigrations(
        CoinMonitorDatabase.MIGRATION_4_5,
        CoinMonitorDatabase.MIGRATION_5_6,
        CoinMonitorDatabase.MIGRATION_6_7,
        CoinMonitorDatabase.migration7To8(
            context = appContext,
            migrateOverlaySettings = ::migrateLegacyOverlaySettings
        ),
        CoinMonitorDatabase.MIGRATION_8_9,
        CoinMonitorDatabase.MIGRATION_9_10,
        CoinMonitorDatabase.MIGRATION_10_11
    ).build()

    val networkLogRepository: NetworkLogRepository = DefaultNetworkLogRepository()

    private val networkFactory = NetworkFactory(
        networkLogRepository = networkLogRepository
    )
    val appUpdateChecker = GitHubReleaseUpdateChecker(
        httpClient = networkFactory.okHttpClient.newBuilder()
            .callTimeout(10, TimeUnit.SECONDS)
            .build()
    )
    private val dexScreenerClient = io.baiyanwu.coinmonitor.data.network.DexScreenerClient(
        networkFactory.dexScreenerApi
    )
    private val geckoTerminalClient = io.baiyanwu.coinmonitor.data.network.GeckoTerminalClient(
        networkFactory.geckoTerminalApi
    )
    val clipboardSettingsStore = io.baiyanwu.coinmonitor.clipboard.ClipboardSettingsStore(appContext)
    val clipboardRepository = io.baiyanwu.coinmonitor.clipboard.ClipboardRepository(dexScreenerClient, geckoTerminalClient)
    val appPreferencesRepository: AppPreferencesRepository = DefaultAppPreferencesRepository(
        context = appContext
    )

    val quoteRepository: QuoteRepository = InMemoryQuoteRepository()

    val watchlistRepository: WatchlistRepository = DefaultWatchlistRepository(
        database = database
    )

    private val overlayPreferences = createOverlayPreferencesDataStore(
        context = appContext,
        scope = appScope
    )

    val overlayRepository: OverlayRepository = DefaultOverlayRepository(
        context = appContext,
        overlayPreferences = overlayPreferences,
        database = database
    )

    val aiConfigRepository: AiConfigRepository = DefaultAiConfigRepository(
        context = appContext
    )

    val okxWalletCredentialsRepository = DefaultOkxWalletCredentialsRepository(appContext)
    private val okxDexMarketClient = io.baiyanwu.coinmonitor.data.network.OkxDexMarketClient(
        httpClient = networkFactory.okHttpClient,
        credentialsProvider = okxWalletCredentialsRepository::getCredentials
    )
    private val onchainMarketProviders = listOf(
        DexScreenerOnchainMarketProvider(dexScreenerClient),
        OkxDexOnchainMarketProvider(okxDexMarketClient)
    )
    private val onchainProviderRouter = OnchainProviderRouter(
        providers = onchainMarketProviders,
        providerOrder = { appPreferencesRepository.getPreferences().onchainProviderOrder },
        routingState = combine(
            appPreferencesRepository.observePreferences(),
            okxWalletCredentialsRepository.observeCredentials()
        ) { preferences, credentials ->
            "${preferences.onchainProviderOrder.joinToString(",") { it.name }}:${credentials.isReady}"
        }.distinctUntilChanged()
    )
    val walletWatchPreferencesRepository = DefaultWalletWatchPreferencesRepository(appContext)
    val walletPortfolioCacheRepository = DefaultWalletPortfolioCacheRepository(appContext)
    val walletPortfolioRepository = DefaultWalletPortfolioRepository(
        client = io.baiyanwu.coinmonitor.data.network.OkxWalletClient(
            httpClient = networkFactory.okHttpClient,
            credentialsProvider = okxWalletCredentialsRepository::getCredentials
        )
    )
    val walletVaultRepository = DefaultWalletVaultRepository(appContext)
    val walletNetworkSettingsRepository = DefaultWalletNetworkSettingsRepository(
        context = appContext,
        httpClient = networkFactory.okHttpClient
    )
    val selfCustodyWalletRepository = DefaultSelfCustodyWalletRepository(
        context = appContext,
        networkSettings = walletNetworkSettingsRepository,
        okxCredentials = okxWalletCredentialsRepository,
        httpClient = networkFactory.okHttpClient
    )
    val walletSessionLockObserver = WalletSessionLockObserver(walletVaultRepository).also {
        ProcessLifecycleOwner.get().lifecycle.addObserver(it)
    }

    val marketSearchRepository: MarketSearchRepository = DefaultMarketSearchRepository(
        alphaApi = networkFactory.alphaApi,
        binanceApi = networkFactory.binanceApi,
        binanceFuturesApi = networkFactory.binanceFuturesApi,
        okxApi = networkFactory.okxApi,
        onchainRouter = onchainProviderRouter
    )

    val marketQuoteRepository: MarketQuoteRepository = DefaultMarketQuoteRepository(
        alphaApi = networkFactory.alphaApi,
        binanceApi = networkFactory.binanceApi,
        binanceFuturesApi = networkFactory.binanceFuturesApi,
        okxApi = networkFactory.okxApi,
        onchainRouter = onchainProviderRouter
    )

    val marketKlineRepository: MarketKlineRepository = DefaultMarketKlineRepository(
        alphaApi = networkFactory.alphaApi,
        binanceApi = networkFactory.binanceApi,
        binanceFuturesApi = networkFactory.binanceFuturesApi,
        okxApi = networkFactory.okxApi,
        dexScreenerClient = dexScreenerClient,
        geckoTerminalClient = geckoTerminalClient,
        watchlistRepository = watchlistRepository
    )

    private val analysisHost = AppAnalysisHost(
        analysisService = AnalysisService(
            marketSourceAdapters = listOf(
                BinanceAnnouncementAdapter(networkFactory.okHttpClient),
                OkxAnnouncementAdapter(networkFactory.okHttpClient),
                ProjectInfoAdapter(
                    dexScreenerClient = dexScreenerClient
                )
            )
        )
    )

    val aiChatRepository: AiChatRepository = DefaultAiChatRepository(
        aiConfigRepository = aiConfigRepository,
        analysisHost = analysisHost,
        aiChatDao = database.aiChatDao(),
        streamingClient = OpenAiCompatibleStreamingClient(
            okHttpClient = networkFactory.okHttpClient.newBuilder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .callTimeout(0, TimeUnit.SECONDS)
                .build()
        )
    )

    val globalQuoteRefreshCoordinator = GlobalQuoteRefreshCoordinator(
        scope = appScope,
        watchlistRepository = watchlistRepository,
        quoteRepository = quoteRepository,
        appPreferencesRepository = appPreferencesRepository,
        marketQuoteRepository = marketQuoteRepository,
        onchainProviderRouter = onchainProviderRouter,
        networkLogRepository = networkLogRepository
    )
}
