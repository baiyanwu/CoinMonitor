package com.tradingview.lightweightcharts.runtime.plugins

/**
 * @param template Syntax - "string{price:#integer:#decimal}string"
 *                 string - Any string
 *                 price (Optional) - Constant. Necessary for regex replacing
 *                 integer (Optional) - Minimal numbers of digits before decimal point
 *                 decimal (Optional) - Fixed number of decimal places
 *                 ex. "{price:#5:#3}$" | 135.5 -> 00135.500$
 *                 ex. "$"              | 135.5 -> $
 *                 ex. "{price}"        | 135.5 -> 135.5
 *                 ex. "{price:#1}"     | 135.5 -> 135.5
 *                 ex. "{price:#0:#0}"  | 135.5 -> 135
 */
class PriceFormatter(template: String) : Plugin(
    name = "priceFormatter",
    file = "scripts/plugins/price-formatter/main.js",
    configurationParams = FormatterParams(template)
)

data class FormatterParams(
    val template: String
)
