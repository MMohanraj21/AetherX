package com.aetherx.memory

import android.content.Context
import com.aetherx.security.KeyStoreEncryption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AetherMemoryRepository(context: Context) {
    private val database = AetherMemoryDatabase.getInstance(context)
    private val memoryDao = database.memoryDao()

    fun getAllMemories(): Flow<List<MemoryEntity>> {
        return memoryDao.getAllMemories().map { list ->
            list.map { item ->
                if (item.isEncrypted) {
                    item.copy(
                        memoryValue = KeyStoreEncryption.decrypt(item.memoryValue)
                    )
                } else item
            }
        }
    }

    suspend fun saveFact(category: String, key: String, value: String, confidence: Double = 1.0) {
        val encryptedValue = KeyStoreEncryption.encrypt(value)
        val entity = MemoryEntity(
            category = category,
            memoryKey = key,
            memoryValue = encryptedValue,
            confidence = confidence,
            isEncrypted = true
        )
        memoryDao.insertMemory(entity)
    }

    suspend fun searchMemories(query: String): List<MemoryEntity> {
        val raw = memoryDao.searchMemories(query)
        return raw.map {
            if (it.isEncrypted) {
                it.copy(memoryValue = KeyStoreEncryption.decrypt(it.memoryValue))
            } else it
        }
    }

    suspend fun deleteMemory(id: Long) {
        memoryDao.deleteMemoryById(id)
    }

    suspend fun clearAll() {
        memoryDao.clearAll()
    }
}
