package io.baiyanwu.coinmonitor.data.local

data class AiChatSessionSummaryRow(
    val id: String,
    val title: String?,
    val itemId: String?,
    val symbol: String?,
    val sourceTitle: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val latestMessagePreview: String?
)
