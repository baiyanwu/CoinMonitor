package io.baiyanwu.coinmonitor.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_chat_sessions")
data class AiChatSessionEntity(
    @PrimaryKey val id: String,
    val title: String?,
    val itemId: String?,
    val symbol: String?,
    val sourceTitle: String?,
    val createdAt: Long,
    val updatedAt: Long
)
