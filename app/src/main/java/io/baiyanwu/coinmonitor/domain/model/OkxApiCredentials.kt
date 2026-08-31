package io.baiyanwu.coinmonitor.domain.model

data class OkxApiCredentials(
    val enabled: Boolean = false,
    val apiKey: String = "",
    val secretKey: String = "",
    val passphrase: String = "",
    val dexPollingIntervalSeconds: Int = DEFAULT_DEX_POLLING_INTERVAL_SECONDS
) {
    val isReady: Boolean
        get() = apiKey.isNotBlank() && secretKey.isNotBlank() && passphrase.isNotBlank()

    val effectiveDexPollingIntervalSeconds: Int
        get() = normalizeDexPollingIntervalSeconds(dexPollingIntervalSeconds)

    companion object {
        const val DEFAULT_DEX_POLLING_INTERVAL_SECONDS = 45
        const val MIN_DEX_POLLING_INTERVAL_SECONDS = 10
        const val MAX_DEX_POLLING_INTERVAL_SECONDS = 120
        const val DEX_POLLING_INTERVAL_STEP_SECONDS = 5
        const val RECOMMENDED_MIN_DEX_POLLING_INTERVAL_SECONDS = 30

        fun normalizeDexPollingIntervalSeconds(value: Int): Int {
            val clamped = value.coerceIn(
                MIN_DEX_POLLING_INTERVAL_SECONDS,
                MAX_DEX_POLLING_INTERVAL_SECONDS
            )
            val stepsFromMinimum = (
                clamped - MIN_DEX_POLLING_INTERVAL_SECONDS +
                    DEX_POLLING_INTERVAL_STEP_SECONDS / 2
                ) / DEX_POLLING_INTERVAL_STEP_SECONDS
            return MIN_DEX_POLLING_INTERVAL_SECONDS +
                stepsFromMinimum * DEX_POLLING_INTERVAL_STEP_SECONDS
        }
    }
}
