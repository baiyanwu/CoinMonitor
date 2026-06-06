export default class Logger {
    constructor() {
        this._level = "none"
    }

    setLevel(level) {
        this._level = level
    }

    d(...args) {
        if (this._getLevel() == 0) {
            console.debug(...args)
        }
    }

    w(...args) {
        if (this._getLevel() <= 1) {
            console.warning(...args)
        }
    }

    e(...args) {
        if (this._getLevel() <= 2) {
            console.error(...args)
        }
    }

    _getLevel() {
        switch(this._level) {
            case "debug":
                return 0
            case "warning":
                return 1
            case "error":
                return 2
            case "none":
                return 3
        }
        return 3
    }
}

export const logger = new Logger()