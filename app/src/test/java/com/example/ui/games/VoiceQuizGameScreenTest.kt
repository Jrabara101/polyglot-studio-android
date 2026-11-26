package com.example.ui.games

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.ai.FirebaseAiRepository
import com.example.data.model.DifficultyLevel
import com.example.data.model.GameType
import com.example.data.model.TargetLanguage
import com.example.ui.theme.PolyglotStudioTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class VoiceQuizGameScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun calculateVoiceRoundResult_ignores_skips_and_averages_graded_only() {
        assertEquals(0 to 0, calculateVoiceRoundResult(emptyList()))
        assertEquals(85 to 68, calculateVoiceRoundResult(listOf(80, 90)))
        assertEquals(100 to 73, calculateVoiceRoundResult(listOf(100)))
    }

    @Test
    fun skipping_all_phrases_records_zero_and_skips_cloud_save() {
        val savedCalls = mutableListOf<Pair<Int, Int>>()

        compose.setContent {
            PolyglotStudioTheme(activeLanguage = TargetLanguage.SPANISH, darkTheme = false) {
                VoiceQuizGameScreen(
                    language = TargetLanguage.SPANISH,
                    difficulty = DifficultyLevel.BEGINNER,
                    aiRepository = FirebaseAiRepository(),
                    speakingKey = null,
                    onSpeak = { _, _, _, _ -> },
                    onSavePhrase = { _, _, _, _, _ -> Result.success("ok") },
                    onCompleteSession = { _, _, _, score, xp, _, _ ->
                        savedCalls += score to xp
                    },
                    onBack = {}
                )
            }
        }

        // Beginner Spanish has 3 voice prompts; skipping all 3 should not invent a 60% score or save XP.
        repeat(3) {
            compose.onNodeWithTag("voice_next_button").performClick()
            compose.waitForIdle()
        }

        compose.onNodeWithText("Nothing to save").assertExists()
        compose.onNodeWithText("0%").assertExists()
        compose.onNodeWithText("+0 XP").assertExists()
        assertEquals(emptyList<Pair<Int, Int>>(), savedCalls)
    }

    @Test
    fun quiz_save_failure_does_not_crash_and_can_be_retried() {
        var saveError: Exception? = IllegalStateException("offline")
        val saved = mutableListOf<GameType>()

        compose.setContent {
            PolyglotStudioTheme(activeLanguage = TargetLanguage.SPANISH, darkTheme = false) {
                QuizGameScreen(
                    language = TargetLanguage.SPANISH,
                    difficulty = DifficultyLevel.ADVANCED, // 2 curated questions
                    aiRepository = FirebaseAiRepository(),
                    speakingKey = null,
                    onSpeak = { _, _, _, _ -> },
                    onCompleteSession = { type, _, _, _, _, _, _ ->
                        saveError?.let { throw it }
                        saved += type
                    },
                    onBack = {}
                )
            }
        }

        repeat(2) {
            compose.onNodeWithTag("quiz_option_1").performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("quiz_next_button").performClick()
            compose.waitForIdle()
        }

        compose.onNodeWithText("Not saved yet").assertExists()
        compose.onNodeWithTag("retry_save_button").assertExists()
        assertTrue(saved.isEmpty())

        saveError = null
        compose.onNodeWithTag("retry_save_button").performClick()
        compose.waitForIdle()

        assertEquals(listOf(GameType.QUIZ), saved)
        assertTrue(compose.onAllNodesWithText("Not saved yet").fetchSemanticsNodes().isEmpty())
    }
}
