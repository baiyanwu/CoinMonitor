export default class SeriesCache extends Map {
    getKeyOfSeries(seriesObject) {
        for (let [key, value] of this.entries()) {
            if (Object.is(value, seriesObject)) {
                return key;
            }
        }
        throw new Error("Series is not found");
    }
}