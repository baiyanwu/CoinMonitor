package io.baiyanwu.coinmonitor.ui.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import io.baiyanwu.coinmonitor.ui.CoinMonitorComposeActivity
import io.baiyanwu.coinmonitor.ui.navigation.DetailPageTransitions

class NetworkLogActivity : CoinMonitorComposeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setCoinMonitorContent { container ->
            NetworkLogRoute(
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
        const val EXTRA_ENTRY_ID = "entry_id"

        fun start(activity: Activity) {
            DetailPageTransitions.start(
                activity = activity,
                intent = Intent(activity, NetworkLogActivity::class.java)
            )
        }

        fun startDetail(activity: Activity, entryId: Long) {
            DetailPageTransitions.start(
                activity = activity,
                intent = Intent(activity, NetworkLogActivity::class.java).apply {
                    putExtra(EXTRA_ENTRY_ID, entryId)
                }
            )
        }
    }
}
