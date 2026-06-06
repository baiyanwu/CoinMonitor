package io.baiyanwu.coinmonitor.domain.model

data class OpenAiCompatibleConfig(
    val enabled: Boolean = false,
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT
) {
    val isReady: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()

    companion object {
        const val DEFAULT_SYSTEM_PROMPT =
            "你是一个加密市场分析助手。回答保持简洁、具体，不编造不存在的行情数据。"
    }
}
