import FunctionManager from "../function-manager";
import PluginManager from "../plugin-manager";
import SeriesCache from "./series-cache";

export default class SeriesCreationService {

    /**
     * 
     * @param {ServiceLocator} locator 
     */
    constructor(locator) {
        this.chart = locator.resolve("chart");
        this.seriesCache = locator.resolve(SeriesCache.name);
        this.functionManager = locator.resolve(FunctionManager.name);
        this.pluginManager = locator.resolve(PluginManager.name);
    }

    register() {
        this._seriesFunctions().forEach((method) => {
            this.functionManager.registerFunction(method.name, (input, resolve) => {
                this._registerPlugins(input.params, (params) => {
                    this._addSeries(input.uuid, method.invoke(params.options));
                    resolve(input.uuid);
                });
            });
        });
    }

    _registerAutoscaleInfoProvider(params, callback) {
        if (!params.options.autoscaleInfoProvider) {
            callback(params);
            return;
        }

        const plugin = params.options.autoscaleInfoProvider;
        this.pluginManager.register(plugin, (fun) => {
            params.options.autoscaleInfoProvider = fun;
            callback(params);
        });
    }

    _registerPriceFormatter(params, callback) {
        if (!params.options.priceFormat || !params.options.priceFormat.formatter) {
            callback(params);
            return;
        }

        const plugin = params.options.priceFormat.formatter;
        this.pluginManager.register(plugin, (fun) => {
            params.options.priceFormat.formatter = fun;
            callback(params);
        });
    }

    _registerPlugins(rawParams, callback) {
        this._registerAutoscaleInfoProvider(rawParams, (autoscaleParams) => {
            this._registerPriceFormatter(autoscaleParams, (params) => {
                callback(params);
            });
        });
    }

    _addSeries(uuid, series) {
        this.seriesCache.set(uuid, series);
    }

    _seriesFunctions() {
        return [
            new AddAreaSeries(this.chart),
            new AddLineSeries(this.chart),
            new AddBarSeries(this.chart),
            new AddCandlestickSeries(this.chart),
            new AddHistogramSeries(this.chart),
            new AddBaselineSeries(this.chart)
        ];
    }
}

class SeriesCreationMethod {
    constructor(name, invoke) {
        this.name = name;
        this.invoke = invoke;
    }
}

class AddAreaSeries extends SeriesCreationMethod {
    constructor(chart) {
        super("addAreaSeries", function(options) {
            return chart.addSeries(window.LightweightCharts.AreaSeries, options);
        })
    }
}

class AddLineSeries extends SeriesCreationMethod {
    constructor(chart) {
        super("addLineSeries", function(options) {
            return chart.addSeries(window.LightweightCharts.LineSeries, options);
        })
    }
}

class AddBarSeries extends SeriesCreationMethod {
    constructor(chart) {
        super("addBarSeries", function(options) {
            return chart.addSeries(window.LightweightCharts.BarSeries, options);
        })
    }
}

class AddCandlestickSeries extends SeriesCreationMethod {
    constructor(chart) {
        super("addCandlestickSeries", function(options) {
            return chart.addSeries(window.LightweightCharts.CandlestickSeries, options);
        })
    }
}

class AddHistogramSeries extends SeriesCreationMethod {
    constructor(chart) {
        super("addHistogramSeries", function(options) {
            return chart.addSeries(window.LightweightCharts.HistogramSeries, options);
        })
    }
}

class AddBaselineSeries extends SeriesCreationMethod {
    constructor(chart) {
        super("addBaselineSeries", function(options) {
            return chart.addSeries(window.LightweightCharts.BaselineSeries, options);
        })
    }
}
