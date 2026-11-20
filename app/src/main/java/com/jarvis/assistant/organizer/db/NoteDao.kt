package com.jarvis.assistant.organizer.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM zara_notes ORDER BY isPinned DESC, updatedAt DESC")
    fun getAllNotesFlow(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM zara_notes ORDER BY isPinned DESC, updatedAt DESC")
    suspend fun getAllNotes(): List<NoteEntity>

    @Query("SELECT * FROM zara_notes WHERE title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    suspend fun searchNotes(query: String): List<NoteEntity>

    @Query("SELECT * FROM zara_notes WHERE category = :category ORDER BY updatedAt DESC")
    suspend fun getNotesByCategory(category: String): List<NoteEntity>

    @Query("SELECT * FROM zara_notes ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestNote(): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity): Long

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Delete
    suspend fun deleteNote(note: NoteEntity)

    @Query("DELETE FROM zara_notes WHERE id = :id")
    suspend fun deleteNoteById(id: Long)

    @Query("DELETE FROM zara_notes WHERE title LIKE '%' || :title || '%'")
    suspend fun deleteNoteByTitle(title: String): Int
}
