import FunctionManager from "../function-manager";
import PluginManager from "../plugin-manager";
import TickMarkFormatterService from "./tick-mark-formatter";

export default class TimeScaleInstanceService {

    /**
     * 
     * @param {ServiceLocator} locator 
     */
    constructor(locator) {
        this.chart = locator.resolve("chart");

        /** @type {PluginManager} */
        this.pluginManager = locator.resolve(PluginManager.name);

        /** @type {FunctionManager} */
        this.functionManager = locator.resolve(FunctionManager.name);

        /** @type {TickMarkFormatterService} */
        this.tickMarkFormatter = locator.resolve(TickMarkFormatterService.name);
    }

    _timeScale() {
        return this.chart.timeScale();
    }

    _timeScaleInstanceMethods() {
        return [
            new ScrollPosition(),
            new ScrollToPosition(),
            new TimeScaleOptions(this.pluginManager),
            new TimeScaleApplyOptions(this.tickMarkFormatter),
            new ScrollToRealTime(),
            new GetVisibleRange(),
            new SetVisibleRange(),
            new GetVisibleLogicalRange(),
            new SetVisibleLogicalRange(),
            new ResetTimeScale(),
            new FitContent(),
            new TimeToCoordinate(),
            new CoordinateToTime(),
            new LogicalToCoordinate(),
            new CoordinateToLogical(),
            new Width(),
            new Height()
        ];
    }

    _timeScaleInstanceSusbcriptions() {
        return [
            new SubscribeVisibleTimeRangeChange(),
            new SubscribeVisibleLogicalRangeChange(),
            new SubscribeSizeChange()
        ];
    }

    register() {
        this._timeScaleInstanceMethods().forEach((method) => {
            this.functionManager.registerFunction(method.name, (input, resolve) => {
                method.invoke(this._timeScale(), input.params, resolve);
            });
        });

        this._timeScaleInstanceSusbcriptions().forEach((subscription) => {
            this.functionManager.registerSubscription(
                subscription.name, 
                (input, callback) => {
                    return subscription.subscribe(this._timeScale(), callback);
                },
                (subscription) => {
                    subscription.unsubscribe(this._timeScale(), subscription);
                }
            );
        });
    }
}

class TimeScaleSubscription {
    constructor(name, subscribe, unsubscribe) {
        this.name = name;
        this.subscribe = subscribe;
        this.unsubscribe = unsubscribe;
    }
}

class TimeScaleMethod {
    constructor(name, invoke) {
        this.name = name;
        this.invoke = invoke;
    }
}

class TimeScaleMethodWithReturn extends TimeScaleMethod {
    constructor(name, invoke) {
        super(name, (timeScale, params, resolve) => {
            resolve(invoke(timeScale, params));
        });
    }
}

class ScrollPosition extends TimeScaleMethodWithReturn {
    constructor() {
        super("scrollPosition", (timeScale, params) => {
            return timeScale.scrollPosition();
        });
    }
}

class ScrollToPosition extends TimeScaleMethod {
    constructor() {
        super("scrollToPosition", (timeScale, params) => {
            timeScale.scrollToPosition(params.position, params.animated);
        });
    }
}

class TimeScaleOptions extends TimeScaleMethodWithReturn {
    constructor(pluginManager) {
        super("timeScaleOptions", (timeScale, params) => {
            const options = timeScale.options();
            if (options.tickMarkFormatter) {
                const fun = options.tickMarkFormatter;
                options.tickMarkFormatter = pluginManager.getPlugin(fun);
            }
            return options;
        });
    }
}

class TimeScaleApplyOptions extends TimeScaleMethod {
    /**
     * 
     * @param {TickMarkFormatterService} tickMarkFormatter 
     */
    constructor(tickMarkFormatter) {
        super("timeScaleApplyOptions", (timeScale, params) => {
            tickMarkFormatter.register(params, (paramsWithPlugin) => {
                timeScale.applyOptions(paramsWithPlugin.options);
            });
        })
    }
}

class ScrollToRealTime extends TimeScaleMethod {
    constructor() {
        super("scrollToRealTime", (timeScale, params) => {
            timeScale.scrollToRealTime();
        });
    }
}

class GetVisibleRange extends TimeScaleMethodWithReturn {
    constructor() {
        super("getVisibleRange", (timeScale, params) => {
            return timeScale.getVisibleRange();
        });
    }
}

class SetVisibleRange extends TimeScaleMethod {
    constructor() {
        super("setVisibleRange", (timeScale, params) => {
            timeScale.setVisibleRange(params.range);
        });
    }
}

class ResetTimeScale extends TimeScaleMethod {
    constructor() {
        super("resetTimeScale", (timeScale, params) => {
            timeScale.resetTimeScale();
        });
    }
}

class GetVisibleLogicalRange extends TimeScaleMethodWithReturn {
    constructor() {
        super("getVisibleLogicalRange", (timeScale, params) => {
            return timeScale.getVisibleLogicalRange();
        });
    }
}

class SetVisibleLogicalRange extends TimeScaleMethod {
    constructor() {
        super("setVisibleLogicalRange", (timeScale, params) => {
            timeScale.setVisibleLogicalRange(params.range);
        });
    }
}

class FitContent extends TimeScaleMethod {
    constructor() {
        super("fitContent", (timeScale, params) => {
            timeScale.fitContent();
        });
    }
}

class TimeToCoordinate extends TimeScaleMethodWithReturn {
    constructor() {
        super("timeToCoordinate", (timeScale, params) => {
            return timeScale.timeToCoordinate(params.time);
        });
    }
}

class CoordinateToTime extends TimeScaleMethodWithReturn {
    constructor() {
        super("coordinateToTime", (timeScale, params) => {
            return timeScale.coordinateToTime(params.x);
        });
    }
}

class LogicalToCoordinate extends TimeScaleMethodWithReturn {
    constructor() {
        super("logicalToCoordinate", (timeScale, params) => {
            return timeScale.logicalToCoordinate(params.logical);
        });
    }
}

class CoordinateToLogical extends TimeScaleMethodWithReturn {
    constructor() {
        super("coordinateToLogical", (timeScale, params) => {
            return timeScale.coordinateToLogical(params.x);
        });
    }
}

class Width extends TimeScaleMethodWithReturn {
    constructor() {
        super("timeScaleWidth", (timeScale, params) => {
            return timeScale.width();
        });
    }
}

class Height extends TimeScaleMethodWithReturn {
    constructor() {
        super("timeScaleHeight", (timeScale, params) => {
            return timeScale.height();
        });
    }
}

class SubscribeVisibleTimeRangeChange extends TimeScaleSubscription {
    constructor() {
        super(
            "subscribeVisibleTimeRangeChange", 
            (timeScale, callback) => {
                timeScale.subscribeVisibleTimeRangeChange(callback);
                return callback;
            },
            (timeScale, subscription) => {
                timeScale.unsubscribeVisibleTimeRangeChange(subscription);
            }
        );
    }
}

class SubscribeSizeChange extends TimeScaleSubscription {
    constructor() {
        super(
            "subscribeTimeScaleSizeChange",
            (timeScale, callback) => {
                const subcription = (width, height) => {
                    callback({width: width, height: height});
                };
                timeScale.subscribeSizeChange(subcription);
                return subcription;
            },
            (timeScale, subscription) => {
                timeScale.unsubscribeSizeChange(subscription);
            }
        );
    }
}

class SubscribeVisibleLogicalRangeChange extends TimeScaleSubscription {
    constructor() {
        super(
            "subscribeVisibleLogicalRangeChange",
            (timeScale, callback) => {
                timeScale.subscribeVisibleLogicalRangeChange(callback);
                return callback;
            },
            (timeScale, subscription) => {
                timeScale.unsubscribeVisibleLogicalRangeChange(subscription);
            }
        );
    }
}
