package io.baiyanwu.coinmonitor.domain.model

/**
 * 网络日志协议类型。
 *
 * 这里先把 HTTP / WSS 抽成统一枚举，
 * 后续如果还要补 SSE、gRPC 等协议时可以继续沿用同一套模型。
 */
enum class NetworkLogProtocol {
    HTTP,
    WSS
}

/**
 * 网络日志录制配置。
 *
 * 总开关负责控制是否进入录制态，
 * 协议开关用于在录制态下进一步筛选 HTTP / WSS 两类日志。
 */
data class NetworkLogRecordingSettings(
    val recordingEnabled: Boolean = false,
    val httpEnabled: Boolean = true,
    val wssEnabled: Boolean = true
) {
    /**
     * 判断当前协议是否允许写入日志仓库。
     */
    fun isEnabledFor(protocol: NetworkLogProtocol): Boolean {
        return when (protocol) {
            NetworkLogProtocol.HTTP -> httpEnabled
            NetworkLogProtocol.WSS -> wssEnabled
        }
    }
}

/**
 * 单条网络日志记录。
 *
 * 当前日志页先按“一行一条”展示摘要，
 * 但这里把协议和详情文本也一起带上，后续扩展详情页时不需要返工数据结构。
 */
data class NetworkLogEntry(
    val id: Long,
    val protocol: NetworkLogProtocol,
    val line: String,
    val detail: String,
    val createdAt: Long
)
