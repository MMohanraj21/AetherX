package com.jarvis.assistant.organizer.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [NoteEntity::class, ReminderEntity::class, TaskEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ZaraOrganizerDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao
    abstract fun reminderDao(): ReminderDao
    abstract fun taskDao(): TaskDao

    companion object {
        private const val DB_NAME = "zara_organizer.db"

        @Volatile
        private var instance: ZaraOrganizerDatabase? = null

        fun getInstance(context: Context): ZaraOrganizerDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ZaraOrganizerDatabase::class.java,
                    DB_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
