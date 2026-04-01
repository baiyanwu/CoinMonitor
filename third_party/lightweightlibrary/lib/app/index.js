import * as LightweightCharts from 'lightweight';
import FunctionManager from './function-manager.js';
import ChartRegistrationFunctionsController from './chart-registration-functions-controller.js';
import PluginManager from './plugin-manager.js';
import { logger } from './logger.js';
import { initLocator } from './service-locator/locator-component.js';

const createChart = LightweightCharts.createChart
window.LightweightCharts = Object.assign({}, LightweightCharts)
delete window.LightweightCharts.createChart

logger.setLevel("warning")

onmessage = function (message) {
    const connectionMessage = JSON.parse(message.data)

    if (connectionMessage.messageType !== "Message::Connection") {
        logger.e("Connection message is not valid")
        return
    }

    const logLevel = connectionMessage.data.logLevel
    logger.setLevel(logLevel)
    logger.d("Received connection message", message)

    const port = message.ports[0]
    const functionManager = new FunctionManager(port)

    const pluginManager = new PluginManager()
    window.pluginManager = pluginManager

    initLocator(functionManager, pluginManager, window.chart);

    const functionsController = new ChartRegistrationFunctionsController(
        window.chart,
        functionManager,
        pluginManager
    )
    functionsController.registerFunctions()
    window.functionsController = functionsController

    logger.d("Connection has been established")
    port.onmessage = function (event) {
        const nativeMessage = JSON.parse(event.data)

        if (nativeMessage.data.fn) {
            logger.d("function", nativeMessage.data.fn)
        }
        logger.d("data", JSON.stringify(nativeMessage.data))

        switch (nativeMessage.messageType) {
            case 'Message::Function':
                functionManager.call(nativeMessage.data)
                break;
            case 'Message::Subscription':
                functionManager.subscribe(nativeMessage.data)
                break;
            case 'Message::SubscriptionCancellation':
                functionManager.unsubscribe(nativeMessage.data)
                break;
        }
    }
}

window.onresize = () => {
    window.chart.resize(window.innerWidth, window.innerHeight)
}

onload = () => {
    document.documentElement.style.margin = '0'
    document.documentElement.style.padding = '0'
    document.documentElement.style.width = '100%'
    document.documentElement.style.height = '100%'
    document.documentElement.style.background = 'transparent'
    document.body.style.margin = '0'
    document.body.style.padding = '0'
    document.body.style.width = '100%'
    document.body.style.height = '100%'
    document.body.style.background = 'transparent'

    window.chart = createChart(document.body, {
        width: window.innerWidth,
        height: window.innerHeight,
        layout: {
            background: {
                type: 'solid',
                color: '#00000000',
            },
            panes: {
                separatorColor: '#00000000',
                separatorHoverColor: '#00000000',
            },
        },
    });
}
