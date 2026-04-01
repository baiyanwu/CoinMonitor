import FunctionManager from "../function-manager";
import PaneCache from "../pane/pane-cache";
import PluginManager from "../plugin-manager";
import LineCache from "./line-cache";
import LineService from "./line-service";
import PriceFormatterService from "./price-formatter";
import SeriesCache from "./series-cache";

export default class SeriesInstanceService {

    constructor(locator) {
        this.chart = locator.resolve("chart");
        this.seriesCache = locator.resolve(SeriesCache.name);
        this.functionManager = locator.resolve(FunctionManager.name);
        this.pluginManager = locator.resolve(PluginManager.name);
        this.lineCache = locator.resolve(LineCache.name);
        this.priceFormatterService = locator.resolve(PriceFormatterService.name);
        this.lineService = locator.resolve(LineService.name);
        /** @type {PaneCache} */
        this.paneCache = locator.resolve(PaneCache.name);
    }

    register() {
        this._seriesInstanceMethods().forEach((method) => {
            this.functionManager.registerFunction(method.name, (input, resolve) => {
                this._findSeries(input, (series) => {
                    method.invoke.bind({input: input})(series, input.params, resolve);
                });
            });
        });

        this._priceLineMethods().forEach((method) => {
            this.functionManager.registerFunction(method.name, (input, resolve) => {
                this.lineService.getLine(input, (line) => {
                    method.invoke.bind({input: input})(line, input.params, resolve);
                });
            });
        });
    }

    _seriesInstanceMethods() {
        return [
            new SetData(),
            new RemoveSeries(this.chart, this.seriesCache),
            new PriceToCoordinate(),
            new CoordinateToPrice(),
            new Options(),
            new DataByIndex(),
            new SeriesType(),
            new ApplyOptions(this.priceFormatterService),
            new SetMarkers(),
            new GetMarkers(),
            new CreatePriceLine(this.lineCache),
            new RemovePriceLine(this.lineService, this.lineCache),
            new Update(),
            new MoveToPane(),
            new GetPane(this.paneCache)
        ];
    }

    _priceLineMethods() {
        return [
            new PriceLineOptions(),
            new PriceLineApplyOptions()
        ];
    }

    _findSeries(input, callback) {
        let series = this.seriesCache.get(input.params.seriesId)
        if (series === undefined) {
            this.functionManager.throwFatalError(new Error(`${seriesName} with uuid:${input.uuid} is not found`), input)
        } else {
            callback(series)
        }
    }
}

/**
 * ==============================================================
 * Methods of series instance
 * ==============================================================
 */
class SeriesInstanceMethod {
    constructor(name, invoke) {
        this.name = name;
        this.invoke = invoke;
    }
}

class SetData extends SeriesInstanceMethod {
    constructor() {
        super("setSeries", function (series, params, resolve) {
            series.setData(params.data);
        });
    }
}

class RemoveSeries extends SeriesInstanceMethod {
    constructor(chart, seriesCache) {
        super("removeSeries", function (series, params, resolve) {
            seriesCache.delete(params.seriesId);
            chart.removeSeries(series);
            resolve();
        });
    }
}

class PriceToCoordinate extends SeriesInstanceMethod {
    constructor() {
        super("priceToCoordinate", function (series, params, resolve) {
            resolve(series.priceToCoordinate(params.price));
        });
    }
}

class CoordinateToPrice extends SeriesInstanceMethod {
    constructor() {
        super("coordinateToPrice", function (series, params, resolve) {
            resolve(series.coordinateToPrice(input.params.coordinate));
        });
    }
}

class Options extends SeriesInstanceMethod {
    constructor(pluginManager) {
        super("options", function (series, params, resolve) {
            let options = series.options();

            if (options.priceFormat.formatter !== undefined) {
                const formatter = options.priceFormat.formatter;
                options.priceFormat.formatter = pluginManager.getPlugin(formatter);
            }

            if (options.autoscaleInfoProvider !== undefined) {
                const autoscaleInfoProvider = options.autoscaleInfoProvider;
                options.autoscaleInfoProvider = pluginManager.getPlugin(autoscaleInfoProvider);
            }

            resolve(options);
        });
    }
}

class SeriesType extends SeriesInstanceMethod {
    constructor() {
        super("seriesType", function (series, params, resolve) {
            resolve(series.seriesType());
        });
    }
}

class ApplyOptions extends SeriesInstanceMethod {
    constructor(priceFormatterService) {
        super("applyOptionsSeries", function (series, params, resolve) {
            priceFormatterService.register(params, (paramsWithFormatter) => {
                series.applyOptions(paramsWithFormatter.options);
                resolve();
            });
        });
    }
}

class DataByIndex extends SeriesInstanceMethod {
    constructor() {
        super("dataByIndexSeries", function (series, params, resolve) {
            const d = series.dataByIndex(params.logicalIndex, params.mismatchDirection)
            resolve(d)
        });
    }
}

class SetMarkers extends SeriesInstanceMethod {
    constructor() {
        super("setMarkers", function (series, params, resolve) {
            series.setMarkers(params.data);
        });
    }
}

class GetMarkers extends SeriesInstanceMethod {
    constructor() {
        super("getMarkersSeries", function (series, params, resolve) {
            resolve(series.markers());
        });
    }
}

class CreatePriceLine extends SeriesInstanceMethod {
    constructor(lineCache) {
        /**
         * {this} - raw input of method
         */
        super("createPriceLine", function (series, params, resolve) {
            let priceLine = series.createPriceLine(params.options);
            lineCache.set(this.input.uuid, priceLine);
        });
    }
}

class RemovePriceLine extends SeriesInstanceMethod {
    constructor(lineService, lineCache) {
        /**
         * {this} - raw input of method
         */
        super("removePriceLine", function (series, params, resolve) {
            lineService.getLine(this.input, (line) => {
                lineCache.delete(params.lineId);
                series.removePriceLine(line);
            });
        });
    }
}

class Update extends SeriesInstanceMethod {
    constructor() {
        super("update", function (series, params, resolve) {
            series.update(params.bar);
        });
    }
}

class MoveToPane extends SeriesInstanceMethod {
    constructor() {
        super("moveToPane", function (series, params, resolve) {
            series.moveToPane(params.paneIndex);
        });
    }
}

class GetPane extends SeriesInstanceMethod {
    constructor(paneCache) {
        super("getPane", function (series, params, resolve) {
            const pane = series.getPane();
            try {
                resolve(paneCache.getKeyOfPane(pane));
            } catch (e) {
                const paneId = `${params.seriesId}-pane`;
                paneCache.set(paneId, pane);
                resolve(paneId);
            }
        });
    }
}

/**
 * ==============================================================
 * Methods of price line instance
 * ==============================================================
 */
class PriceLineInstanceMethod {
    constructor(name, invoke) {
        this.name = name;
        this.invoke = invoke;
    }
}

class PriceLineOptions extends PriceLineInstanceMethod {
    constructor() {
        super("priceLineOptions", function (line, params, resolve) {
            resolve(line.options());
        });
    }
}

class PriceLineApplyOptions extends PriceLineInstanceMethod {
    constructor() {
        super("priceLineApplyOptions", function (line, params, resolve) {
            line.applyOptions(params.options);
        });
    }
}
