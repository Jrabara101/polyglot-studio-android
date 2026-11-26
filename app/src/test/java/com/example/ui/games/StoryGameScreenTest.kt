package com.example.ui.games

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.content.StoryCatalog
import com.example.data.model.DifficultyLevel
import com.example.data.model.GameType
import com.example.data.model.TargetLanguage
import com.example.ui.theme.PolyglotStudioTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class StoryGameScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() {
        StoryCatalog.init(ApplicationProvider.getApplicationContext<Context>())
    }

    @Test
    fun completes_3_turn_japanese_story_offline_with_branching_and_feedback() {
        val stories = StoryCatalog.stories(TargetLanguage.JAPANESE, DifficultyLevel.BEGINNER)
        assertEquals(12, stories.size)
        val firstStory = stories.first()
        val openingNode = firstStory.nodes.getValue(firstStory.startNodeId)
        val turn1Choice = openingNode.choices.first()
        val turn1Node = firstStory.nodes.getValue(turn1Choice.nextNodeId)

        val savedTypes = mutableListOf<GameType>()

        compose.setContent {
            PolyglotStudioTheme(activeLanguage = TargetLanguage.JAPANESE, darkTheme = false) {
                StoryGameScreen(
                    language = TargetLanguage.JAPANESE,
                    difficulty = DifficultyLevel.BEGINNER,
                    speakingKey = null,
                    onSpeak = { _, _, _, _ -> },
                    onSavePhrase = { _, _, _, _, _ -> Result.success("ok") },
                    onCompleteSession = { type, _, _, _, _, _, _ ->
                        savedTypes += type
                    },
                    onBack = {}
                )
            }
        }

        // Opening state: offline story picker, Turn 0/3, no custom text box or AI spinner
        compose.onNodeWithTag("story_picker_button").assertTextContains("Story 1/12", substring = true)
        compose.onNodeWithText("Turn 0/3").assertExists()
        compose.onNodeWithText(firstStory.title).assertExists()
        compose.onNodeWithText(openingNode.npcText).assertExists()
        assertTrue(compose.onAllNodesWithTag("story_custom_input").fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithText("Or type your own reply", substring = true).fetchSemanticsNodes().isEmpty())

        // Turn 1 -> instant host branch reply & naturalness feedback
        compose.onNodeWithTag("story_choice_0").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Turn 1/3").assertExists()
        compose.onNodeWithText(turn1Node.npcText).assertExists()
        compose.onNodeWithText("Naturalness ${turn1Choice.naturalness}%", substring = true).assertExists()

        // Turn 2 & Turn 3 -> terminal node n3 unlocks Complete Story button
        compose.onNodeWithTag("story_choice_1").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Turn 2/3").assertExists()

        compose.onNodeWithTag("story_choice_0").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Turn 3/3").assertExists()
        assertTrue(compose.onAllNodesWithTag("story_choice_0").fetchSemanticsNodes().isEmpty())

        compose.onNodeWithTag("story_complete_button").performClick()
        compose.waitForIdle()

        assertEquals(listOf(GameType.STORY), savedTypes)
        compose.onNodeWithTag("next_story_button").assertExists()
        compose.onNodeWithText("Next Story").assertExists()
    }

    @Test
    fun story_picker_and_next_story_switch_scenarios_cleanly() {
        val stories = StoryCatalog.stories(TargetLanguage.JAPANESE, DifficultyLevel.BEGINNER)
        val story2 = stories[1]
        val story3 = stories[2]

        compose.setContent {
            PolyglotStudioTheme(activeLanguage = TargetLanguage.JAPANESE, darkTheme = false) {
                StoryGameScreen(
                    language = TargetLanguage.JAPANESE,
                    difficulty = DifficultyLevel.BEGINNER,
                    speakingKey = null,
                    onSpeak = { _, _, _, _ -> },
                    onSavePhrase = { _, _, _, _, _ -> Result.success("ok") },
                    onCompleteSession = { _, _, _, _, _, _, _ -> },
                    onBack = {}
                )
            }
        }

        // Advance one turn mid-dialogue, then jump to Story 2 via picker
        compose.onNodeWithTag("story_choice_0").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Turn 1/3").assertExists()

        compose.onNodeWithTag("story_picker_button").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("story_picker_item_1").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("story_picker_button").assertTextContains("Story 2/12", substring = true)
        compose.onNodeWithText("Turn 0/3").assertExists()
        compose.onNodeWithText(story2.title).assertExists()
        compose.onNodeWithText(story2.nodes.getValue("n0").npcText).assertExists()

        // Complete Story 2 and click Next Story to advance to Story 3
        repeat(3) {
            compose.onNodeWithTag("story_choice_0").performClick()
            compose.waitForIdle()
        }
        compose.onNodeWithTag("story_complete_button").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("next_story_button").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("story_picker_button").assertTextContains("Story 3/12", substring = true)
        compose.onNodeWithText("Turn 0/3").assertExists()
        compose.onNodeWithText(story3.title).assertExists()
    }

    @Test
    fun switching_difficulty_mid_dialogue_resets_turns_and_loads_new_level() {
        var difficulty by mutableStateOf(DifficultyLevel.BEGINNER)
        val intermediateFirst = StoryCatalog.stories(TargetLanguage.JAPANESE, DifficultyLevel.INTERMEDIATE).first()

        compose.setContent {
            PolyglotStudioTheme(activeLanguage = TargetLanguage.JAPANESE, darkTheme = false) {
                StoryGameScreen(
                    language = TargetLanguage.JAPANESE,
                    difficulty = difficulty,
                    speakingKey = null,
                    onSpeak = { _, _, _, _ -> },
                    onSavePhrase = { _, _, _, _, _ -> Result.success("ok") },
                    onCompleteSession = { _, _, _, _, _, _, _ -> },
                    onBack = {}
                )
            }
        }

        compose.onNodeWithTag("story_choice_0").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Turn 1/3").assertExists()

        difficulty = DifficultyLevel.INTERMEDIATE
        compose.waitForIdle()

        compose.onNodeWithTag("story_picker_button").assertTextContains("Story 1/11", substring = true)
        compose.onNodeWithText("Turn 0/3").assertExists()
        compose.onNodeWithText(intermediateFirst.title).assertExists()
    }

    @Test
    fun speaker_button_speaks_host_line_via_kokoro_key_and_bookmark_saves_phrase() {
        val firstStory = StoryCatalog.stories(TargetLanguage.JAPANESE, DifficultyLevel.BEGINNER).first()
        val opening = firstStory.nodes.getValue("n0")

        var spokenKey: String? = null
        var spokenText: String? = null
        var spokenLang: TargetLanguage? = null
        var savedPhrase: Triple<String, String, String>? = null

        compose.setContent {
            PolyglotStudioTheme(activeLanguage = TargetLanguage.JAPANESE, darkTheme = false) {
                StoryGameScreen(
                    language = TargetLanguage.JAPANESE,
                    difficulty = DifficultyLevel.BEGINNER,
                    speakingKey = null,
                    onSpeak = { key, text, lang, _ ->
                        spokenKey = key
                        spokenText = text
                        spokenLang = lang
                    },
                    onSavePhrase = { _, phrase, phonetic, english, _ ->
                        savedPhrase = Triple(phrase, phonetic, english)
                        Result.success("ok")
                    },
                    onCompleteSession = { _, _, _, _, _, _, _ -> },
                    onBack = {}
                )
            }
        }

        compose.onNodeWithTag("story_speak_0").performClick()
        compose.onNodeWithTag("story_bookmark_0").performClick()
        compose.waitForIdle()

        val expectedKey = StoryCatalog.speakerKey(firstStory.id, "n0")
        assertEquals(expectedKey, spokenKey)
        assertEquals(opening.npcText, spokenText)
        assertEquals(TargetLanguage.JAPANESE, spokenLang)
        assertNotNull(
            "Expected speaker key to resolve to bundled Kokoro clip",
            StoryCatalog.audioAssetForSpeakerKey(TargetLanguage.JAPANESE, expectedKey)
        )
        assertEquals(Triple(opening.npcText, opening.npcPhonetic, opening.npcEnglish), savedPhrase)
    }

    @Test
    fun offline_save_failure_shows_retry_and_recovers() {
        var saveFailure: Exception? = IOException("Device offline")
        val saved = mutableListOf<GameType>()

        compose.setContent {
            PolyglotStudioTheme(activeLanguage = TargetLanguage.SPANISH, darkTheme = false) {
                StoryGameScreen(
                    language = TargetLanguage.SPANISH,
                    difficulty = DifficultyLevel.BEGINNER,
                    speakingKey = null,
                    onSpeak = { _, _, _, _ -> },
                    onSavePhrase = { _, _, _, _, _ -> Result.success("ok") },
                    onCompleteSession = { type, _, _, _, _, _, _ ->
                        saveFailure?.let { throw it }
                        saved += type
                    },
                    onBack = {}
                )
            }
        }

        repeat(3) {
            compose.onNodeWithTag("story_choice_0").performClick()
            compose.waitForIdle()
        }
        compose.onNodeWithTag("story_complete_button").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Not saved yet").assertExists()
        compose.onNodeWithTag("retry_save_button").assertExists()
        assertTrue(saved.isEmpty())

        saveFailure = null
        compose.onNodeWithTag("retry_save_button").performClick()
        compose.waitForIdle()

        assertEquals(listOf(GameType.STORY), saved)
        assertTrue(compose.onAllNodesWithText("Not saved yet").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Synced to Cloud").assertExists()
    }

    @Test
    fun completion_card_with_offline_error_is_scrollable_on_compact_height() {
        compose.setContent {
            PolyglotStudioTheme(activeLanguage = TargetLanguage.FRENCH, darkTheme = false) {
                Box(modifier = Modifier.size(width = 411.dp, height = 420.dp)) {
                    StoryGameScreen(
                        language = TargetLanguage.FRENCH,
                        difficulty = DifficultyLevel.BEGINNER,
                        speakingKey = null,
                        onSpeak = { _, _, _, _ -> },
                        onSavePhrase = { _, _, _, _, _ -> Result.success("ok") },
                        onCompleteSession = { _, _, _, _, _, _, _ ->
                            throw IOException("Device offline")
                        },
                        onBack = {}
                    )
                }
            }
        }

        repeat(3) {
            compose.onNodeWithTag("story_choice_0").performClick()
            compose.waitForIdle()
        }
        compose.onNodeWithTag("story_complete_button").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("retry_save_button").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("next_story_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun bookmark_failure_shows_inline_error_without_silent_swallow() {
        compose.setContent {
            PolyglotStudioTheme(activeLanguage = TargetLanguage.JAPANESE, darkTheme = false) {
                StoryGameScreen(
                    language = TargetLanguage.JAPANESE,
                    difficulty = DifficultyLevel.BEGINNER,
                    speakingKey = null,
                    onSpeak = { _, _, _, _ -> },
                    onSavePhrase = { _, _, _, _, _ ->
                        Result.failure(IllegalStateException("Phrasebook write rejected"))
                    },
                    onCompleteSession = { _, _, _, _, _, _, _ -> },
                    onBack = {}
                )
            }
        }

        compose.onNodeWithTag("story_bookmark_0").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("story_bookmark_error").assertIsDisplayed()
        compose.onNodeWithText("Phrasebook write rejected").assertIsDisplayed()
    }
}
