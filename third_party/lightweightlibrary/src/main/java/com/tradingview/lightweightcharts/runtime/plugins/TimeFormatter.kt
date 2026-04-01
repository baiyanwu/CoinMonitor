package com.tradingview.lightweightcharts.runtime.plugins

class TimeFormatter(locale: String, dateTimeFormat: DateTimeFormat) : Plugin(
    name = "timeFormatter",
    file = "scripts/plugins/time-formatter/main.js",
    configurationParams = TimeFormatterParams(locale, dateTimeFormat)
)

data class TimeFormatterParams(
    val locale: String,
    val dateTimeFormat: DateTimeFormat
)

enum class DateTimeFormat {
    DATE, TIME, DATE_TIME
}
