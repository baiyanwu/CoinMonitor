package com.tradingview.lightweightcharts.api.delegates

import android.graphics.Bitmap
import com.tradingview.lightweightcharts.api.chart.models.ImageMimeType
import com.tradingview.lightweightcharts.api.interfaces.ChartApi
import com.tradingview.lightweightcharts.api.interfaces.ChartApi.Func.APPLY_OPTIONS
import com.tradingview.lightweightcharts.api.interfaces.ChartApi.Func.CHART_OPTIONS
import com.tradingview.lightweightcharts.api.interfaces.ChartApi.Func.PRICE_SCALE
import com.tradingview.lightweightcharts.api.interfaces.ChartApi.Func.REMOVE
import com.tradingview.lightweightcharts.api.interfaces.ChartApi.Func.REMOVE_SERIES
import com.tradingview.lightweightcharts.api.interfaces.ChartApi.Func.SUBSCRIBE_CROSSHAIR_MOVE
import com.tradingview.lightweightcharts.api.interfaces.ChartApi.Func.SUBSCRIBE_ON_CLICK
import com.tradingview.lightweightcharts.api.interfaces.ChartApi.Func.TAKE_SCREENSHOT
import com.tradingview.lightweightcharts.api.interfaces.ChartApi.Params.MIME
import com.tradingview.lightweightcharts.api.interfaces.ChartApi.Params.OPTIONS
import com.tradingview.lightweightcharts.api.interfaces.PriceScaleApi
import com.tradingview.lightweightcharts.api.interfaces.PriceScaleApi.Params.PRICE_SCALE_ID
import com.tradingview.lightweightcharts.api.interfaces.SeriesApi
import com.tradingview.lightweightcharts.api.interfaces.SeriesApi.Func.ADD_AREA_SERIES
import com.tradingview.lightweightcharts.api.interfaces.SeriesApi.Func.ADD_BAR_SERIES
import com.tradingview.lightweightcharts.api.interfaces.SeriesApi.Func.ADD_BASELINE_SERIES
import com.tradingview.lightweightcharts.api.interfaces.SeriesApi.Func.ADD_CANDLESTICK_SERIES
import com.tradingview.lightweightcharts.api.interfaces.SeriesApi.Func.ADD_HISTOGRAM_SERIES
import com.tradingview.lightweightcharts.api.interfaces.SeriesApi.Func.ADD_LINE_SERIES
import com.tradingview.lightweightcharts.api.interfaces.SeriesApi.Params.SERIES_UUID
import com.tradingview.lightweightcharts.api.interfaces.TimeScaleApi
import com.tradingview.lightweightcharts.api.options.common.BaselineStyleOptions
import com.tradingview.lightweightcharts.api.options.models.*
import com.tradingview.lightweightcharts.api.serializer.*
import com.tradingview.lightweightcharts.api.series.models.*
import com.tradingview.lightweightcharts.runtime.controller.WebMessageController
import com.tradingview.lightweightcharts.runtime.version.ChartRuntimeObject

class ChartApiDelegate(
    private val controller: WebMessageController
) : ChartApi, ChartRuntimeObject {

    override fun getVersion(): Int {
        return controller.hashCode()
    }

    override val timeScale: TimeScaleApi = TimeScaleApiDelegate(controller)

    override fun subscribeCrosshairMove(onCrosshairMoved: (params: MouseEventParams) -> Unit) {
        controller.callSubscribe(
            SUBSCRIBE_CROSSHAIR_MOVE,
            callback = onCrosshairMoved,
            deserializer = MouseEventParamsDeserializer()
        )
    }

    override fun unsubscribeCrosshairMove(onCrosshairMoved: (params: MouseEventParams) -> Unit) {
        controller.callUnsubscribe(
            SUBSCRIBE_CROSSHAIR_MOVE,
            subscription = onCrosshairMoved
        )
    }

    override fun subscribeClick(onClicked: (params: MouseEventParams) -> Unit) {
        controller.callSubscribe(
            SUBSCRIBE_ON_CLICK,
            callback = onClicked,
            deserializer = MouseEventParamsDeserializer()
        )
    }

    override fun unsubscribeClick(onClicked: (params: MouseEventParams) -> Unit) {
        controller.callUnsubscribe(
            SUBSCRIBE_ON_CLICK,
            subscription = onClicked
        )
    }

    override fun addAreaSeries(
        options: AreaSeriesOptions,
        onSeriesCreated: (api: SeriesApi) -> Unit
    ) {
        controller.callFunction(
            ADD_AREA_SERIES,
            mapOf(OPTIONS to options),
            { uuid ->
                onSeriesCreated(
                    SeriesApiDelegate(
                        uuid,
                        controller,
                        AreaSeriesOptionsDeserializer()
                    )
                )
            },
            PrimitiveSerializer.StringDeserializer
        )
    }

    override fun addBarSeries(
        options: BarSeriesOptions,
        onSeriesCreated: (api: SeriesApi) -> Unit
    ) {
        controller.callFunction(
            ADD_BAR_SERIES,
            mapOf(OPTIONS to options),
            { uuid ->
                onSeriesCreated(
                    SeriesApiDelegate(
                        uuid,
                        controller,
                        BarSeriesOptionsDeserializer()
                    )
                )
            },
            PrimitiveSerializer.StringDeserializer
        )
    }

    override fun addCandlestickSeries(
        options: CandlestickSeriesOptions,
        onSeriesCreated: (api: SeriesApi) -> Unit
    ) {
        controller.callFunction(
            ADD_CANDLESTICK_SERIES,
            mapOf(OPTIONS to options),
            { uuid ->
                onSeriesCreated(
                    SeriesApiDelegate(
                        uuid,
                        controller,
                        CandlestickSeriesOptionsDeserializer()
                    )
                )
            },
            PrimitiveSerializer.StringDeserializer
        )
    }

    override fun addHistogramSeries(
        options: HistogramSeriesOptions,
        onSeriesCreated: (api: SeriesApi) -> Unit
    ) {
        controller.callFunction(
            ADD_HISTOGRAM_SERIES,
            mapOf(OPTIONS to options),
            { uuid ->
                onSeriesCreated(
                    SeriesApiDelegate(
                        uuid,
                        controller,
                        HistogramSeriesOptionsDeserializer()
                    )
                )
            },
            PrimitiveSerializer.StringDeserializer
        )
    }

    override fun addLineSeries(
        options: LineSeriesOptions,
        onSeriesCreated: (api: SeriesApi) -> Unit
    ) {
        controller.callFunction(
            ADD_LINE_SERIES,
            mapOf(OPTIONS to options),
            { uuid ->
                onSeriesCreated(
                    SeriesApiDelegate(
                        uuid,
                        controller,
                        LineSeriesOptionsDeserializer()
                    )
                )
            },
            PrimitiveSerializer.StringDeserializer
        )
    }

    override fun addBaselineSeries(
        options: BaselineStyleOptions,
        onSeriesCreated: (api: SeriesApi) -> Unit
    ) {
        controller.callFunction(
            ADD_BASELINE_SERIES,
            mapOf(OPTIONS to options),
            { uuid ->
                onSeriesCreated(
                    SeriesApiDelegate(
                        uuid,
                        controller,
                        LineSeriesOptionsDeserializer()
                    )
                )
            },
            PrimitiveSerializer.StringDeserializer
        )
    }

    override fun remove() {
        controller.callFunction(REMOVE)
    }

    override fun removeSeries(seriesApi: SeriesApi, onSeriesDeleted: () -> Unit) {
        if (seriesApi is ChartRuntimeObject) {
            assert(seriesApi.getVersion() == getVersion()) {
                "The object should be removed by the same ChartApi as it was created"
            }
        }

        controller.callFunction(
            REMOVE_SERIES,
            mapOf(SERIES_UUID to seriesApi.uuid),
            onSeriesDeleted
        )
    }

    override fun priceScale(id: PriceScaleId): PriceScaleApi {
        val uuid = controller.callFunction(
            PRICE_SCALE,
            mapOf(PRICE_SCALE_ID to id.value)
        )
        return PriceScaleApiDelegate(uuid, controller)
    }

    @Deprecated(
        "Using ChartApi.priceScale() method without arguments " +
            "has been deprecated, pass valid price scale id instead"
    )
    override fun priceScale(): PriceScaleApi {
        val uuid = controller.callFunction(PRICE_SCALE)
        return PriceScaleApiDelegate(uuid, controller)
    }

    override fun applyOptions(options: ChartOptions) {
        controller.callFunction(
            APPLY_OPTIONS,
            mapOf(OPTIONS to options)
        )
    }

    override fun options(onOptionsReceived: (options: ChartOptions) -> Unit) {
        controller.callFunction(
            CHART_OPTIONS,
            callback = onOptionsReceived,
            deserializer = ChartOptionsDeserializer()
        )
    }

    override fun takeScreenshot(mimeType: ImageMimeType, onScreenshotReady: (Bitmap) -> Unit) {
        controller.callFunction(
            TAKE_SCREENSHOT,
            mapOf(MIME to mimeType),
            callback = onScreenshotReady,
            deserializer = BitmapDeserializer()
        )
    }
}
