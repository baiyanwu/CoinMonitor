import FunctionManager from '../function-manager.js';
import PriceScaleCreationService from './price-scale-creation.js';
import PriceScaleInstanceService from './price-scale-instance.js';

export default class PriceScaleFunctionManager {

    /**
     * 
     * @param {ServiceLocator} locator 
     */
    constructor(locator) {
        this.functionManager = locator.resolve(FunctionManager.name);
        this.creationService = locator.resolve(PriceScaleCreationService.name);
        this.instanceService = locator.resolve(PriceScaleInstanceService.name);
    }

    register() {
        this.creationService.register();
        this.instanceService.register();
    }

}
