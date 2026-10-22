package com.lucio.lith.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ConversationEntity::class, MessageEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class LithDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        fun create(context: Context): LithDatabase =
            Room.databaseBuilder(context, LithDatabase::class.java, "lith.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
