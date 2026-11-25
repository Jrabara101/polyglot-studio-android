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
    fun japanese_has_34_stories_split_by_level() {
        val all = StoryCatalog.stories(TargetLanguage.JAPANESE)
        assertEquals("Japanese story count", 34, all.size)
        assertEquals(12, StoryCatalog.stories(TargetLanguage.JAPANESE, DifficultyLevel.BEGINNER).size)
        assertEquals(11, StoryCatalog.stories(TargetLanguage.JAPANESE, DifficultyLevel.INTERMEDIATE).size)
        assertEquals(11, StoryCatalog.stories(TargetLanguage.JAPANESE, DifficultyLevel.ADVANCED).size)
        assertEquals("Unique Japanese story IDs", 34, all.map { it.id }.toSet().size)
        assertEquals("Unique Japanese story titles", 34, all.map { it.title }.toSet().size)
    }

    @Test
    fun every_loaded_japanese_story_is_a_valid_3_turn_6_node_diamond_dag() {
        val stories = StoryCatalog.stories(TargetLanguage.JAPANESE)
        assertTrue("Expected Japanese stories to be loaded", stories.isNotEmpty())
        for (story in stories) {
            assertValidDiamondStory(story)
        }
    }

    @Test
    fun every_japanese_story_node_has_bundled_kokoro_clip() {
        val stories = StoryCatalog.stories(TargetLanguage.JAPANESE)
        assertEquals(34, stories.size)
        for (story in stories) {
            for (node in story.nodes.values) {
                val expected = "audio/stories/japanese/${story.id}_${node.id}.m4a"
                assertEquals(
                    "Missing clip for ${story.id}/${node.id}",
                    expected,
                    StoryCatalog.audioAssetFor(TargetLanguage.JAPANESE, story.id, node.id)
                )
                assertNotNull(
                    "Normalized lookup missing for ${story.id}/${node.id}",
                    StoryCatalog.audioAssetFor(TargetLanguage.JAPANESE, node.npcText)
                )
            }
        }
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
