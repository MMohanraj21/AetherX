package com.jarvis.assistant.data.memory

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val category: String,
    val key: String,
    val value: String,
    val rawText: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        const val CATEGORY_USER_PROFILE = "UserProfile"
        const val CATEGORY_PREFERENCES = "Preferences"
        const val CATEGORY_EDUCATION = "Education"
        const val CATEGORY_CAREER = "Career"
        const val CATEGORY_PROJECTS = "Projects"
        const val CATEGORY_GOALS = "Goals"
        const val CATEGORY_RELATIONSHIPS = "Relationships"
        const val CATEGORY_TECH_PREFERENCES = "TechPreferences"
        const val CATEGORY_PLANS = "Plans"
        const val CATEGORY_EVENTS = "Events"
        const val CATEGORY_RECURRING_TASKS = "RecurringTasks"
        const val CATEGORY_SUMMARIES = "Summaries"
        const val CATEGORY_CORRECTIONS = "Corrections"
        const val CATEGORY_CONTEXT = "Context"

        // Backward compatibility constants
        const val CATEGORY_PERSONAL = CATEGORY_USER_PROFILE
        const val CATEGORY_CONTACTS = CATEGORY_RELATIONSHIPS
        const val CATEGORY_ROUTINES = CATEGORY_RECURRING_TASKS
        const val CATEGORY_NOTES = CATEGORY_CONTEXT

        val ALL_CATEGORIES = listOf(
            CATEGORY_USER_PROFILE,
            CATEGORY_PREFERENCES,
            CATEGORY_EDUCATION,
            CATEGORY_CAREER,
            CATEGORY_PROJECTS,
            CATEGORY_GOALS,
            CATEGORY_RELATIONSHIPS,
            CATEGORY_TECH_PREFERENCES,
            CATEGORY_PLANS,
            CATEGORY_EVENTS,
            CATEGORY_RECURRING_TASKS,
            CATEGORY_SUMMARIES,
            CATEGORY_CORRECTIONS,
            CATEGORY_CONTEXT
        )
    }

    val formattedDate: String
        get() {
            val sdf = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
}
