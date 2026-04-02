package io.baiyanwu.coinmonitor.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.baiyanwu.coinmonitor.data.local.AiChatMessageEntity
import io.baiyanwu.coinmonitor.data.local.AiChatSessionEntity
import io.baiyanwu.coinmonitor.data.local.AiChatSessionSummaryRow
import kotlinx.coroutines.flow.Flow

@Dao
interface AiChatDao {
    @Query(
        """
        SELECT 
            s.id,
            s.title,
            s.itemId,
            s.symbol,
            s.sourceTitle,
            s.createdAt,
            s.updatedAt,
            (
                SELECT m.content
                FROM ai_chat_messages m
                WHERE m.sessionId = s.id
                ORDER BY m.timestampMillis DESC
                LIMIT 1
            ) AS latestMessagePreview
        FROM ai_chat_sessions s
        WHERE EXISTS (
            SELECT 1
            FROM ai_chat_messages m
            WHERE m.sessionId = s.id
        )
        ORDER BY s.updatedAt DESC
        """
    )
    fun observeSessionSummaries(): Flow<List<AiChatSessionSummaryRow>>

    @Query("SELECT * FROM ai_chat_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: String): AiChatSessionEntity?

    @Query("SELECT * FROM ai_chat_sessions ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestSession(): AiChatSessionEntity?

    @Query("SELECT * FROM ai_chat_messages WHERE sessionId = :sessionId ORDER BY timestampMillis ASC")
    fun observeMessages(sessionId: String): Flow<List<AiChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: AiChatSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: AiChatMessageEntity)

    @Query(
        """
        UPDATE ai_chat_sessions
        SET title = :title,
            itemId = :itemId,
            symbol = :symbol,
            sourceTitle = :sourceTitle,
            updatedAt = :updatedAt
        WHERE id = :sessionId
        """
    )
    suspend fun updateSessionSnapshot(
        sessionId: String,
        title: String?,
        itemId: String?,
        symbol: String?,
        sourceTitle: String?,
        updatedAt: Long
    )

    @Query("UPDATE ai_chat_sessions SET updatedAt = :updatedAt WHERE id = :sessionId")
    suspend fun touchSession(sessionId: String, updatedAt: Long)

    @Query("UPDATE ai_chat_messages SET content = :content WHERE id = :messageId")
    suspend fun updateMessageContent(messageId: String, content: String)
}
