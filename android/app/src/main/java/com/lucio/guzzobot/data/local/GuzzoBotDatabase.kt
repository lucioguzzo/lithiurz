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
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        ScheduledTaskEntity::class,
        ProjectEntity::class,
        ArtifactEntity::class,
    ],
    version = 7,
    exportSchema = false,
)
@TypeConverters(AttachmentConverter::class)
abstract class GuzzoBotDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun taskDao(): TaskDao
    abstract fun projectDao(): ProjectDao
    abstract fun artifactDao(): ArtifactDao

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

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE conversations ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL("ALTER TABLE conversations ADD COLUMN projectId INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS projects (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, createdAt INTEGER NOT NULL)",
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS artifacts (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "conversationId INTEGER, kind TEXT NOT NULL, name TEXT NOT NULL, " +
                        "location TEXT NOT NULL, detail TEXT, createdAt INTEGER NOT NULL)",
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN skillId TEXT")
            }
        }

        fun create(context: Context): GuzzoBotDatabase =
            Room.databaseBuilder(context, GuzzoBotDatabase::class.java, "guzzobot.db")
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                )
                .fallbackToDestructiveMigration()
                .build()
    }
}
