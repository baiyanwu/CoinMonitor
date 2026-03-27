package io.baiyanwu.coinmonitor.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.overlay.OverlayPermissionHelper
import io.baiyanwu.coinmonitor.overlay.OverlayServiceController
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.ui.home.HomeRoute
import io.baiyanwu.coinmonitor.ui.search.SearchRoute
import io.baiyanwu.coinmonitor.ui.settings.OverlaySettingsRoute
import io.baiyanwu.coinmonitor.ui.settings.SettingsRoute
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorComponentDefaults
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens

private object Destinations {
    const val HOME = "home"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val OVERLAY_SETTINGS = "settings/overlay"
}

private data class MainTab(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector
)

@Composable
fun CoinMonitorNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var overlayPermissionGranted by remember { mutableStateOf(OverlayPermissionHelper.canDrawOverlays(context)) }
    var notificationPermissionGranted by remember {
        mutableStateOf(
            !OverlayPermissionHelper.requiresNotificationPermission() ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
    }
    val tabs = remember {
        listOf(
            MainTab(Destinations.HOME, R.string.tab_home, Icons.Rounded.Home),
            MainTab(Destinations.SETTINGS, R.string.tab_settings, Icons.Rounded.Settings)
        )
    }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        overlayPermissionGranted = OverlayPermissionHelper.canDrawOverlays(context)
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationPermissionGranted = granted
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayPermissionGranted = OverlayPermissionHelper.canDrawOverlays(context)
                notificationPermissionGranted =
                    !OverlayPermissionHelper.requiresNotificationPermission() ||
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val requestOverlayPermission = {
        overlayPermissionLauncher.launch(
            OverlayPermissionHelper.createPermissionIntent(context.packageName)
        )
    }
    val requestNotificationPermission = {
        if (OverlayPermissionHelper.requiresNotificationPermission() && !notificationPermissionGranted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
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
            if (currentRoute != Destinations.SEARCH && currentRoute != Destinations.OVERLAY_SETTINGS) {
                NavigationBar(
                    containerColor = colors.cardBackground,
                    contentColor = colors.secondaryText
                ) {
                    tabs.forEach { tab ->
                        val selected = when (tab.route) {
                            Destinations.SETTINGS -> currentRoute == Destinations.SETTINGS || currentRoute == Destinations.OVERLAY_SETTINGS
                            else -> currentRoute == tab.route
                        }
                        NavigationBarItem(
                            selected = selected,
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
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destinations.HOME,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { resolveEnterTransition() },
            exitTransition = { resolveExitTransition() },
            popEnterTransition = { resolvePopEnterTransition() },
            popExitTransition = { resolvePopExitTransition() }
        ) {
            composable(Destinations.HOME) {
                HomeRoute(
                    container = container,
                    onNavigateSearch = { navController.navigate(Destinations.SEARCH) }
                )
            }
            composable(Destinations.SEARCH) {
                SearchRoute(
                    container = container,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Destinations.SETTINGS) {
                SettingsRoute(
                    container = container,
                    onNavigateOverlaySettings = { navController.navigate(Destinations.OVERLAY_SETTINGS) }
                )
            }
            composable(Destinations.OVERLAY_SETTINGS) {
                OverlaySettingsRoute(
                    container = container,
                    overlayPermissionGranted = overlayPermissionGranted,
                    notificationPermissionGranted = notificationPermissionGranted,
                    onBack = { navController.popBackStack() },
                    onRequestOverlayPermission = requestOverlayPermission,
                    onRequestNotificationPermission = requestNotificationPermission,
                    onStartOverlay = { OverlayServiceController.start(context) },
                    onStopOverlay = { OverlayServiceController.stop(context) }
                )
            }
        }
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.resolveEnterTransition(): EnterTransition {
    val targetRoute = targetState.destination.route
    return when {
        targetRoute.isDetailRoute() -> {
            // 二级页面使用轻量缩放进入，避免滑动时同时看到前后两个页面。
            scaleIn(
                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                initialScale = 1.04f,
                transformOrigin = TransformOrigin(0.5f, 0f)
            )
        }

        else -> EnterTransition.None
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.resolveExitTransition(): ExitTransition {
    val targetRoute = targetState.destination.route
    return when {
        // 进入二级页时，底层页面不做退出动画，避免和新页面叠加展示。
        targetRoute.isDetailRoute() -> ExitTransition.None
        else -> ExitTransition.None
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.resolvePopEnterTransition(): EnterTransition {
    // 返回时目标页直接稳定接管，避免底层页提前露出。
    return EnterTransition.None
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.resolvePopExitTransition(): ExitTransition {
    return if (initialState.destination.route.isDetailRoute()) {
        scaleOut(
            animationSpec = tween(durationMillis = 220, easing = LinearOutSlowInEasing),
            targetScale = 1.04f,
            transformOrigin = TransformOrigin(0.5f, 0f)
        )
    } else {
        ExitTransition.None
    }
}

private fun String?.isDetailRoute(): Boolean {
    return this == Destinations.SEARCH || this == Destinations.OVERLAY_SETTINGS
}
