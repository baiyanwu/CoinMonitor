package io.baiyanwu.coinmonitor.ui.navigation

import android.app.Activity
import android.content.Intent
import io.baiyanwu.coinmonitor.R

/**
 * 二级页面统一走原生 Activity 转场，和底部导航容器解耦，避免再触发内容区重排。
 */
object DetailPageTransitions {
    fun start(activity: Activity, intent: Intent) {
        activity.startActivity(intent)
        activity.overrideTransition(
            enterAnim = R.anim.page_enter_from_right,
            exitAnim = R.anim.page_stay
        )
    }

    fun finish(activity: Activity) {
        activity.overrideTransition(
            enterAnim = R.anim.page_stay,
            exitAnim = R.anim.page_exit_to_right
        )
    }

    @Suppress("DEPRECATION")
    private fun Activity.overrideTransition(
        enterAnim: Int,
        exitAnim: Int
    ) {
        overridePendingTransition(enterAnim, exitAnim)
    }
}
