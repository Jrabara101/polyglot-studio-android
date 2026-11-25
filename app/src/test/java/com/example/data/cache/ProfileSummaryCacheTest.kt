package com.example.data.cache

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.DifficultyLevel
import com.example.data.model.TargetLanguage
import com.example.data.model.UserProfile
import com.example.ui.progress.formatCountLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProfileSummaryCacheTest {

    private lateinit var context: Context
    private lateinit var cache: ProfileSummaryCache

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(ProfileSummaryCache.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        cache = ProfileSummaryCache(context)
    }

    @Test
    fun write_and_read_round_trips_profile_for_same_uid() {
        val original = UserProfile(
            id = "u1",
            userId = "u1",
            displayName = "Polyglot Learner",
            activeLanguage = TargetLanguage.SPANISH.code,
            difficulty = DifficultyLevel.INTERMEDIATE.code,
            totalXp = 709L,
            currentStreak = 2L,
            lastActiveDate = "2026-11-25"
        )
        cache.write(original)

        val restored = cache.read("u1")
        assertNotNull(restored)
        assertEquals("u1", restored!!.userId)
        assertEquals("Polyglot Learner", restored.displayName)
        assertEquals(TargetLanguage.SPANISH.code, restored.activeLanguage)
        assertEquals(DifficultyLevel.INTERMEDIATE.code, restored.difficulty)
        assertEquals(709L, restored.totalXp)
        assertEquals(2L, restored.currentStreak)
        assertEquals("2026-11-25", restored.lastActiveDate)
    }

    @Test
    fun read_for_different_uid_returns_null_and_clear_evicts() {
        cache.write(UserProfile(userId = "u1", displayName = "Alice", totalXp = 50L))
        assertNull(cache.read("u2"))

        cache.clear("u1")
        assertNull(cache.read("u1"))
    }

    @Test
    fun corrupted_language_and_difficulty_codes_fall_back_safely() {
        context.getSharedPreferences(ProfileSummaryCache.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("u1_present", true)
            .putString("u1_displayName", "CorruptUser")
            .putString("u1_activeLanguage", "klingon")
            .putString("u1_difficulty", "impossible")
            .putLong("u1_totalXp", -10L)
            .putLong("u1_currentStreak", -5L)
            .commit()

        val restored = cache.read("u1")
        assertNotNull(restored)
        assertEquals(TargetLanguage.JAPANESE.code, restored!!.activeLanguage)
        assertEquals(DifficultyLevel.BEGINNER.code, restored.difficulty)
        assertEquals(0L, restored.totalXp)
        assertEquals(0L, restored.currentStreak)
    }

    @Test
    fun formatCountLabel_handles_singular_and_plural() {
        assertEquals("1 game", formatCountLabel(1L, "game", "games"))
        assertEquals("0 games", formatCountLabel(0L, "game", "games"))
        assertEquals("5 games", formatCountLabel(5L, "game", "games"))
        assertEquals("1 day", formatCountLabel(1L, "day", "days"))
        assertEquals("2 days", formatCountLabel(2L, "day", "days"))
    }
}
