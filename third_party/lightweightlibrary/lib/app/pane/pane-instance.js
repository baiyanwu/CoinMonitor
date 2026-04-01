import FunctionManager from "../function-manager";
import PaneCache from "./pane-cache";

export default class PaneInstanceService {

    constructor(locator) {
        /** @type {PaneCache} */
        this.paneCache = locator.resolve(PaneCache.name);
        /** @type {FunctionManager} */
        this.functionManager = locator.resolve(FunctionManager.name);
    }

    register() {
        this._paneInstanceMethods().forEach((method) => {
            this.functionManager.registerFunction(method.name, (input, resolve) => {
                this._findPane(input, (pane) => {
                    method.invoke(pane, input.params, resolve);
                });
            });
        });
    }

    _findPane(input, callback) {
        const pane = this.paneCache.get(input.params.paneId);
        if (pane === undefined) {
            this.functionManager.throwFatalError(new Error(`Pane with uuid:${input.uuid} is not found`), input);
        } else {
            callback(pane);
        }
    }

    _paneInstanceMethods() {
        return [
            new PaneSetStretchFactor(),
            new PaneGetStretchFactor(),
            new PaneIndex()
        ];
    }
}

class PaneInstanceMethod {
    constructor(name, invoke) {
        this.name = name;
        this.invoke = invoke;
    }
}

class PaneSetStretchFactor extends PaneInstanceMethod {
    constructor() {
        super("paneSetStretchFactor", (pane, params, resolve) => {
            pane.setStretchFactor(params.stretchFactor);
        });
    }
}

class PaneGetStretchFactor extends PaneInstanceMethod {
    constructor() {
        super("paneGetStretchFactor", (pane, params, resolve) => {
            resolve(pane.getStretchFactor());
        });
    }
}

class PaneIndex extends PaneInstanceMethod {
    constructor() {
        super("paneIndex", (pane, params, resolve) => {
            resolve(pane.paneIndex());
        });
    }
}
