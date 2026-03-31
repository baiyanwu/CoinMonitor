package com.tradingview.lightweightcharts.api.chart.models.color.surface

import androidx.annotation.ColorInt
import com.tradingview.lightweightcharts.api.chart.models.color.IntColor

class SolidColor : SurfaceColor {

    constructor(color: IntColor) {
        this.color = color
    }

    constructor(@ColorInt color: Int) {
        this.color = IntColor(color)
    }

    override val type: SurfaceColor.ColorType = SurfaceColor.ColorType.SOLID

    val color: IntColor
}
