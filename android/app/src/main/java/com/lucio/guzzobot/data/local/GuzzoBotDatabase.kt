package com.lucio.guzzobot.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lucio.guzzobot.data.model.Attachment
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AttachmentConverter {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @TypeConverter
    fun fromList(value: List<Attachment>): String = json.encodeToString(value)

    @TypeConverter
    fun toList(value: String?): List<Attachment> =
        if (value.isNullOrBlank()) {
            emptyList()
        } else {
            runCatching { json.decodeFromString<List<Attachment>>(value) }.getOrDefault(emptyList())
        }
}

@Database(
    entities = [ConversationEntity::class, MessageEntity::class, ScheduledTaskEntity::class],
    version = 4,
    exportSchema = false,
)
@TypeConverters(AttachmentConverter::class)
abstract class GuzzoBotDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun taskDao(): TaskDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN attachments TEXT NOT NULL DEFAULT '[]'")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN toolCalls TEXT")
                db.execSQL("ALTER TABLE messages ADD COLUMN toolCallId TEXT")
                db.execSQL("ALTER TABLE messages ADD COLUMN toolName TEXT")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS tasks (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "title TEXT NOT NULL, goal TEXT NOT NULL, " +
                        "everyHours INTEGER NOT NULL, mayWrite INTEGER NOT NULL, " +
                        "enabled INTEGER NOT NULL, lastRunAt INTEGER, lastOutcome TEXT)",
                )
            }
        }

        fun create(context: Context): GuzzoBotDatabase =
            Room.databaseBuilder(context, GuzzoBotDatabase::class.java, "guzzobot.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
    }
}
