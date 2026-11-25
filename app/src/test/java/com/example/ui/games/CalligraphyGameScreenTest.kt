package com.example.ui.games

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.example.data.ai.CalligraphyEvaluation
import com.example.data.ai.CalligraphyRequest
import com.example.data.model.DifficultyLevel
import com.example.data.model.GameType
import com.example.data.model.TargetLanguage
import com.example.ui.theme.PolyglotStudioTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Screen tests for Ink & Stroke Studio (see qa/ink-stroke-studio-findings.md).
 * Grading and saving are faked, so no network or Firebase is needed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class CalligraphyGameScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val spoken = mutableListOf<String>()
    private val gradeRequests = mutableListOf<CalligraphyRequest>()
    private val savedScores = mutableListOf<Int>()
    private var nextScore = 80
    private var gradingError: Exception? = null
    private var saveError: Exception? = null
    private var gradingGate: CompletableDeferred<Unit>? = null

    private val fakeGrade: suspend (CalligraphyRequest) -> CalligraphyEvaluation = { req ->
        gradeRequests += req
        gradingGate?.await()
        gradingError?.let { throw it }
        CalligraphyEvaluation(nextScore, nextScore >= 65, "Nice strokes.", "Keep it balanced.")
    }

    @Composable
    private fun Game(language: TargetLanguage, difficulty: DifficultyLevel) {
        PolyglotStudioTheme(activeLanguage = language, darkTheme = false) {
            CalligraphyGameScreen(
                language = language,
                difficulty = difficulty,
                onEvaluate = fakeGrade,
                speakingKey = null,
                onSpeak = { _, text, _, _ -> spoken += text },
                onCompleteSession = { type, _, _, score, _, _, _ ->
                    assertEquals(GameType.CALLIGRAPHY, type)
                    saveError?.let { throw it }
                    savedScores += score
                },
                onBack = {}
            )
        }
    }

    private fun showGame(
        language: TargetLanguage = TargetLanguage.JAPANESE,
        difficulty: DifficultyLevel = DifficultyLevel.BEGINNER
    ) = compose.setContent { Game(language, difficulty) }

    private fun drawStroke() {
        compose.onNodeWithTag("calligraphy_canvas").performTouchInput {
            swipe(Offset(width * 0.3f, height * 0.5f), Offset(width * 0.7f, height * 0.5f), durationMillis = 300)
        }
    }

    private fun tag(t: String) = compose.onNodeWithTag(t)

    private fun padWasEmptyOnCheck(): Boolean {
        tag("check_calligraphy_button").performClick()
        compose.waitForIdle()
        return compose.onAllNodesWithText("on the pad first!", substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    private fun drawCheckAndNext() {
        drawStroke()
        tag("check_calligraphy_button").performClick()
        compose.waitForIdle()
        tag("next_character_button").performClick()
        compose.waitForIdle()
    }

    // ---------- Pad basics ----------

    @Test
    fun check_on_empty_pad_asks_learner_to_draw_first() {
        showGame()
        assertTrue(padWasEmptyOnCheck())
        assertEquals(0, gradeRequests.size)
    }

    @Test
    fun undo_and_clear_are_disabled_until_something_is_drawn() { // finding 15
        showGame()
        tag("undo_stroke_button").assertIsNotEnabled()
        tag("clear_canvas_button").assertIsNotEnabled()
        drawStroke()
        tag("undo_stroke_button").assertIsEnabled()
        tag("clear_canvas_button").assertIsEnabled()
    }

    @Test
    fun undo_removes_the_only_stroke() {
        showGame()
        drawStroke()
        tag("undo_stroke_button").performClick()
        assertTrue(padWasEmptyOnCheck())
    }

    @Test
    fun guide_button_hides_and_shows_the_faded_character() {
        showGame()
        assertEquals(2, compose.onAllNodesWithText("あ").fetchSemanticsNodes().size)
        tag("toggle_guide_button").performClick()
        assertEquals(1, compose.onAllNodesWithText("あ").fetchSemanticsNodes().size)
        tag("toggle_guide_button").performClick()
        assertEquals(2, compose.onAllNodesWithText("あ").fetchSemanticsNodes().size)
    }

    @Test
    fun single_tap_draws_a_dot() { // finding 2
        showGame()
        tag("calligraphy_canvas").performTouchInput { click(center) }
        assertTrue("Tap was ignored", !padWasEmptyOnCheck())
        assertTrue(gradeRequests.single().strokeDescription.startsWith("1. dot at (50,50)"))
    }

    @Test
    fun drawing_survives_screen_rotation() { // finding 8
        val restore = StateRestorationTester(compose)
        restore.setContent { Game(TargetLanguage.JAPANESE, DifficultyLevel.BEGINNER) }
        drawStroke()
        restore.emulateSavedInstanceStateRestore()
        assertTrue("Drawing lost after rotation", !padWasEmptyOnCheck())
    }

    @Test
    fun progress_survives_screen_rotation() { // finding 8
        val restore = StateRestorationTester(compose)
        restore.setContent { Game(TargetLanguage.JAPANESE, DifficultyLevel.BEGINNER) }
        drawCheckAndNext()
        compose.onNodeWithText("2/6").assertExists()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("2/6").assertExists()
    }

    // ---------- Grading ----------

    @Test
    fun grading_sends_stroke_order_and_hint() { // finding 11
        showGame()
        drawStroke()
        tag("check_calligraphy_button").performClick()
        compose.waitForIdle()
        val req = gradeRequests.single()
        assertEquals("あ", req.targetChar)
        assertTrue(req.strokeHint.startsWith("3 strokes"))
        assertTrue(req.strokeDescription, req.strokeDescription.startsWith("1. line from (30,50) to (70,50), going right"))
        compose.onNodeWithText("Great Form! 80/100").assertExists()
        compose.onNodeWithText("You drew 1 stroke; 'あ' has 3.").assertExists() // app-side stroke count
    }

    @Test
    fun grading_failure_shows_friendly_message_and_skip_still_works() { // findings 1 and 4
        gradingError = IllegalStateException("Grading is busy right now. Please try again in a moment, or tap Skip.")
        showGame()
        drawStroke()
        tag("check_calligraphy_button").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Grading is busy", substring = true).assertExists()
        tag("skip_character_button").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("2/6").assertExists()
    }

    @Test
    fun pad_is_locked_while_grading() { // finding 14
        gradingGate = CompletableDeferred()
        showGame()
        drawStroke()
        tag("check_calligraphy_button").performClick()
        compose.waitForIdle()
        tag("undo_stroke_button").assertIsNotEnabled()
        tag("clear_canvas_button").assertIsNotEnabled()
        tag("skip_character_button").assertIsNotEnabled()
        gradingGate!!.complete(Unit)
        compose.waitForIdle()
        tag("clear_canvas_button").assertIsEnabled()
    }

    @Test
    fun retries_keep_only_the_best_score() { // finding 6
        showGame(TargetLanguage.FRENCH, DifficultyLevel.ADVANCED) // 3 items
        drawStroke()
        nextScore = 50; tag("check_calligraphy_button").performClick(); compose.waitForIdle()
        nextScore = 90; tag("check_calligraphy_button").performClick(); compose.waitForIdle()
        nextScore = 70; tag("check_calligraphy_button").performClick(); compose.waitForIdle()
        tag("next_character_button").performClick(); compose.waitForIdle()
        tag("skip_character_button").performClick(); compose.waitForIdle()
        tag("skip_character_button").performClick(); compose.waitForIdle()
        assertEquals(listOf(90), savedScores) // best of 50/90/70, skipped items not counted
    }

    // ---------- Finishing and saving ----------

    @Test
    fun real_average_is_saved_without_a_floor() { // finding 13
        nextScore = 10
        showGame(TargetLanguage.FRENCH, DifficultyLevel.ADVANCED)
        repeat(3) { drawCheckAndNext() }
        assertEquals(listOf(10), savedScores)
    }

    @Test
    fun skipping_everything_saves_nothing() { // finding 13
        showGame(TargetLanguage.FRENCH, DifficultyLevel.ADVANCED)
        repeat(3) { tag("skip_character_button").performClick(); compose.waitForIdle() }
        compose.onNodeWithText("nothing was saved", substring = true).assertExists()
        compose.onNodeWithText("Nothing to save").assertExists()
        assertEquals(emptyList<Int>(), savedScores)
    }

    @Test
    fun save_failure_does_not_crash_and_can_be_retried() { // finding 10
        saveError = IllegalStateException("offline")
        showGame(TargetLanguage.FRENCH, DifficultyLevel.ADVANCED)
        repeat(3) { drawCheckAndNext() }
        compose.onNodeWithText("Couldn't save your result", substring = true).assertExists()
        compose.onNodeWithText("Not saved yet").assertExists()
        assertTrue(compose.onAllNodesWithText("Synced to Cloud").fetchSemanticsNodes().isEmpty())
        saveError = null
        tag("retry_save_button").performClick()
        compose.waitForIdle()
        assertEquals(listOf(80), savedScores)
        compose.onAllNodesWithText("Couldn't save your result", substring = true).fetchSemanticsNodes().let {
            assertTrue(it.isEmpty())
        }
    }

    // ---------- Sound and content ----------

    @Test
    fun sound_button_reads_japanese_character_as_written() {
        showGame()
        tag("calligraphy_speak_button").performClick()
        assertEquals(listOf("あ"), spoken)
    }

    @Test
    fun sound_button_reads_symbol_names_not_punctuation() { // finding 7
        showGame(TargetLanguage.SPANISH, DifficultyLevel.BEGINNER)
        tag("calligraphy_speak_button").performClick() // "ñ"
        assertEquals(listOf("eñe"), spoken)
    }

    @Test
    fun every_level_has_at_least_three_characters() { // finding 12
        for (lang in TargetLanguage.entries) for (level in DifficultyLevel.entries) {
            val n = com.example.data.content.CuratedContent.calligraphyItems(lang, level).size
            assertTrue("$lang $level has $n", n >= 3)
        }
    }
}

/** Finding 9: in landscape the pad, Check and Skip must all be on screen without scrolling. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w891dp-h411dp-land-xxhdpi")
class CalligraphyGameLandscapeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun pad_and_controls_fit_on_a_landscape_screen() {
        compose.setContent {
            PolyglotStudioTheme(activeLanguage = TargetLanguage.JAPANESE, darkTheme = false) {
                CalligraphyGameScreen(
                    language = TargetLanguage.JAPANESE,
                    difficulty = DifficultyLevel.BEGINNER,
                    onEvaluate = { CalligraphyEvaluation(80, true, "", "") },
                    speakingKey = null,
                    onSpeak = { _, _, _, _ -> },
                    onCompleteSession = { _, _, _, _, _, _, _ -> },
                    onBack = {}
                )
            }
        }
        compose.onNodeWithTag("calligraphy_canvas").assertIsDisplayed()
        compose.onNodeWithTag("check_calligraphy_button").assertIsDisplayed()
        compose.onNodeWithTag("skip_character_button").assertIsDisplayed()
    }
}
