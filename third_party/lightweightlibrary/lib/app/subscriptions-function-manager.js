import {logger} from './logger.js'

export default class SubscriptionsFunctionManager {

    constructor(chart, functionManager, seriesFunctionManager) {
        this.chart = chart
        this.functionManager = functionManager
        this.seriesFunctionManager = seriesFunctionManager
    }

    register() {
        this.functionManager.registerSubscription(
            "subscribeOnClick",
            (input, callback) => {
                try {
                    const subscription = (params) => {
                        let mouseEvent = this.mouseEvent(params, input)
                        callback(mouseEvent)
                    }
                    chart.subscribeClick(subscription)
                    logger.d("subscribeOnChartClicked successful")
                    return subscription
                } catch (error) {
                    logger.e('subscribeOnClick has been failed', error)
                    return null
                }
            },
            (subscription) => {
                try {
                    chart.unsubscribeClick(subscription)
                    logger.d("unsubscribeOnChartClicked successful")
                } catch (error) {
                    logger.e('unsubscribeOnClick has been failed', error)
                }
            }
        )

        this.functionManager.registerSubscription("subscribeCrosshairMove",
            (input, callback) => {
                try {
                    const subscription = (params) => {
                        let mouseEvent = this.mouseEvent(params, input)
                        callback(mouseEvent)
                    }
                    chart.subscribeCrosshairMove(subscription)
                    logger.d("subscribeCrosshairMove successful")
                    return subscription
                } catch (error) {
                    logger.e('subscribeCrosshairMove has been failed', error)
                    return null
                }
            },
            (subscription) => {
                try {
                    chart.unsubscribeCrosshairMove(subscription)
                    logger.d("unsubscribeCrosshairMove successful")
                } catch (error) {
                    logger.e('unsubscribeCrosshairMove has been failed', error)
                }
            }
        )
    }

    mouseEvent(params, input) {
        let result = this.selectProps("time", "logical", "point")(params)
        result.sourceEvent = this.selectProps(
            "clientX", "clientY", "pageX", "pageY", "screenX", "screenY",
            "localX", "localY", "ctrlKey", "altKey", "shiftKey", "metaKey"
        )(params.sourceEvent)

        result.seriesData = []
        params.seriesData.forEach((value, key, map) => {
            result.seriesData.push({
                id: this.seriesFunctionManager.getSeriesId(key, input),
                prices: value
            })
        })


        if (params.hoveredSeries) {
            result.hoveredSeries = this.seriesFunctionManager.getSeriesId(params.hoveredSeries, input)
        }
        return result
    }

    selectProps(...props) {
        return function (obj) {
            const newObj = {};
            props.forEach(name => {
                if (obj && obj.hasOwnProperty(name)) {
                    newObj[name] = obj[name];
                }
            });

            return newObj;
        }
    }


}
