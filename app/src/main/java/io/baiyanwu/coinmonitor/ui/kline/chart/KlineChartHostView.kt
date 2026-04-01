package io.baiyanwu.coinmonitor.ui.kline.chart

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/**
 * K 线图宿主视图。
 *
 * 页面层只和这个宿主交互，不直接依赖 TradingView 的具体类型，
 * 这样未来切换到自研图表引擎时，只需要替换内部 engine 实现即可。
 */
class KlineChartHostView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val engine: KlineChartEngine = KlineChartEngineFactory.create(context)
    private var released = false

    init {
        addView(
            engine.view,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
    }

    /**
     * 把页面层组装好的统一模型交给具体图表引擎渲染。
     */
    fun render(model: KlineChartRenderModel) {
        if (released) return
        engine.render(model)
    }

    fun release() {
        if (released) return
        released = true
        engine.release()
    }
}
