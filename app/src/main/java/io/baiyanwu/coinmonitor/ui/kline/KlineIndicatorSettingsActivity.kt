package io.baiyanwu.coinmonitor.ui.kline

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import io.baiyanwu.coinmonitor.ui.CoinMonitorComposeActivity
import io.baiyanwu.coinmonitor.ui.navigation.DetailPageTransitions

/**
 * K 线指标配置页 Activity。
 *
 * 这里独立成二级页面，避免在 K 线主页面里继续堆叠弹层和复杂状态，
 * 也方便后续把更多图表参数继续扩展到同一页面。
 */
class KlineIndicatorSettingsActivity : CoinMonitorComposeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setCoinMonitorContent { container ->
            KlineIndicatorSettingsRoute(
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

        /**
         * 打开 K 线指标配置页。
         */
        fun start(activity: Activity) {
            DetailPageTransitions.start(
                activity = activity,
                intent = Intent(activity, KlineIndicatorSettingsActivity::class.java)
            )
        }
    }
}
