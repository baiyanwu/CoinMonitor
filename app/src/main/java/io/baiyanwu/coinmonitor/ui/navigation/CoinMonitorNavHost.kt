package io.baiyanwu.coinmonitor.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.ui.home.HomeRoute
import io.baiyanwu.coinmonitor.ui.kline.KlineRoute
import io.baiyanwu.coinmonitor.ui.kline.chart.KlineChartHostView
import io.baiyanwu.coinmonitor.ui.search.SearchMode
import io.baiyanwu.coinmonitor.ui.settings.SettingsRoute
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorComponentDefaults
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens
import io.baiyanwu.coinmonitor.ui.wallet.WalletAddRoute
import io.baiyanwu.coinmonitor.ui.wallet.WalletBackupRoute
import io.baiyanwu.coinmonitor.ui.wallet.WalletManageRoute
import io.baiyanwu.coinmonitor.ui.wallet.WalletPage
import io.baiyanwu.coinmonitor.ui.wallet.WalletReceiveRoute
import io.baiyanwu.coinmonitor.ui.wallet.WalletRoute
import io.baiyanwu.coinmonitor.ui.wallet.WalletSecurityRoute
import io.baiyanwu.coinmonitor.ui.wallet.WalletSendRoute

private object Destinations {
    const val MAIN = "main"
    const val HOME = "home"
    const val KLINE = "kline"
    const val WALLET = "wallet"
    const val WALLET_ADD = "wallet/add"
    const val WALLET_MANAGE = "wallet/manage"
    const val WALLET_RECEIVE = "wallet/receive"
    const val WALLET_SEND = "wallet/send"
    const val WALLET_BACKUP = "wallet/backup"
    const val WALLET_SECURITY = "wallet/security"
    const val SETTINGS = "settings"
}

private data class MainTab(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector
)

@Composable
fun CoinMonitorNavHost(
    container: AppContainer,
    onOpenSearch: (SearchMode) -> Unit,
    onOpenKlineSearch: () -> Unit,
    onOpenKlineHistory: () -> Unit,
    onOpenKlineIndicatorSettings: () -> Unit,
    onOpenOverlayItems: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenThirdPartyApiSettings: () -> Unit,
    onOpenWalletNetworkSettings: () -> Unit,
    onOpenWatchWallet: () -> Unit,
    onOpenNetworkLog: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val context = LocalContext.current
    val rootNavController = rememberNavController()
    val klineChartHostView = remember(context) { KlineChartHostView(context) }
    DisposableEffect(klineChartHostView) {
        onDispose {
            klineChartHostView.release()
        }
    }
    val walletRouteForPage: (WalletPage) -> String = { page ->
        when (page) {
            WalletPage.ADD -> Destinations.WALLET_ADD
            WalletPage.MANAGE -> Destinations.WALLET_MANAGE
            WalletPage.RECEIVE -> Destinations.WALLET_RECEIVE
            WalletPage.SEND -> Destinations.WALLET_SEND
            WalletPage.BACKUP -> Destinations.WALLET_BACKUP
            WalletPage.SECURITY -> Destinations.WALLET_SECURITY
            WalletPage.HOME -> Destinations.MAIN
        }
    }
    val openWalletPage: (WalletPage) -> Unit = { page ->
        if (page == WalletPage.HOME) {
            rootNavController.popBackStack(Destinations.MAIN, inclusive = false)
        } else {
            rootNavController.navigate(walletRouteForPage(page)) {
                popUpTo(Destinations.MAIN) { inclusive = false }
                launchSingleTop = true
            }
        }
    }
    val finishWalletPage: () -> Unit = {
        rootNavController.popBackStack()
    }

    NavHost(
        navController = rootNavController,
        startDestination = Destinations.MAIN,
        modifier = Modifier.fillMaxSize(),
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None }
    ) {
        composable(Destinations.MAIN) {
            MainShell(
                container = container,
                klineChartHostView = klineChartHostView,
                onOpenSearch = onOpenSearch,
                onOpenKlineSearch = onOpenKlineSearch,
                onOpenKlineHistory = onOpenKlineHistory,
                onOpenKlineIndicatorSettings = onOpenKlineIndicatorSettings,
                onOpenOverlayItems = onOpenOverlayItems,
                onOpenOverlaySettings = onOpenOverlaySettings,
                onOpenThirdPartyApiSettings = onOpenThirdPartyApiSettings,
                onOpenWalletNetworkSettings = onOpenWalletNetworkSettings,
                onOpenWatchWallet = onOpenWatchWallet,
                onOpenNetworkLog = onOpenNetworkLog,
                onOpenAbout = onOpenAbout,
                onOpenWalletPage = openWalletPage
            )
        }
        walletDetailComposable(Destinations.WALLET_ADD) {
            WalletAddRoute(container, finishWalletPage, openWalletPage)
        }
        walletDetailComposable(Destinations.WALLET_MANAGE) {
            WalletManageRoute(container, finishWalletPage, openWalletPage)
        }
        walletDetailComposable(Destinations.WALLET_RECEIVE) {
            WalletReceiveRoute(container, finishWalletPage, openWalletPage)
        }
        walletDetailComposable(Destinations.WALLET_SEND) {
            WalletSendRoute(container, finishWalletPage, openWalletPage)
        }
        walletDetailComposable(Destinations.WALLET_BACKUP) {
            WalletBackupRoute(container, finishWalletPage, openWalletPage)
        }
        walletDetailComposable(Destinations.WALLET_SECURITY) {
            WalletSecurityRoute(container, finishWalletPage, openWalletPage)
        }
    }
}

@Composable
private fun MainShell(
    container: AppContainer,
    klineChartHostView: KlineChartHostView,
    onOpenSearch: (SearchMode) -> Unit,
    onOpenKlineSearch: () -> Unit,
    onOpenKlineHistory: () -> Unit,
    onOpenKlineIndicatorSettings: () -> Unit,
    onOpenOverlayItems: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenThirdPartyApiSettings: () -> Unit,
    onOpenWalletNetworkSettings: () -> Unit,
    onOpenWatchWallet: () -> Unit,
    onOpenNetworkLog: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenWalletPage: (WalletPage) -> Unit
) {
    val mainNavController = rememberNavController()
    val tabs = remember {
        listOf(
            MainTab(Destinations.HOME, R.string.tab_home, Icons.Rounded.Home),
            MainTab(Destinations.WALLET, R.string.tab_wallet, Icons.Rounded.AccountBalanceWallet),
            MainTab(Destinations.SETTINGS, R.string.tab_settings, Icons.Rounded.Settings)
        )
    }
    val navBackStackEntry by mainNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val colors = CoinMonitorThemeTokens.colors
    val navigateToTopLevel: (String) -> Unit = { route ->
        mainNavController.navigate(route) {
            popUpTo(mainNavController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = colors.pageBackground,
        bottomBar = {
            NavigationBar(
                containerColor = colors.cardBackground,
                contentColor = colors.secondaryText
            ) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = { navigateToTopLevel(tab.route) },
                        colors = CoinMonitorComponentDefaults.navigationBarItemColors(),
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = stringResource(tab.labelRes)
                            )
                        },
                        label = { Text(stringResource(tab.labelRes)) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = mainNavController,
            startDestination = Destinations.HOME,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            composable(Destinations.HOME) {
                HomeRoute(
                    container = container,
                    contentTopInset = innerPadding.calculateTopPadding(),
                    contentBottomInset = innerPadding.calculateBottomPadding(),
                    onNavigateSearch = onOpenSearch,
                    onNavigateOverlayItems = onOpenOverlayItems,
                    onNavigateOverlaySettings = onOpenOverlaySettings
                )
            }
            composable(Destinations.KLINE) {
                KlineRoute(
                    container = container,
                    chartHostView = klineChartHostView,
                    contentTopInset = innerPadding.calculateTopPadding(),
                    contentBottomInset = innerPadding.calculateBottomPadding(),
                    onOpenSearch = onOpenKlineSearch,
                    onOpenHistory = onOpenKlineHistory,
                    onOpenIndicatorSettings = onOpenKlineIndicatorSettings,
                    onOpenThirdPartyApiSettings = onOpenThirdPartyApiSettings
                )
            }
            composable(Destinations.SETTINGS) {
                SettingsRoute(
                    container = container,
                    contentTopInset = innerPadding.calculateTopPadding(),
                    contentBottomInset = innerPadding.calculateBottomPadding(),
                    onNavigateOverlaySettings = onOpenOverlaySettings,
                    onNavigateThirdPartyApiSettings = onOpenThirdPartyApiSettings,
                    onNavigateNetworkLog = onOpenNetworkLog,
                    onNavigateAbout = onOpenAbout
                )
            }
            composable(Destinations.WALLET) {
                WalletRoute(
                    container = container,
                    contentTopInset = innerPadding.calculateTopPadding(),
                    contentBottomInset = innerPadding.calculateBottomPadding(),
                    onOpenWatchWallet = onOpenWatchWallet,
                    onOpenAssetSettings = onOpenThirdPartyApiSettings,
                    onOpenNetworkSettings = onOpenWalletNetworkSettings,
                    onOpenPage = onOpenWalletPage
                )
            }
        }
    }
}

private fun NavGraphBuilder.walletDetailComposable(
    route: String,
    content: @Composable () -> Unit
) {
    composable(
        route = route,
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(
                    durationMillis = 300,
                    easing = FastOutSlowInEasing
                )
            )
        },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(
                    durationMillis = 300,
                    easing = FastOutSlowInEasing
                )
            )
        }
    ) {
        content()
    }
}
