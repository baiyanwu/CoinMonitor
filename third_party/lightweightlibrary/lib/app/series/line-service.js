import FunctionManager from "../function-manager";
import LineCache from "./line-cache";

export default class LineService {

    /**
     * 
     * @param {ServiceLocator} locator 
     */
    constructor(locator) {
        this.functionManager = locator.resolve(FunctionManager.name);
        this.lineCache = locator.resolve(LineCache.name);
    }

    getLine(input, callback) {
        let line = this.lineCache.get(input.params.lineId);
        if (line === undefined) {
            const error = new Error(`PriceLine with uuid:${input.uuid} is not found`);
            this.functionManager.throwFatalError(error, input);
        } else {
            callback(line);
        }
    }
}