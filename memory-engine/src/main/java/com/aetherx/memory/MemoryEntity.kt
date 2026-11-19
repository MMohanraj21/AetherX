package com.aetherx.memory

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "aether_memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val category: String, // "preference", "user_fact", "interaction", "relationship"
    val memoryKey: String,
    val memoryValue: String,
    val confidence: Double = 1.0,
    val timestamp: Long = System.currentTimeMillis(),
    val isEncrypted: Boolean = true
)
