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
    entities = [ConversationEntity::class, MessageEntity::class],
    version = 2,
    exportSchema = false,
)
@TypeConverters(AttachmentConverter::class)
abstract class GuzzoBotDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN attachments TEXT NOT NULL DEFAULT '[]'")
            }
        }

        fun create(context: Context): GuzzoBotDatabase =
            Room.databaseBuilder(context, GuzzoBotDatabase::class.java, "guzzobot.db")
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
    }
}
