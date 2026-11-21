package com.jarvis.assistant.data.memory

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MemoryEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        ConversationSummaryEntity::class,
        TaskContextEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class MemoryDatabase : RoomDatabase() {

    abstract fun memoryDao(): MemoryDao
    abstract fun conversationDao(): ConversationDao

    companion object {
        private const val DATABASE_NAME = "jarvis_memory.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Add new columns to memories table
                try {
                    db.execSQL("ALTER TABLE memories ADD COLUMN lastUpdated INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE memories ADD COLUMN sourceConversationId TEXT NOT NULL DEFAULT ''")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE memories ADD COLUMN importance INTEGER NOT NULL DEFAULT 1")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE memories ADD COLUMN confidence REAL NOT NULL DEFAULT 1.0")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE memories ADD COLUMN isOutdated INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE memories ADD COLUMN expirationTime INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}

                // 2. Create conversations table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `conversations` (
                        `conversationId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `startTime` INTEGER NOT NULL,
                        `endTime` INTEGER NOT NULL,
                        `summary` TEXT NOT NULL,
                        `turnCount` INTEGER NOT NULL,
                        PRIMARY KEY(`conversationId`)
                    )
                """.trimIndent())

                // 3. Create messages table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `messages` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `conversationId` TEXT NOT NULL,
                        `sender` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_conversationId` ON `messages` (`conversationId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_timestamp` ON `messages` (`timestamp`)")

                // 4. Create conversation_summaries table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `conversation_summaries` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `conversationId` TEXT NOT NULL,
                        `summaryText` TEXT NOT NULL,
                        `keyTakeaways` TEXT NOT NULL,
                        `topics` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_conversation_summaries_conversationId` ON `conversation_summaries` (`conversationId`)")

                // 5. Create task_contexts table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `task_contexts` (
                        `taskId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `goal` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `contextJson` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`taskId`)
                    )
                """.trimIndent())
            }
        }

        @Volatile
        private var instance: MemoryDatabase? = null

        fun getInstance(context: Context): MemoryDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MemoryDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
