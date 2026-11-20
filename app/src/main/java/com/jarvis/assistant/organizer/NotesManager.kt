package com.jarvis.assistant.organizer

import android.content.Context
import com.jarvis.assistant.organizer.db.NoteEntity
import com.jarvis.assistant.organizer.db.ZaraOrganizerDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NotesManager(context: Context) {
    private val noteDao = ZaraOrganizerDatabase.getInstance(context).noteDao()

    suspend fun createNote(
        title: String,
        content: String,
        category: String = "General",
        isPinned: Boolean = false
    ): NoteEntity = withContext(Dispatchers.IO) {
        val note = NoteEntity(
            title = title.ifBlank { "Quick Note" },
            content = content,
            category = category,
            isPinned = isPinned
        )
        val id = noteDao.insertNote(note)
        note.copy(id = id)
    }

    suspend fun getAllNotes(): List<NoteEntity> = withContext(Dispatchers.IO) {
        noteDao.getAllNotes()
    }

    suspend fun searchNotes(query: String): List<NoteEntity> = withContext(Dispatchers.IO) {
        noteDao.searchNotes(query)
    }

    suspend fun getNotesByCategory(category: String): List<NoteEntity> = withContext(Dispatchers.IO) {
        noteDao.getNotesByCategory(category)
    }

    suspend fun getLatestNote(): NoteEntity? = withContext(Dispatchers.IO) {
        noteDao.getLatestNote()
    }

    suspend fun deleteNoteByTitle(title: String): Boolean = withContext(Dispatchers.IO) {
        noteDao.deleteNoteByTitle(title) > 0
    }

    suspend fun deleteNoteById(id: Long) = withContext(Dispatchers.IO) {
        noteDao.deleteNoteById(id)
    }
}
