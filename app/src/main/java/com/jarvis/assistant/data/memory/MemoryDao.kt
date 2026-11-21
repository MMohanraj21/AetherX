package com.jarvis.assistant.data.memory

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {

    @Query("SELECT * FROM memories ORDER BY timestamp DESC")
    fun getAllMemoriesFlow(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories ORDER BY timestamp DESC")
    suspend fun getAllMemories(): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE category = :category ORDER BY timestamp DESC")
    fun getMemoriesByCategoryFlow(category: String): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE rawText LIKE '%' || :query || '%' OR `key` LIKE '%' || :query || '%' OR `value` LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchMemoriesFlow(query: String): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE rawText LIKE '%' || :query || '%' OR `key` LIKE '%' || :query || '%' OR `value` LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    suspend fun searchMemories(query: String): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntity): Long

    @Update
    suspend fun updateMemory(memory: MemoryEntity)

    @Delete
    suspend fun deleteMemory(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteMemoryById(id: Long)

    @Query("DELETE FROM memories WHERE rawText LIKE '%' || :keyword || '%' OR `key` LIKE '%' || :keyword || '%' OR `value` LIKE '%' || :keyword || '%'")
    suspend fun deleteMemoriesByKeyword(keyword: String): Int

    @Query("SELECT * FROM memories WHERE isOutdated = 0 ORDER BY timestamp DESC")
    suspend fun getActiveMemories(): List<MemoryEntity>

    @Query("SELECT * FROM memories ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMemories(limit: Int = 10): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE importance >= :minImportance AND isOutdated = 0 ORDER BY importance DESC, timestamp DESC")
    suspend fun getImportantMemories(minImportance: Int = 2): List<MemoryEntity>

    @Query("UPDATE memories SET isOutdated = 1 WHERE id = :id")
    suspend fun markMemoryOutdated(id: Long)

    @Query("DELETE FROM memories")
    suspend fun clearAllMemories()
}
