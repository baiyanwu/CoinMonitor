const path = require('path');

const config = {
    mode: "production",
    optimization: {
        minimize: false
    },
    resolve: {
        alias: {
            lightweight$: path.resolve(__dirname, './node_modules/lightweight-charts/dist/lightweight-charts.development.mjs'),
        },
    },
};

module.exports = () => {
    return config;
};
