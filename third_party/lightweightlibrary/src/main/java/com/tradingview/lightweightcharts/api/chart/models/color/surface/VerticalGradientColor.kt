package com.tradingview.lightweightcharts.api.chart.models.color.surface

import androidx.annotation.ColorInt
import com.tradingview.lightweightcharts.api.chart.models.color.IntColor

class VerticalGradientColor : SurfaceColor {

    constructor(topColor: IntColor, bottomColor: IntColor) {
        this.topColor = topColor
        this.bottomColor = bottomColor
    }

    constructor(@ColorInt topColor: Int, @ColorInt bottomColor: Int) {
        this.topColor = IntColor(topColor)
        this.bottomColor = IntColor(bottomColor)
    }

    override val type: SurfaceColor.ColorType = SurfaceColor.ColorType.VERTICAL_GRADIENT

    val topColor: IntColor

    val bottomColor: IntColor
}
