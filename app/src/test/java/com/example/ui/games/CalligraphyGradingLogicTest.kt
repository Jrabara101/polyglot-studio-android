package com.example.ui.games

import androidx.compose.ui.geometry.Offset
import com.example.data.ai.parseCalligraphyEvaluation
import com.example.data.content.CalligraphyPromptItem
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CalligraphyGradingLogicTest {

    /** Finding 3: the drawing keeps its proportions in the graded image. */
    @Test
    fun pad_is_scaled_evenly_and_centered_in_the_square_image() {
        val t = padTransform(PAD_ASPECT, 384)
        val topLeft = t.map(Offset(0f, 0f))
        val bottomRight = t.map(Offset(1f, 1f))
        val w = bottomRight.x - topLeft.x
        val h = bottomRight.y - topLeft.y
        assertEquals(384f, w, 0.5f)                // full width used
        assertEquals(PAD_ASPECT, w / h, 0.001f)    // same shape as the pad, not squashed
        assertEquals(384f - bottomRight.y, topLeft.y, 0.5f) // vertically centered
    }

    @Test
    fun strokes_are_described_in_order_with_direction() {
        val text = describeStrokes(
            listOf(
                InkStroke(listOf(Offset(0.2f, 0.3f), Offset(0.8f, 0.3f))),
                InkStroke(listOf(Offset(0.5f, 0.1f), Offset(0.5f, 0.9f))),
                InkStroke(listOf(Offset(0.5f, 0.5f)))
            ),
            PAD_ASPECT
        )
        assertEquals(
            "1. line from (20,30) to (80,30), going right; " +
                "2. line from (50,10) to (50,90), going down; " +
                "3. dot at (50,50)",
            text
        )
    }

    /** Finding 5: the app decides pass/fail from the score. */
    @Test
    fun pass_is_decided_by_score_not_by_the_model() {
        val low = parseCalligraphyEvaluation("""{"score":40,"isPass":true,"strokeFeedback":"ok","improvementTip":"t"}""")
        assertFalse(low.isPass)
        val high = parseCalligraphyEvaluation("""{"score":65,"isPass":false,"strokeFeedback":"ok","improvementTip":"t"}""")
        assertTrue(high.isPass)
    }

    @Test(expected = IllegalArgumentException::class)
    fun missing_score_is_an_error_so_the_backup_model_is_tried() {
        parseCalligraphyEvaluation("""{"strokeFeedback":"ok"}""")
    }

    @Test
    fun score_is_clamped_and_blank_text_gets_defaults() {
        val e = parseCalligraphyEvaluation("""{"score":140,"strokeFeedback":"","improvementTip":""}""")
        assertEquals(100, e.score)
        assertTrue(e.strokeFeedback.isNotBlank())
        assertTrue(e.improvementTip.isNotBlank())
    }

    @Test
    fun stroke_count_note_compares_with_the_hint() {
        val a = CalligraphyPromptItem("jp_c1", "あ", "a", "Hiragana 'a'", "3 strokes: horizontal, curve down, sweeping loop")
        assertEquals("Strokes: you drew 3 of 3 ✓", strokeCountNote(a, 3))
        assertEquals("You drew 2 strokes; 'あ' has 3.", strokeCountNote(a, 2))
        val enye = CalligraphyPromptItem("es_c1", "ñ", "eñe", "x", "Draw lowercase 'n' topped with a tilde")
        assertNull(strokeCountNote(enye, 2)) // no count in the hint, so no note
    }
}
