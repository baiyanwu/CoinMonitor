
import { logger } from './logger.js'

export default class FunctionManager {
    constructor(port) {
        this.functions = []
        this.port = port
        this.subscriptions = new Map()
    }

    registerFunction(functionName, functionRef) {
        if (!this.hasDuplicates(functionName)) {
            this.functions.push({name: functionName, functionRef: functionRef})
        } else {
            logger.e(`Function:${functionName} already registered`)
        }
    }

    registerSubscription(subscriptionName, subscribe, unsubscribe) {
        if (!this.hasDuplicates(subscriptionName)) {
            this.functions.push({name: subscriptionName, subscribe: subscribe, unsubscribe: unsubscribe })
        } else {
            logger.e(`Subscription:${subscriptionName} already registered`)
        }
    }

    call(data) {
        const fn = this.functions.find((value) => { return value.name === data.fn })

        if (fn === undefined) {
            this.throwFatalError(new Error(`${data.fn} is not found`), data)
            return
        }

        try {
            fn.functionRef(data, (result) => {
                this.port.postMessage(JSON.stringify({
                    messageType: "Message::FunctionResult",
                    data: {
                        fn: data.fn,
                        uuid: data.uuid,
                        result: result
                    }
                }))
            })
        } catch (e) {
            this.throwFatalError(e, data)
        }
    }

    subscribe(data) {
        const fn = this.functions.find((value) => { return value.name === data.fn })

        if (fn === undefined) {
            this.throwFatalError(new Error(`${data.fn} is not found`), data)
            return
        }

        let callback = (result) => {
            logger.d('subscription result', result)
            this.port.postMessage(JSON.stringify({
                messageType: "Message::SubscriptionResult",
                data: {
                    fn: data.fn,
                    uuid: data.uuid,
                    result: result
                }
            }))
        }
        const subscription = fn.subscribe(data, callback)
        this.subscriptions.set(data.uuid + data.fn, subscription)
    }

    unsubscribe(data) {
        const fn = this.functions.find((value) => { return value.name === data.fn })

        if (fn === undefined) {
            this.throwFatalError(new Error(`Function:${data.fn} is not found`), data)
            return
        }

        const id = data.uuid + data.fn
        const subscription = this.subscriptions.get(id)

        if (subscription === undefined) {
            this.throwFatalError(new Error(`Subscriber:${data.fn} with uuid:${data.uuid} is not found`), data)
            return
        }

        fn.unsubscribe(subscription)
        this.subscriptions.delete(id)

        this.port.postMessage(JSON.stringify({
            messageType: "Message::SubscriptionCancellationResult",
            data: {
                fn: data.fn,
                uuid: data.uuid
            }
        }))
    }

    throwFatalError(error, data) {
        logger.e(error.message)

        if (data === undefined) {
            data = {}
        }

        data.message = error.stack

        this.port.postMessage(JSON.stringify({
            messageType: "Message::FatalError",
            data: data
        }))
    }

    hasDuplicates(functionName) {
        let duplicatedValue = this.functions.find((value) => { return value.name === functionName })
        return undefined !== duplicatedValue
    }
}

