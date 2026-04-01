export default class PaneCache extends Map {
    getKeyOfPane(paneObject) {
        for (let [key, value] of this.entries()) {
            if (Object.is(value, paneObject)) {
                return key;
            }
        }
        throw new Error("Pane is not found");
    }
}
