import TimeScaleInstanceService from "./time-scale-instance";

export default class TimeScaleFunctionManager {

    /**
     * 
     * @param {ServiceLocator} locator
     */
    constructor(locator) {
        /** @type {TimeScaleInstanceService} */
        this.timeScaleInstanceService = locator.resolve(TimeScaleInstanceService.name);
    }

    register() {
        this.timeScaleInstanceService.register();
    }
}
