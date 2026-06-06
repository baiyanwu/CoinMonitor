import { logger } from './logger.js'

export default class PluginManager {
    constructor() {
        this.plugins = []
    }

    register(plugin, consumer) {
        logger.d("registration of plugin", plugin)
        const uuid = plugin.uuid
        const name = plugin.name
        const fileLink = plugin.file
        const configurationParams = plugin.configurationParams
        
        const foundPlugin = this.plugins.find((item) => item.uuid == uuid)
        if (foundPlugin) {
            consumer(foundPlugin.fn)
        } else {
            this.addPlugin(consumer, uuid, name, fileLink, configurationParams)
        }
    }

    getPlugin(fn) {
        return this.plugins.find((item) => item.fn == fn)
    }

    addPlugin(consumer, uuid, name, fileLink, configurationParams) {
        this.addScript(fileLink, () => {
            const pluginConfiguration = window[name]
            const fn = pluginConfiguration(configurationParams)
            delete window[name]
            this.plugins.push({
                uuid: uuid,
                name: name, 
                file: fileLink,
                configurationParams: configurationParams,
                fn: fn
            })
            consumer(fn)
        })
    }

    addScript(file, onLoad) {
        var script = document.createElement('script')
        script.type = 'text/javascript'
        script.src = file
        script.async = false
        script.onload = onLoad
        document.getElementsByTagName('head')[0].appendChild(script)
    }
}