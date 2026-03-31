package io.baiyanwu.coinmonitor.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.ui.home.HomeRoute
import io.baiyanwu.coinmonitor.ui.kline.KlineRoute
import io.baiyanwu.coinmonitor.ui.settings.SettingsRoute
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorComponentDefaults
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens

private object Destinations {
    const val HOME = "home"
    const val KLINE = "kline"
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
    onOpenSearch: () -> Unit,
    onOpenKlineSearch: () -> Unit,
    onOpenKlineIndicatorSettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenThirdPartyApiSettings: () -> Unit,
    onOpenNetworkLog: () -> Unit
) {
    val navController = rememberNavController()
    val tabs = remember {
        listOf(
            MainTab(Destinations.HOME, R.string.tab_home, Icons.Rounded.Home),
            MainTab(Destinations.KLINE, R.string.tab_kline, Icons.Rounded.ShowChart),
            MainTab(Destinations.SETTINGS, R.string.tab_settings, Icons.Rounded.Settings)
        )
    }
    val navigateToTopLevel: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val colors = CoinMonitorThemeTokens.colors

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
            navController = navController,
            startDestination = Destinations.HOME,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            composable(Destinations.HOME) {
                HomeRoute(
                    container = container,
                    onNavigateSearch = onOpenSearch,
                    onNavigateOverlaySettings = onOpenOverlaySettings,
                    onNavigateKline = { itemId ->
                        container.klineSelectionStore.select(itemId)
                        navigateToTopLevel(Destinations.KLINE)
                    }
                )
            }
            composable(Destinations.KLINE) {
                KlineRoute(
                    container = container,
                    onOpenSearch = onOpenKlineSearch,
                    onOpenIndicatorSettings = onOpenKlineIndicatorSettings
                )
            }
            composable(Destinations.SETTINGS) {
                SettingsRoute(
                    container = container,
                    onNavigateOverlaySettings = onOpenOverlaySettings,
                    onNavigateThirdPartyApiSettings = onOpenThirdPartyApiSettings,
                    onNavigateNetworkLog = onOpenNetworkLog
                )
            }
        }
    }
}
