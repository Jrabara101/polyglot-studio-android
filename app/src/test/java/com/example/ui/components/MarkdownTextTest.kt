package com.example.ui.components

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTextTest {

    @Test
    fun parseSimpleMarkdown_applies_bold_italic_code_and_bullets_without_raw_markers() {
        val input = """
            ### Nasal Vowels
            Try **an / en** *(vahn)* and `un`:
            * **vent** — wind
            - *pain* — bread
        """.trimIndent()

        val rendered = parseSimpleMarkdown(input)
        val plain = rendered.text

        assertFalse("Bold markers should be stripped: $plain", plain.contains("**"))
        assertFalse("Heading hashes should be stripped: $plain", plain.contains("###"))
        assertFalse("Backticks should be stripped: $plain", plain.contains("`"))
        assertTrue("Bullets should render with bullet dot: $plain", plain.contains("• vent — wind"))
        assertTrue("Dash bullets should render with bullet dot: $plain", plain.contains("• pain — bread"))

        val hasBold = rendered.spanStyles.any { it.item.fontWeight == FontWeight.Bold }
        val hasItalic = rendered.spanStyles.any { it.item.fontStyle == FontStyle.Italic }
        val hasMonospace = rendered.spanStyles.any { it.item.fontFamily == FontFamily.Monospace }
        assertTrue("Expected at least one Bold span", hasBold)
        assertTrue("Expected at least one Italic span", hasItalic)
        assertTrue("Expected at least one Monospace span for inline code", hasMonospace)
    }

    @Test
    fun parseSimpleMarkdown_handles_partial_streaming_markdown_without_crashing() {
        val partialBold = parseSimpleMarkdown("Great job! Try **Bonjour")
        assertTrue(partialBold.text.contains("Bonjour"))
        assertFalse(partialBold.text.contains("**"))

        val partialBoldItalic = parseSimpleMarkdown("Try ***Salut")
        assertEquals("Try Salut", partialBoldItalic.text)
    }

    @Test
    fun stripMarkdownForSpeech_removes_markdown_tokens_cleanly() {
        val raw = """
            ## Quick Tip
            Say **an / en** *(vahn)* like `vent`.
            * **Bonjour** — Hello
            - Check [guide](https://example.com)
        """.trimIndent()

        val speech = stripMarkdownForSpeech(raw)

        assertFalse(speech.contains("*"))
        assertFalse(speech.contains("#"))
        assertFalse(speech.contains("`"))
        assertFalse(speech.contains("https://"))
        assertEquals("Quick Tip. Say an / en (vahn) like vent. Bonjour — Hello. Check guide", speech)
    }
}
