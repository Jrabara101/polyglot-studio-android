package com.example.data.cache

import android.content.Context
import com.example.data.model.DifficultyLevel
import com.example.data.model.TargetLanguage
import com.example.data.model.UserProfile

/**
 * Lightweight, uid-scoped local cache for the learner's profile summary so cold starts render
 * the right language theme, XP, and streak immediately and remain usable offline before
 * Firestore emits its first snapshot.
 */
class ProfileSummaryCache(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(uid: String): UserProfile? {
        if (uid.isBlank() || !prefs.getBoolean(key(uid, "present"), false)) return null
        val displayName = prefs.getString(key(uid, "displayName"), null).orEmpty().ifBlank { "Polyglot Learner" }
        val activeLanguage = TargetLanguage.fromCode(prefs.getString(key(uid, "activeLanguage"), null)).code
        val difficulty = DifficultyLevel.fromCode(prefs.getString(key(uid, "difficulty"), null)).code
        val totalXp = prefs.getLong(key(uid, "totalXp"), 0L).coerceAtLeast(0L)
        val currentStreak = prefs.getLong(key(uid, "currentStreak"), 0L).coerceAtLeast(0L)
        val lastActiveDate = prefs.getString(key(uid, "lastActiveDate"), "").orEmpty()
        return UserProfile(
            id = uid,
            userId = uid,
            displayName = displayName,
            activeLanguage = activeLanguage,
            difficulty = difficulty,
            totalXp = totalXp,
            currentStreak = currentStreak,
            lastActiveDate = lastActiveDate
        )
    }

    fun write(profile: UserProfile) {
        val uid = profile.userId.ifBlank { profile.id }
        if (uid.isBlank()) return
        prefs.edit()
            .putBoolean(key(uid, "present"), true)
            .putString(key(uid, "displayName"), profile.displayName)
            .putString(key(uid, "activeLanguage"), TargetLanguage.fromCode(profile.activeLanguage).code)
            .putString(key(uid, "difficulty"), DifficultyLevel.fromCode(profile.difficulty).code)
            .putLong(key(uid, "totalXp"), profile.totalXp.coerceAtLeast(0L))
            .putLong(key(uid, "currentStreak"), profile.currentStreak.coerceAtLeast(0L))
            .putString(key(uid, "lastActiveDate"), profile.lastActiveDate)
            .apply()
    }

    fun updateLanguage(uid: String, language: TargetLanguage) {
        val existing = read(uid) ?: return
        write(existing.copy(activeLanguage = language.code))
    }

    fun updateDifficulty(uid: String, difficulty: DifficultyLevel) {
        val existing = read(uid) ?: return
        write(existing.copy(difficulty = difficulty.code))
    }

    fun clear(uid: String? = null) {
        if (uid == null) {
            prefs.edit().clear().apply()
            return
        }
        val editor = prefs.edit()
        for (suffix in FIELDS) {
            editor.remove(key(uid, suffix))
        }
        editor.apply()
    }

    private fun key(uid: String, suffix: String): String = "${uid}_$suffix"

    companion object {
        const val PREFS_NAME = "polyglot_profile_cache"
        private val FIELDS = listOf(
            "present",
            "displayName",
            "activeLanguage",
            "difficulty",
            "totalXp",
            "currentStreak",
            "lastActiveDate"
        )
    }
}
