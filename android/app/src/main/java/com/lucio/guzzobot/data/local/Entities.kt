package com.lucio.guzzobot.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lucio.guzzobot.data.model.Attachment

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val providerId: String,
    val model: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("conversationId")],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val role: String,
    val content: String,
    val reasoning: String? = null,
    val model: String? = null,
    @ColumnInfo(defaultValue = "0") val isError: Boolean = false,
    /** Allegati del messaggio, serializzati in JSON da [AttachmentConverter]. */
    val attachments: List<Attachment> = emptyList(),
    /** Chiamate agli strumenti chieste dal modello, in JSON (messaggi assistant). */
    val toolCalls: String? = null,
    /** A quale chiamata risponde il messaggio (messaggi con ruolo "tool"). */
    val toolCallId: String? = null,
    /** Nome dello strumento, per mostrarlo nell'elenco delle attività. */
    val toolName: String? = null,
    val createdAt: Long,
)

/** Un compito che GUZZOBOT esegue da solo a intervalli. */
@Entity(tableName = "tasks")
data class ScheduledTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val goal: String,
    /** Ogni quante ore ripeterlo: WorkManager non scende sotto i 15 minuti. */
    val everyHours: Int = 24,
    /** Senza nessuno che approvi, di norma il compito legge e riferisce soltanto. */
    val mayWrite: Boolean = false,
    val enabled: Boolean = true,
    val lastRunAt: Long? = null,
    val lastOutcome: String? = null,
)
