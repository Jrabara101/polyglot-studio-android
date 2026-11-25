package com.example.data.content

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.DifficultyLevel
import com.example.data.model.TargetLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LexiconTest {

    @Before
    fun setUp() {
        Lexicon.init(ApplicationProvider.getApplicationContext<Context>())
    }

    @Test
    fun `every language has 100 reviewed words split by level`() {
        for (language in TargetLanguage.entries) {
            val all = Lexicon.entries(language)
            assertEquals("${language.code} word count", 100, all.size)
            assertEquals(40, Lexicon.entries(language, DifficultyLevel.BEGINNER).size)
            assertEquals(35, Lexicon.entries(language, DifficultyLevel.INTERMEDIATE).size)
            assertEquals(25, Lexicon.entries(language, DifficultyLevel.ADVANCED).size)
        }
    }

    @Test
    fun `card game uses lexicon words`() {
        val cards = CuratedContent.cardPairs(TargetLanguage.JAPANESE, DifficultyLevel.BEGINNER)
        assertEquals(40, cards.size)
        val water = cards.first { it.id == "jp_v_001" }
        assertEquals("水", water.targetText)
        assertEquals("みず · mizu", water.phonetic)
    }

    @Test
    fun `every word and example in every language has a clip`() {
        for (language in TargetLanguage.entries) {
            for (entry in Lexicon.entries(language)) {
                val dir = "audio/${language.code}"
                assertEquals("$dir/${entry.id}.m4a", Lexicon.audioAssetFor(language, entry.text))
                assertEquals("$dir/${entry.id}_ex.m4a", Lexicon.audioAssetFor(language, entry.exampleSentence))
                if (language == TargetLanguage.JAPANESE) {
                    assertEquals("$dir/${entry.id}.m4a", Lexicon.audioAssetFor(language, entry.reading))
                }
            }
        }
    }

    @Test
    fun `clip lookup ignores case and punctuation`() {
        assertNotNull(Lexicon.audioAssetFor(TargetLanguage.JAPANESE, " 水をください "))
        assertTrue(
            Lexicon.indexKey(TargetLanguage.SPANISH, "¡Hola!") == Lexicon.indexKey(TargetLanguage.SPANISH, "hola")
        )
    }

    @Test
    fun `unknown text has no clip`() {
        assertEquals(null, Lexicon.audioAssetFor(TargetLanguage.JAPANESE, "これは辞書にない文です"))
    }
}
