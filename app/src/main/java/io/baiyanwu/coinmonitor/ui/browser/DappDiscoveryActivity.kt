package io.baiyanwu.coinmonitor.ui.browser

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.ui.CoinMonitorComposeActivity
import io.baiyanwu.coinmonitor.ui.components.CoinMonitorIcons
import io.baiyanwu.coinmonitor.ui.components.CoinMonitorSmallIconButton
import io.baiyanwu.coinmonitor.ui.navigation.DetailPageTransitions
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorDesignTheme
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens

/** 钱包的二级目录页；只展示发现内容，WebView 继续由独立浏览器持有。 */
class DappDiscoveryActivity : CoinMonitorComposeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setCoinMonitorContent { container ->
            CoinMonitorDesignTheme {
                val design = CoinMonitorThemeTokens.design
                Scaffold(
                    containerColor = design.colors.pageBackground,
                    contentWindowInsets = WindowInsets(0),
                    topBar = {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = design.spacing.inline),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CoinMonitorSmallIconButton(CoinMonitorIcons.Back, stringResource(R.string.back), onClick = ::finish)
                            Text(stringResource(R.string.wallet_dapp_browser), style = design.typography.rowTitle)
                        }
                    }
                ) { padding ->
                    DappDiscoveryRoute(
                        container = container,
                        contentTopInset = padding.calculateTopPadding(),
                        contentBottomInset = padding.calculateBottomPadding(),
                        onOpenBrowser = { url -> DappBrowserActivity.start(this@DappDiscoveryActivity, url) }
                    )
                }
            }
        }
    }

    override fun finish() {
        super.finish()
        DetailPageTransitions.finish(this)
    }

    companion object {
        fun start(activity: Activity) = DetailPageTransitions.start(activity, Intent(activity, DappDiscoveryActivity::class.java))
    }
}
