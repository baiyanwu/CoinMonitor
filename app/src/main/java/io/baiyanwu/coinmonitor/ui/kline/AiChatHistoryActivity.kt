package io.baiyanwu.coinmonitor.ui.kline

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import io.baiyanwu.coinmonitor.ui.CoinMonitorComposeActivity
import io.baiyanwu.coinmonitor.ui.navigation.DetailPageTransitions

class AiChatHistoryActivity : CoinMonitorComposeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setCoinMonitorContent { container ->
            AiChatHistoryRoute(
                container = container,
                onBack = { finish() },
                onSelectSession = { sessionId ->
                    container.aiChatSessionSelectionStore.select(sessionId)
                    finish()
                }
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
                intent = Intent(activity, AiChatHistoryActivity::class.java)
            )
        }
    }
}
