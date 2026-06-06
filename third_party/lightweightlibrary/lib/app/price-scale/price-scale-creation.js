import FunctionManager from "../function-manager.js";
import PriceScaleCache from "./price-scale-cache.js";
import SeriesCache from "../series/series-cache.js";

export default class PriceScaleCreationService {

    /**
     * 
     * @param {ServiceLocator} locator 
     */
    constructor(locator) {
        this.chart = locator.resolve("chart")
        this.pricescalesCache = locator.resolve(PriceScaleCache.name);
        this.seriesCache = locator.resolve(SeriesCache.name);
        this.functionManager = locator.resolve(FunctionManager.name);
    }

    register() {
        this._createPriceApiFunctions().forEach((method) => {
            this.functionManager.registerFunction(method.name, (input, resolve) => {
                this._addPriceScale(input.uuid, method.invoke(input));
                resolve(input.uuid);
            });
        });
    }

    _addPriceScale(uuid, series) {
        this.pricescalesCache.set(uuid, series);
    }

    _createPriceApiFunctions() {
        return [
            new PriceScaleChart(this.chart),
            new SeriesPriceScaleChart(this.seriesCache),
        ];
    }
}

class SeriesCreationMethod {
    constructor(name, invoke) {
        this.name = name;
        this.invoke = invoke;
    }
}

class PriceScaleChart extends SeriesCreationMethod {
    constructor(chart) {
        super("priceScale", function (input) {
            return chart.priceScale(input.params.priceScaleId);
        })
    }
}


class SeriesPriceScaleChart extends SeriesCreationMethod {
    constructor(seriesCache) {
        super("priceScaleSeries", function (input) {
            const series = this._findSeries(input);
            return series.priceScale();
        })
        this.seriesCache = seriesCache;
    }

    _findSeries(input) {
        let series = this.seriesCache.get(input.params.seriesId)
        if (series === undefined) {
            this.functionManager.throwFatalError(new Error(`${seriesName} with uuid:${input.uuid} is not found`), input);
        }

        return series;
    }
}
