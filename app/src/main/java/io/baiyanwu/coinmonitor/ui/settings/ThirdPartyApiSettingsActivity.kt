package io.baiyanwu.coinmonitor.ui.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import io.baiyanwu.coinmonitor.ui.CoinMonitorComposeActivity
import io.baiyanwu.coinmonitor.ui.navigation.DetailPageTransitions

class ThirdPartyApiSettingsActivity : CoinMonitorComposeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setCoinMonitorContent { container ->
            ThirdPartyApiSettingsRoute(
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
                intent = Intent(activity, ThirdPartyApiSettingsActivity::class.java)
            )
        }
    }
}

