package com.tradingview.lightweightcharts

import android.util.Log
import com.tradingview.lightweightcharts.runtime.messaging.LogLevel

object Logger {
    private const val TAG = "[LIGHTWEIGHT_CHARTS]"

    var level = LogLevel.WARNING

    fun d(source: Any) {
        if (level.isDebug()) {
            Log.d(TAG, source.toString())
        }
    }

    fun w(source: Any) {
        if (level.isWarning()) {
            Log.w(TAG, source.toString())
        }
    }

    fun e(source: Any) {
        if (level.isError()) {
            Log.e(TAG, source.toString())
        }
    }
}
