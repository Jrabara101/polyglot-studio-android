package com.example.data.content

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.DifficultyLevel
import com.example.data.model.TargetLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StoryCatalogTest {

    private val kanjiPattern = Regex("[\\u4E00-\\u9FFF]")

    @Before
    fun setUp() {
        StoryCatalog.init(ApplicationProvider.getApplicationContext<Context>())
    }

    @Test
    fun catalog_has_100_stories_split_by_language_and_level() {
        val jp = StoryCatalog.stories(TargetLanguage.JAPANESE)
        val es = StoryCatalog.stories(TargetLanguage.SPANISH)
        val fr = StoryCatalog.stories(TargetLanguage.FRENCH)

        assertEquals("Total story count", 100, jp.size + es.size + fr.size)

        assertEquals("Japanese story count", 34, jp.size)
        assertEquals(12, StoryCatalog.stories(TargetLanguage.JAPANESE, DifficultyLevel.BEGINNER).size)
        assertEquals(11, StoryCatalog.stories(TargetLanguage.JAPANESE, DifficultyLevel.INTERMEDIATE).size)
        assertEquals(11, StoryCatalog.stories(TargetLanguage.JAPANESE, DifficultyLevel.ADVANCED).size)
        assertEquals("Unique Japanese story IDs", 34, jp.map { it.id }.toSet().size)
        assertEquals("Unique Japanese story titles", 34, jp.map { it.title }.toSet().size)

        assertEquals("Spanish story count", 33, es.size)
        assertEquals(11, StoryCatalog.stories(TargetLanguage.SPANISH, DifficultyLevel.BEGINNER).size)
        assertEquals(11, StoryCatalog.stories(TargetLanguage.SPANISH, DifficultyLevel.INTERMEDIATE).size)
        assertEquals(11, StoryCatalog.stories(TargetLanguage.SPANISH, DifficultyLevel.ADVANCED).size)
        assertEquals("Unique Spanish story IDs", 33, es.map { it.id }.toSet().size)
        assertEquals("Unique Spanish story titles", 33, es.map { it.title }.toSet().size)

        assertEquals("French story count", 33, fr.size)
        assertEquals(11, StoryCatalog.stories(TargetLanguage.FRENCH, DifficultyLevel.BEGINNER).size)
        assertEquals(11, StoryCatalog.stories(TargetLanguage.FRENCH, DifficultyLevel.INTERMEDIATE).size)
        assertEquals(11, StoryCatalog.stories(TargetLanguage.FRENCH, DifficultyLevel.ADVANCED).size)
        assertEquals("Unique French story IDs", 33, fr.map { it.id }.toSet().size)
        assertEquals("Unique French story titles", 33, fr.map { it.title }.toSet().size)
    }

    @Test
    fun every_loaded_story_is_a_valid_3_turn_6_node_diamond_dag() {
        val allStories = TargetLanguage.entries.flatMap { StoryCatalog.stories(it) }
        assertEquals("Expected all 100 stories to be loaded", 100, allStories.size)
        for (story in allStories) {
            assertValidDiamondStory(story)
        }
    }

    @Test
    fun every_story_node_has_bundled_kokoro_clip() {
        var clipCount = 0
        for (language in TargetLanguage.entries) {
            val stories = StoryCatalog.stories(language)
            assertTrue("Expected stories for ${language.code}", stories.isNotEmpty())
            for (story in stories) {
                for (node in story.nodes.values) {
                    val expected = "audio/stories/${language.code}/${story.id}_${node.id}.m4a"
                    assertEquals(
                        "Missing clip for ${language.code}/${story.id}/${node.id}",
                        expected,
                        StoryCatalog.audioAssetFor(language, story.id, node.id)
                    )
                    assertEquals(
                        "Speaker key lookup missing for ${language.code}/${story.id}/${node.id}",
                        expected,
                        StoryCatalog.audioAssetForSpeakerKey(
                            language,
                            StoryCatalog.speakerKey(story.id, node.id)
                        )
                    )
                    assertNotNull(
                        "Normalized lookup missing for ${language.code}/${story.id}/${node.id}",
                        StoryCatalog.audioAssetFor(language, node.npcText)
                    )
                    clipCount++
                }
            }
        }
        assertEquals("Total Kokoro story clips", 600, clipCount)
    }

    private fun assertValidDiamondStory(story: StoryDefinition) {
        val expectedIds = setOf("n0", "n1a", "n1b", "n2a", "n2b", "n3")
        assertEquals("${story.id}: exact 6 node IDs", expectedIds, story.nodes.keys)
        assertEquals("${story.id}: startNodeId", "n0", story.startNodeId)
        assertTrue("${story.id}: blank title", story.title.isNotBlank())
        assertTrue("${story.id}: blank setting", story.setting.isNotBlank())
        assertTrue("${story.id}: blank category", story.category.isNotBlank())

        val n0 = story.nodes.getValue("n0")
        val n1a = story.nodes.getValue("n1a")
        val n1b = story.nodes.getValue("n1b")
        val n2a = story.nodes.getValue("n2a")
        val n2b = story.nodes.getValue("n2b")
        val n3 = story.nodes.getValue("n3")

        assertEquals("${story.id}/n0 targets", setOf("n1a", "n1b"), n0.choices.map { it.nextNodeId }.toSet())
        assertEquals("${story.id}/n0 choice count", 2, n0.choices.size)
        assertEquals("${story.id}/n1a targets", setOf("n2a", "n2b"), n1a.choices.map { it.nextNodeId }.toSet())
        assertEquals("${story.id}/n1a choice count", 2, n1a.choices.size)
        assertEquals("${story.id}/n1b targets", setOf("n2a", "n2b"), n1b.choices.map { it.nextNodeId }.toSet())
        assertEquals("${story.id}/n1b choice count", 2, n1b.choices.size)
        assertEquals("${story.id}/n2a targets", setOf("n3"), n2a.choices.map { it.nextNodeId }.toSet())
        assertEquals("${story.id}/n2a choice count", 2, n2a.choices.size)
        assertEquals("${story.id}/n2b targets", setOf("n3"), n2b.choices.map { it.nextNodeId }.toSet())
        assertEquals("${story.id}/n2b choice count", 2, n2b.choices.size)
        assertTrue("${story.id}/n3 must be terminal", n3.choices.isEmpty())

        for (node in story.nodes.values) {
            assertTrue("${story.id}/${node.id}: blank npcText", node.npcText.isNotBlank())
            assertTrue("${story.id}/${node.id}: blank npcPhonetic", node.npcPhonetic.isNotBlank())
            assertTrue("${story.id}/${node.id}: blank npcEnglish", node.npcEnglish.isNotBlank())
            if (story.language == TargetLanguage.JAPANESE) {
                assertTrue("${story.id}/${node.id}: blank npcReading", node.npcReading.isNotBlank())
                assertFalse(
                    "${story.id}/${node.id}: npcReading contains kanji: ${node.npcReading}",
                    kanjiPattern.containsMatchIn(node.npcReading)
                )
            }
            for (choice in node.choices) {
                assertTrue("${story.id}/${node.id}: blank choice text", choice.text.isNotBlank())
                assertTrue("${story.id}/${node.id}: blank choice phonetic", choice.phonetic.isNotBlank())
                assertTrue("${story.id}/${node.id}: blank choice english", choice.english.isNotBlank())
                assertTrue("${story.id}/${node.id}: blank choice feedback", choice.feedback.isNotBlank())
                assertTrue(
                    "${story.id}/${node.id}: naturalness ${choice.naturalness} out of range",
                    choice.naturalness in 60..100
                )
            }
        }
    }
}
