package io.baiyanwu.coinmonitor.ui.walletwatch

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import io.baiyanwu.coinmonitor.ui.CoinMonitorComposeActivity
import io.baiyanwu.coinmonitor.ui.navigation.DetailPageTransitions
import io.baiyanwu.coinmonitor.ui.settings.ThirdPartyApiSettingsActivity

class WalletWatchActivity : CoinMonitorComposeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setCoinMonitorContent { container ->
            WalletWatchRoute(container, onBack = ::finish, onOpenSettings = { ThirdPartyApiSettingsActivity.start(this) })
        }
    }

    override fun finish() { super.finish(); DetailPageTransitions.finish(this) }

    companion object {
        fun start(activity: Activity) = DetailPageTransitions.start(activity, Intent(activity, WalletWatchActivity::class.java))
    }
}
