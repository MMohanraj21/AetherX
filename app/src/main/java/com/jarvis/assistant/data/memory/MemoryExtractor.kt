package com.jarvis.assistant.data.memory

import android.util.Log
import java.util.Locale

/**
 * Autonomous Memory Extractor for ZARA AI.
 * Inspects user conversation turns and automatically identifies, classifies,
 * and extracts persistent facts across the 14 memory categories:
 * UserProfile, Preferences, Education, Career, Projects, Goals, Relationships,
 * TechPreferences, Plans, Events, RecurringTasks, Summaries, Corrections, Context.
 */
object MemoryExtractor {

    private const val TAG = "ZaraAssistant"

    data class ExtractedFact(
        val category: String,
        val key: String,
        val value: String,
        val rawText: String
    )

    fun extract(userUtterance: String): List<ExtractedFact> {
        val clean = userUtterance.trim()
        if (clean.length < 5 || clean.length > 300) return emptyList()

        val lower = clean.lowercase(Locale.ROOT)
        val results = mutableListOf<ExtractedFact>()

        // 1. UserProfile (Name, Age, Location, Birthday)
        val nameMatch = Regex("(?i)\\b(?:my\\s+name\\s+is|i\\s+am|call\\s+me|en\\s+peyar|mera\\s+naam)\\s+([A-Za-z]{2,20})\\b").find(clean)
        if (nameMatch != null) {
            val name = nameMatch.groupValues[1].replaceFirstChar { it.uppercase() }
            if (name.lowercase() !in listOf("zara", "jarvis", "listening", "speaking", "here", "ready")) {
                results.add(ExtractedFact(MemoryEntity.CATEGORY_USER_PROFILE, "user_name", name, "User's name is $name"))
            }
        }

        val ageMatch = Regex("(?i)\\b(?:i\\s+am|i'm|age\\s+is)\\s+(\\d{1,2})\\s*(?:years\\s+old|yrs\\s+old)?\\b").find(clean)
        if (ageMatch != null) {
            val age = ageMatch.groupValues[1]
            results.add(ExtractedFact(MemoryEntity.CATEGORY_USER_PROFILE, "user_age", age, "User is $age years old"))
        }

        val liveMatch = Regex("(?i)\\b(?:i\\s+live\\s+in|i'm\\s+from|my\\s+hometown\\s+is|naan\\s+([A-Za-z]+)\\s+la\\s+irukken)\\s+([A-Za-z\\s]{2,25})\\b").find(clean)
        if (liveMatch != null) {
            val place = (liveMatch.groupValues.getOrNull(2) ?: liveMatch.groupValues[1]).trim()
            results.add(ExtractedFact(MemoryEntity.CATEGORY_USER_PROFILE, "user_location", place, "User lives in $place"))
        }

        // 2. Preferences (Favorite music, food, color, sports)
        val favMatch = Regex("(?i)\\b(?:my\\s+favou?rite|i\\s+love|i\\s+prefer|i\\s+really\\s+like|enakku\\s+pidicha)\\s+([A-Za-z0-9\\s]{2,20})\\s+(?:is|are|=|irukku)\\s+([A-Za-z0-9\\s]{2,40})").find(clean)
        if (favMatch != null) {
            val topic = favMatch.groupValues[1].trim()
            val value = favMatch.groupValues[2].trim()
            results.add(ExtractedFact(MemoryEntity.CATEGORY_PREFERENCES, "pref_${topic.take(15)}", value, "User's favorite $topic is $value"))
        }

        // 3. Education (School, College, Degree)
        val eduMatch = Regex("(?i)\\b(?:i\\s+study\\s+at|i'm\\s+studying\\s+at|my\\s+college\\s+is|my\\s+school\\s+is|i\\s+graduated\\s+from|doing\\s+my\\s+([A-Za-z]+)\\s+in|pursuing)\\s+([A-Za-z0-9\\s]{2,40})").find(clean)
        if (eduMatch != null) {
            val institution = eduMatch.groupValues[eduMatch.groupValues.size - 1].trim()
            results.add(ExtractedFact(MemoryEntity.CATEGORY_EDUCATION, "education_info", institution, "User studied/studies at $institution"))
        }

        // 4. Career (Job, Company, Role)
        val careerMatch = Regex("(?i)\\b(?:i\\s+work\\s+at|i'm\\s+working\\s+at|my\\s+job\\s+is|i\\s+am\\s+a\\s+([A-Za-z]+)\\s+engineer|i\\s+am\\s+a\\s+([A-Za-z]+)\\s+developer|working\\s+as\\s+(?:a\\s+)?)([A-Za-z0-9\\s]{2,30})").find(clean)
        if (careerMatch != null) {
            val role = careerMatch.groupValues[careerMatch.groupValues.size - 1].trim()
            results.add(ExtractedFact(MemoryEntity.CATEGORY_CAREER, "career_role", role, "User works as/at $role"))
        }

        // 5. Projects (AetherX, ZARA, Apps, Repositories)
        val projMatch = Regex("(?i)\\b(?:working\\s+on\\s+(?:a\\s+)?project|building\\s+(?:a\\s+)?project|my\\s+project\\s+is|developing)\\s+([A-Za-z0-9\\s]{2,30})").find(clean)
        if (projMatch != null) {
            val proj = projMatch.groupValues[1].trim()
            results.add(ExtractedFact(MemoryEntity.CATEGORY_PROJECTS, "project_${proj.take(15)}", proj, "User is developing project $proj"))
        }

        // 6. Goals (Aspirations, Short-term, Long-term)
        val goalMatch = Regex("(?i)\\b(?:my\\s+goal\\s+is\\s+to|i\\s+want\\s+to\\s+become|aiming\\s+to|planning\\s+to\\s+achieve)\\s+([A-Za-z0-9\\s]{3,50})").find(clean)
        if (goalMatch != null) {
            val goal = goalMatch.groupValues[1].trim()
            results.add(ExtractedFact(MemoryEntity.CATEGORY_GOALS, "user_goal", goal, "User's goal: $goal"))
        }

        // 7. Relationships (Family, Friends, Colleagues)
        val relMatch = Regex("(?i)\\b(?:my\\s+(brother|sister|mom|mother|dad|father|friend|best\\s+friend|wife|husband|gf|girlfriend|bf|boyfriend)\\s+(?:is|name\\s+is|=|peyar))\\s+([A-Za-z]{2,20})").find(clean)
        if (relMatch != null) {
            val relation = relMatch.groupValues[1].trim()
            val relName = relMatch.groupValues[2].trim()
            results.add(ExtractedFact(MemoryEntity.CATEGORY_RELATIONSHIPS, "rel_${relation.replace(" ", "_")}", relName, "User's $relation is $relName"))
        }

        // 8. TechPreferences (Languages, Frameworks, OS, Editors)
        val techMatch = Regex("(?i)\\b(?:my\\s+favou?rite\\s+(?:tech|language|os|editor|ide|framework)|i\\s+code\\s+in|i\\s+prefer\\s+(?:using\\s+)?(kotlin|python|android|linux|termux|neovim|flutter|react))\\s*([A-Za-z0-9\\s]{0,20})").find(clean)
        if (techMatch != null) {
            val tech = if (techMatch.groupValues[1].isNotBlank()) techMatch.groupValues[1] else techMatch.groupValues[2]
            results.add(ExtractedFact(MemoryEntity.CATEGORY_TECH_PREFERENCES, "tech_pref", tech.trim(), "User tech preference: ${tech.trim()}"))
        }

        // 9. Plans (Upcoming travel, visits)
        val planMatch = Regex("(?i)\\b(?:planning\\s+to\\s+visit|going\\s+to|traveling\\s+to|trip\\s+to)\\s+([A-Za-z0-9\\s]{2,30})").find(clean)
        if (planMatch != null) {
            val place = planMatch.groupValues[1].trim()
            results.add(ExtractedFact(MemoryEntity.CATEGORY_PLANS, "travel_plan", place, "User plans to visit $place"))
        }

        // 10. RecurringTasks / Routines (Daily habits)
        val routineMatch = Regex("(?i)\\b(?:i\\s+(?:usually\\s+)?wake\\s+up\\s+at|i\\s+go\\s+to\\s+(?:gym|bed|sleep|work)\\s+at)\\s+([A-Za-z0-9:\\s]{2,20})").find(clean)
        if (routineMatch != null) {
            val routine = routineMatch.groupValues[0].trim()
            results.add(ExtractedFact(MemoryEntity.CATEGORY_RECURRING_TASKS, "daily_routine", routine, "User routine: $routine"))
        }

        // 11. Corrections (User explicitly correcting ZARA)
        val correctionMatch = Regex("(?i)\\b(?:no\\s+that's\\s+wrong|not\\s+that|i\\s+said|i\\s+meant|don't\\s+call\\s+me\\s+([A-Za-z]+),\\s*call\\s+me\\s+([A-Za-z]+)|actually\\s+my\\s+([A-Za-z]+)\\s+is)\\s+([A-Za-z0-9\\s]{2,30})").find(clean)
        if (correctionMatch != null) {
            results.add(ExtractedFact(MemoryEntity.CATEGORY_CORRECTIONS, "user_correction", clean, "User correction: $clean"))
        }

        for (item in results) {
            Log.i(TAG, "MEMORY_EXTRACTED: category='${item.category}', key='${item.key}', value='${item.value}'")
        }

        return results
    }
}
