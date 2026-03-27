package io.baiyanwu.coinmonitor.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.baiyanwu.coinmonitor.ui.CoinMonitorComposeActivity
import io.baiyanwu.coinmonitor.ui.navigation.DetailPageTransitions
import io.baiyanwu.coinmonitor.overlay.OverlayPermissionHelper
import io.baiyanwu.coinmonitor.overlay.OverlayServiceController

class OverlaySettingsActivity : CoinMonitorComposeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setCoinMonitorContent { container ->
            OverlaySettingsActivityContent(
                container = container,
                onBack = { finish() }
            )
        }
    }

    override fun finish() {
        super.finish()
        DetailPageTransitions.finish(this)
    }

    companion object {
        fun start(activity: Activity) {
            DetailPageTransitions.start(
                activity = activity,
                intent = Intent(activity, OverlaySettingsActivity::class.java)
            )
        }
    }
}

@Composable
private fun OverlaySettingsActivityContent(
    container: io.baiyanwu.coinmonitor.data.AppContainer,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var overlayPermissionGranted by remember { mutableStateOf(OverlayPermissionHelper.canDrawOverlays(context)) }
    var notificationPermissionGranted by remember {
        mutableStateOf(
            !OverlayPermissionHelper.requiresNotificationPermission() ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
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

    OverlaySettingsRoute(
        container = container,
        overlayPermissionGranted = overlayPermissionGranted,
        notificationPermissionGranted = notificationPermissionGranted,
        onBack = onBack,
        onRequestOverlayPermission = {
            overlayPermissionLauncher.launch(
                OverlayPermissionHelper.createPermissionIntent(context.packageName)
            )
        },
        onRequestNotificationPermission = {
            if (OverlayPermissionHelper.requiresNotificationPermission() && !notificationPermissionGranted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        onStartOverlay = { OverlayServiceController.start(context) },
        onStopOverlay = { OverlayServiceController.stop(context) }
    )
}
