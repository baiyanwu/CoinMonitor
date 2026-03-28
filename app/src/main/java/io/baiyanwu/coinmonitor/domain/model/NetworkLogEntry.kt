package io.baiyanwu.coinmonitor.domain.model

/**
 * 当前日志页先只按“一行一条”展示摘要，但这里提前把详情文本也带上，
 * 后续做单条详情页时不需要再返工数据结构。
 */
data class NetworkLogEntry(
    val id: Long,
    val line: String,
    val detail: String,
    val createdAt: Long
)
