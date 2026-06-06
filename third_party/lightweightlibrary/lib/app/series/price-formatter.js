import PluginManager from "../plugin-manager";

export default class PriceFormatterService {

    /**
     * 
     * @param {ServiceLocator} locator 
     */
    constructor(locator) {
        this.pluginManager = locator.resolve(PluginManager.name);
    }

    register(params, callback) {
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
}