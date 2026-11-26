package com.example.ui.speech

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.ai.FirebaseAiRepository
import com.example.data.content.CuratedContent
import com.example.data.model.TargetLanguage
import com.example.ui.theme.PolyglotStudioTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class SpeechGuideBookmarkTest {

    @get:Rule
    val compose = createComposeRule()

    private val allowedFirestoreSourceGames = setOf(
        "cards", "calligraphy", "quiz", "voice", "story", "speech", "tutor"
    )

    @Test
    fun bookmark_uses_firestore_allowed_source_game_and_shows_error_when_save_fails() {
        val capturedSources = mutableListOf<String>()
        var failNext = true

        compose.setContent {
            PolyglotStudioTheme(activeLanguage = TargetLanguage.FRENCH, darkTheme = false) {
                SpeechGuideScreen(
                    selectedLanguage = TargetLanguage.FRENCH,
                    onSelectLanguage = {},
                    aiRepository = FirebaseAiRepository(),
                    speakingKey = null,
                    ttsError = null,
                    onSpeak = { _, _, _, _ -> },
                    onSavePhrase = { _, _, _, _, sourceGame ->
                        capturedSources += sourceGame
                        if (failNext) Result.failure(IOException("Permission denied"))
                        else Result.success("phrase_1")
                    }
                )
            }
        }

        // First attempt fails -> must pass valid sourceGame ("speech") and show error banner.
        compose.onNodeWithTag("phoneme_bookmark_fr_p1").performClick()
        compose.waitForIdle()

        assertEquals(listOf("speech"), capturedSources)
        assertTrue(
            "sourceGame must match firestore.rules isValidSourceGame",
            capturedSources.all { it in allowedFirestoreSourceGames }
        )
        compose.onNodeWithText("Permission denied", substring = true).assertExists()

        // Second attempt succeeds -> error clears.
        failNext = false
        compose.onNodeWithTag("phoneme_bookmark_fr_p1").performClick()
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithText("Permission denied", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun speech_guide_sanitizes_slash_separated_examples_for_tts() {
        val frenchU = CuratedContent.phonemesFor(TargetLanguage.FRENCH).first { it.id == "fr_p2" }
        assertEquals("tu, rue", frenchU.speechExampleText())
    }
}
