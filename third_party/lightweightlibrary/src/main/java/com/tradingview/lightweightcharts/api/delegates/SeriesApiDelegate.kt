package com.tradingview.lightweightcharts.api.delegates

import com.tradingview.lightweightcharts.api.interfaces.PaneApi
import com.tradingview.lightweightcharts.api.interfaces.PriceScaleApi
import com.tradingview.lightweightcharts.api.interfaces.SeriesApi
import com.tradingview.lightweightcharts.api.interfaces.SeriesApi.Func.APPLY_OPTIONS
import com.tradingview.lightweightcharts.api.interfaces.SeriesApi.Func.COORDINATE_TO_PRICE
import com.tradingview.lightweightcharts.api.interfaces.SeriesApi.Func.CREATE_PRICE_LINE
import com.tradingview.lightweightcharts.api.interfaces.SeriesApi.Func.DATA_BY_INDEX_SERIES
import com.tradingview.lightweightcharts.api.interfaces.SeriesApi.Func.GET_PANE
import com.tradingview.lightweightcharts.api.interfaces.SeriesApi.Func.GET_MARKERS_SERIES
import com.tradingview.lightweightcharts.api.interfaces.SeriesApi.Func.MOVE_TO_PANE
import com.tradingview.lightweightcharts.api.interfaces.SeriesApi.Func.PRICE_SCALE_SERIES
import com.tradingview.lightweightcharts.api.interfaces.SeriesApi.Func.PRICE_TO_COORDINATE
import com.tradingview.lightweightcharts.api.interfaces.SeriesApi.Func.REMOVE_PRICE_LINE
import com.tradingview.lightweightcharts.api.interfaces.SeriesApi.Func.SERIES_TYPE
import com.tradingview.lightweightcharts.api.interfaces.SeriesApi.Func.SET_MARKERS
import com.tradingview.lightweightcharts.api.interfaces.SeriesApi.Func.SET_SERIES
import com.tradingview.lightweightcharts.api.interfaces.SeriesApi.Func.UPDATE
import com.tradingview.lightweightcharts.api.interfaces.SeriesApi.Params.BAR
import com.tradingview.lightweightcharts.api.interfaces.SeriesApi.Params.COORDINATE
import com.tradingview.lightweightcharts.api.interfaces.SeriesApi.Params.DATA
import com.tradingview.lightweightcharts.api.interfaces.SeriesApi.Params.LINE_ID
import com.tradingview.lightweightcharts.api.interfaces.SeriesApi.Params.LOGICAL_INDEX
import com.tradingview.lightweightcharts.api.interfaces.SeriesApi.Params.MISMATCH_DIRECTION
import com.tradingview.lightweightcharts.api.interfaces.SeriesApi.Params.OPTIONS
import com.tradingview.lightweightcharts.api.interfaces.SeriesApi.Params.PANE_INDEX
import com.tradingview.lightweightcharts.api.interfaces.SeriesApi.Params.PRICE
import com.tradingview.lightweightcharts.api.interfaces.SeriesApi.Params.SERIES_UUID
import com.tradingview.lightweightcharts.api.options.enums.MismatchDirection
import com.tradingview.lightweightcharts.api.options.models.PriceLineOptions
import com.tradingview.lightweightcharts.api.options.models.SeriesOptionsCommon
import com.tradingview.lightweightcharts.api.serializer.ClassSimpleDeserializer
import com.tradingview.lightweightcharts.api.serializer.Deserializer
import com.tradingview.lightweightcharts.api.serializer.PrimitiveSerializer
import com.tradingview.lightweightcharts.api.serializer.SeriesMarkersDeserializer
import com.tradingview.lightweightcharts.api.serializer.SeriesTypeDeserializer
import com.tradingview.lightweightcharts.api.series.common.PriceLine
import com.tradingview.lightweightcharts.api.series.common.PriceLineDelegate
import com.tradingview.lightweightcharts.api.series.common.SeriesData
import com.tradingview.lightweightcharts.api.series.enums.SeriesType
import com.tradingview.lightweightcharts.api.series.models.SeriesMarker
import com.tradingview.lightweightcharts.runtime.controller.WebMessageController
import com.tradingview.lightweightcharts.runtime.version.ChartRuntimeObject

class SeriesApiDelegate<T : SeriesOptionsCommon>(
    override val uuid: String,
    private val controller: WebMessageController,
    private val optionsDeserializer: Deserializer<out T>,
) : SeriesApi, ChartRuntimeObject {

    override fun getVersion(): Int {
        return controller.hashCode()
    }

    override fun setData(data: List<SeriesData>) {
        controller.callFunction(
            SET_SERIES,
            mapOf(
                SERIES_UUID to uuid,
                DATA to data
            )
        )
    }

    override fun priceToCoordinate(price: Float, onCoordinateReceived: (Float?) -> Unit) {
        controller.callFunction<Float?>(
            PRICE_TO_COORDINATE,
            mapOf(
                SERIES_UUID to uuid,
                PRICE to price
            ),
            callback = onCoordinateReceived,
            PrimitiveSerializer.FloatDeserializer
        )
    }

    override fun coordinateToPrice(coordinate: Float, onPriceReceived: (Float?) -> Unit) {
        controller.callFunction(
            COORDINATE_TO_PRICE,
            mapOf(
                SERIES_UUID to uuid,
                COORDINATE to coordinate
            ),
            callback = onPriceReceived,
            PrimitiveSerializer.FloatDeserializer
        )
    }

    override fun applyOptions(options: SeriesOptionsCommon) {
        controller.callFunction(
            APPLY_OPTIONS,
            mapOf(
                SERIES_UUID to uuid,
                OPTIONS to options
            )
        )
    }

    override fun options(onOptionsReceived: (SeriesOptionsCommon) -> Unit) {
        controller.callFunction(
            OPTIONS,
            mapOf(SERIES_UUID to uuid),
            callback = onOptionsReceived,
            deserializer = optionsDeserializer
        )
    }

    override fun priceScale(): PriceScaleApi {
        val uuid = controller.callFunction(
            PRICE_SCALE_SERIES,
            mapOf(
                SERIES_UUID to uuid,
            )
        )
        return PriceScaleApiDelegate(uuid, controller)
    }

    override fun <T : SeriesData> dataByIndex(
        clazz: Class<T>,
        logicalIndex: Int,
        direction: MismatchDirection,
        dataReceived: (T) -> Unit,
    ) {
        controller.callFunction(
            DATA_BY_INDEX_SERIES,
            mapOf(
                SERIES_UUID to uuid,
                LOGICAL_INDEX to logicalIndex,
                MISMATCH_DIRECTION to direction.value
            ),
            callback = dataReceived,
            deserializer = ClassSimpleDeserializer(clazz)
        )
    }

    override fun setMarkers(data: List<SeriesMarker>) {
        controller.callFunction(
            SET_MARKERS,
            mapOf(
                SERIES_UUID to uuid,
                DATA to data
            )
        )
    }

    override fun markers(markersReceived: (List<SeriesMarker>) -> Unit) {
        controller.callFunction(
            GET_MARKERS_SERIES,
            mapOf(
                SERIES_UUID to uuid,
            ),
            callback = markersReceived,
            deserializer = SeriesMarkersDeserializer()
        )
    }

    override fun createPriceLine(options: PriceLineOptions): PriceLine {
        val uuid = controller.callFunction(
            CREATE_PRICE_LINE,
            mapOf(
                SERIES_UUID to uuid,
                OPTIONS to options
            )
        )
        return PriceLineDelegate(
            uuid,
            controller
        )
    }

    override fun removePriceLine(line: PriceLine) {
        controller.callFunction(
            REMOVE_PRICE_LINE,
            mapOf(
                SERIES_UUID to uuid,
                LINE_ID to line.uuid
            )
        )
    }

    override fun update(bar: SeriesData) {
        controller.callFunction(
            UPDATE,
            mapOf(
                SERIES_UUID to uuid,
                BAR to bar
            )
        )
    }

    override fun seriesType(onSeriesTypeReceived: (SeriesType) -> Unit) {
        controller.callFunction(
            SERIES_TYPE,
            mapOf(SERIES_UUID to uuid),
            callback = onSeriesTypeReceived,
            deserializer = SeriesTypeDeserializer()
        )
    }

    override fun moveToPane(paneIndex: Int) {
        controller.callFunction(
            MOVE_TO_PANE,
            mapOf(
                SERIES_UUID to uuid,
                PANE_INDEX to paneIndex
            )
        )
    }

    override fun getPane(onPaneReceived: (PaneApi) -> Unit) {
        controller.callFunction(
            GET_PANE,
            mapOf(SERIES_UUID to uuid),
            callback = { paneUuid: String ->
                onPaneReceived(PaneApiDelegate(paneUuid, controller))
            },
            deserializer = PrimitiveSerializer.StringDeserializer
        )
    }
}
