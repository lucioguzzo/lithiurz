package com.lucio.guzzobot.data.repo

import com.lucio.guzzobot.data.local.ChatDao
import com.lucio.guzzobot.data.local.ConversationEntity
import com.lucio.guzzobot.data.local.MessageEntity
import com.lucio.guzzobot.data.model.Attachment
import com.lucio.guzzobot.data.model.Roles
import kotlinx.coroutines.flow.Flow

class ChatRepository(private val dao: ChatDao) {

    fun conversations(): Flow<List<ConversationEntity>> = dao.observeConversations()

    fun messages(conversationId: Long): Flow<List<MessageEntity>> = dao.observeMessages(conversationId)

    suspend fun conversation(id: Long): ConversationEntity? = dao.conversation(id)

    suspend fun createConversation(providerId: String, model: String, title: String): Long {
        val now = System.currentTimeMillis()
        return dao.insertConversation(
            ConversationEntity(
                title = title.ifBlank { "Nuova chat" },
                providerId = providerId,
                model = model,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun addMessage(
        conversationId: Long,
        role: String,
        content: String,
        reasoning: String? = null,
        model: String? = null,
        isError: Boolean = false,
        attachments: List<Attachment> = emptyList(),
    ): Long {
        val id = dao.insertMessage(
            MessageEntity(
                conversationId = conversationId,
                role = role,
                content = content,
                reasoning = reasoning?.takeIf { it.isNotBlank() },
                model = model,
                isError = isError,
                attachments = attachments,
                createdAt = System.currentTimeMillis(),
            ),
        )
        dao.touchConversation(conversationId, System.currentTimeMillis())
        return id
    }

    suspend fun history(conversationId: Long): List<MessageEntity> = dao.messages(conversationId)

    suspend fun rename(conversationId: Long, title: String) =
        dao.renameConversation(conversationId, title.trim().take(120))

    suspend fun setModel(conversationId: Long, providerId: String, model: String) =
        dao.setConversationModel(conversationId, providerId, model)

    suspend fun delete(conversationId: Long) = dao.deleteConversation(conversationId)

    suspend fun deleteAll() = dao.deleteAllConversations()

    suspend fun deleteFrom(conversationId: Long, messageId: Long) =
        dao.deleteMessagesFrom(conversationId, messageId)

    suspend fun deleteTrailingAssistant(conversationId: Long) {
        val messages = dao.messages(conversationId)
        val lastUser = messages.indexOfLast { it.role == Roles.USER }
        if (lastUser == -1) return
        messages.drop(lastUser + 1).forEach { dao.deleteMessage(it.id) }
    }

    suspend fun isEmpty(conversationId: Long): Boolean = dao.messageCount(conversationId) == 0
}
