package io.baiyanwu.coinmonitor.domain.model

data class OkxApiCredentials(
    val enabled: Boolean = false,
    val apiKey: String = "",
    val secretKey: String = "",
    val passphrase: String = ""
) {
    val isReady: Boolean
        get() = apiKey.isNotBlank() && secretKey.isNotBlank() && passphrase.isNotBlank()
}

