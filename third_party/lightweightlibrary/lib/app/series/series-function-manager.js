import FunctionManager from '../function-manager.js';
import SeriesCache from './series-cache.js';
import SeriesCreationService from './series-creation.js'
import SeriesInstanceService from './series-instance.js';

export default class SeriesFunctionManager {

    /**
     * 
     * @param {ServiceLocator} locator 
     */
    constructor(locator) {
        /** @type {SeriesCache} */
        this.seriesCache = locator.resolve(SeriesCache.name);
        this.functionManager = locator.resolve(FunctionManager.name);
        this.seriesCreationService = locator.resolve(SeriesCreationService.name);
        this.seriesInstanceService = locator.resolve(SeriesInstanceService.name);
    }

    register() {
        this.seriesCreationService.register();
        this.seriesInstanceService.register();
    }

    getSeriesId(seriesObject, input) {
        try {
            return this.seriesCache.getKeyOfSeries(seriesObject);
        } catch(e) {
            this.functionManager.throwFatalError(new Error(`Series id is not found`), input);

            return undefined;
        }
    }
}
