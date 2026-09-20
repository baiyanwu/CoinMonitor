package io.baiyanwu.coinmonitor.ui.walletwatch

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.ui.CoinMonitorComposeActivity
import io.baiyanwu.coinmonitor.ui.navigation.DetailPageTransitions
import io.baiyanwu.coinmonitor.ui.settings.ThirdPartyApiSettingsActivity
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens

class WalletWatchActivity : CoinMonitorComposeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setCoinMonitorContent { container -> WalletWatchDetail(container) }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun WalletWatchDetail(container: AppContainer) {
        Column(Modifier.fillMaxSize()) {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.wallet_watch_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = ::finish) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = CoinMonitorThemeTokens.colors.pageBackground
                )
            )
            Box(Modifier.weight(1f)) {
                WalletWatchRoute(
                    container = container,
                    onOpenSettings = { ThirdPartyApiSettingsActivity.start(this@WalletWatchActivity) }
                )
            }
        }
    }

    override fun finish() {
        super.finish()
        DetailPageTransitions.finish(this)
    }

    companion object {
        fun start(activity: Activity) {
            DetailPageTransitions.start(activity, Intent(activity, WalletWatchActivity::class.java))
        }
    }
}
