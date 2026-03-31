export default class PriceScaleFunctionManager {

    constructor(chart, functionManager) {
        this.chart = chart
        this.functionManager = functionManager
        this.cache = new Map()
    }

    register() {
        this.functionManager.registerFunction("priceScale", (input, resolve) => {
            this.cache.set(input.uuid, this.chart.priceScale(input.params.priceScaleId))
        })
        this.functionManager.registerFunction("priceScaleApplyOptions", (input, resolve) => {
            const scale = this.cache.get(input.params.caller)
            if (scale === undefined) {
                this.functionManager.throwFatalError(new Error(`PriceScale with uuid:${input.caller} is not found`), input)
            } else {
                scale.applyOptions(input.params.options)
            }
        })
        this.functionManager.registerFunction("priceScaleWidth", (input, resolve) => {
            const scale = this.cache.get(input.params.caller)
            if (scale === undefined) {
                this.functionManager.throwFatalError(new Error(`PriceScale with uuid:${input.caller} is not found`), input)
            } else {
                resolve(scale.width())
            }
        })
    }
}