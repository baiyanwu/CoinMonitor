export default class ServiceLocator {
    constructor() {
        this._containers = {};
    }

    register(key, lazyContainer) {
        if (this._containers.hasOwnProperty(key)) {
            throw new Error("Key registered already");
        }

        this._containers[key] = new ContainerWrapper(lazyContainer);
    }

    resolve(key) {
        if (!this._containers.hasOwnProperty(key)) {
            throw new Error(`Container for ${key} is not exists`);
        }
        return this._containers[key].resolve();
    }
}

class ContainerWrapper {
    constructor(container) {
        this.container = container;
    }

    resolve() {
        if (!this.instance) {
            this.instance = this.container();
        }
        return this.instance;
    }
}

export const Locator = new ServiceLocator();

import './locator-component';