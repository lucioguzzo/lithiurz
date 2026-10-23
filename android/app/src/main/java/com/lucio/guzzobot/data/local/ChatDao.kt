package com.lucio.guzzobot.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun conversation(id: Long): ConversationEntity?

    @Insert
    suspend fun insertConversation(conversation: ConversationEntity): Long

    @Query("UPDATE conversations SET title = :title WHERE id = :id")
    suspend fun renameConversation(id: Long, title: String)

    @Query("UPDATE conversations SET updatedAt = :at WHERE id = :id")
    suspend fun touchConversation(id: Long, at: Long)

    @Query("UPDATE conversations SET providerId = :providerId, model = :model WHERE id = :id")
    suspend fun setConversationModel(id: Long, providerId: String, model: String)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: Long)

    @Query("DELETE FROM conversations")
    suspend fun deleteAllConversations()

    @Query("SELECT * FROM messages WHERE conversationId = :id ORDER BY id ASC")
    fun observeMessages(id: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :id ORDER BY id ASC")
    suspend fun messages(id: Long): List<MessageEntity>

    @Insert
    suspend fun insertMessage(message: MessageEntity): Long

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessage(id: Long)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND id >= :fromId")
    suspend fun deleteMessagesFrom(conversationId: Long, fromId: Long)

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :id")
    suspend fun messageCount(id: Long): Int
}

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks ORDER BY id DESC")
    fun observeTasks(): Flow<List<ScheduledTaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun task(id: Long): ScheduledTaskEntity?

    @Query("SELECT * FROM tasks WHERE enabled = 1")
    suspend fun enabledTasks(): List<ScheduledTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: ScheduledTaskEntity): Long

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE tasks SET lastRunAt = :at, lastOutcome = :outcome WHERE id = :id")
    suspend fun recordRun(id: Long, at: Long, outcome: String)
}
