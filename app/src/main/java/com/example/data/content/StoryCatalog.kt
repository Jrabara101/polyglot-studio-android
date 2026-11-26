package com.example.data.content

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.model.DifficultyLevel
import com.example.data.model.TargetLanguage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap

data class StoryChoice(
    val text: String,
    val reading: String,
    val phonetic: String,
    val english: String,
    val naturalness: Int,
    val feedback: String,
    val nextNodeId: String
)

data class StoryNode(
    val id: String,
    val npcText: String,
    val npcReading: String,
    val npcPhonetic: String,
    val npcEnglish: String,
    val choices: List<StoryChoice>
)

data class StoryDefinition(
    val id: String,
    val language: TargetLanguage,
    val level: DifficultyLevel,
    val title: String,
    val setting: String,
    val category: String,
    val drawableRes: Int,
    val startNodeId: String,
    val nodes: Map<String, StoryNode>
)

/**
 * Bundled offline branching stories loaded per-language from assets/stories/<language>.json,
 * paired with pre-generated Kokoro pronunciation clips in
 * assets/audio/stories/<language>/<storyId>_<nodeId>.m4a.
 *
 * Call [init] once with the application context and [preloadAsync] on [Dispatchers.IO] so
 * UI screens never block the main thread parsing JSON or scanning assets.
 */
object StoryCatalog {
    private const val TAG = "StoryCatalog"

    @Volatile private var appContext: Context? = null
    private val languageCache = ConcurrentHashMap<TargetLanguage, LanguageStories>()
    private val languageLocks = TargetLanguage.entries.associateWith { Any() }

    private class LanguageStories(
        val stories: List<StoryDefinition>,
        /** "<language code>|<storyId>|<nodeId>" -> asset path */
        val nodeAudioIndex: Map<String, String>,
        /** "<language code>|<normalized npcText/npcReading>" -> asset path */
        val textAudioIndex: Map<String, String>
    )

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** Warms all language story catalogs on a background dispatcher. */
    suspend fun preloadAsync(dispatcher: CoroutineDispatcher = Dispatchers.IO) {
        withContext(dispatcher) {
            for (language in TargetLanguage.entries) {
                loaded(language)
            }
        }
    }

    fun stories(language: TargetLanguage): List<StoryDefinition> =
        loaded(language)?.stories.orEmpty()

    fun stories(language: TargetLanguage, level: DifficultyLevel): List<StoryDefinition> =
        stories(language).filter { it.level == level }

    fun audioAssetFor(language: TargetLanguage, storyId: String, nodeId: String): String? =
        loaded(language)?.nodeAudioIndex?.get("${language.code}|$storyId|$nodeId")

    fun audioAssetFor(language: TargetLanguage, text: String): String? =
        loaded(language)?.textAudioIndex?.get(Lexicon.indexKey(language, text))

    /** Resolves a speaker key like "story_node_<storyId>|<nodeId>" to its exact bundled clip. */
    fun audioAssetForSpeakerKey(language: TargetLanguage, key: String): String? {
        if (!key.startsWith(SPEAKER_KEY_PREFIX)) return null
        val rest = key.removePrefix(SPEAKER_KEY_PREFIX)
        val sep = rest.indexOf('|')
        if (sep <= 0 || sep >= rest.lastIndex) return null
        return audioAssetFor(language, rest.substring(0, sep), rest.substring(sep + 1))
    }

    fun speakerKey(storyId: String, nodeId: String): String = "$SPEAKER_KEY_PREFIX$storyId|$nodeId"

    internal fun resetForTest() {
        languageCache.clear()
    }

    internal fun loadedLanguagesForTest(): Set<TargetLanguage> = languageCache.keys.toSet()

    private fun loaded(language: TargetLanguage): LanguageStories? {
        languageCache[language]?.let { return it }
        val context = appContext ?: return null
        val lock = languageLocks.getValue(language)
        return synchronized(lock) {
            languageCache[language] ?: loadLanguage(context, language).also {
                languageCache[language] = it
            }
        }
    }

    private fun loadLanguage(context: Context, language: TargetLanguage): LanguageStories {
        val list = try {
            val json = context.assets.open("stories/${language.code}.json")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            parse(json, language)
        } catch (e: Exception) {
            Log.w(TAG, "No bundled stories for ${language.code}", e)
            emptyList()
        }

        val nodeAudioIndex = mutableMapOf<String, String>()
        val textAudioIndex = mutableMapOf<String, String>()
        val clips = try {
            context.assets.list("audio/stories/${language.code}")?.toSet().orEmpty()
        } catch (_: Exception) {
            emptySet()
        }

        for (story in list) {
            for (node in story.nodes.values) {
                val fileName = "${story.id}_${node.id}.m4a"
                if (fileName !in clips) continue
                val path = "audio/stories/${language.code}/$fileName"
                nodeAudioIndex["${language.code}|${story.id}|${node.id}"] = path
                if (node.npcText.isNotBlank()) {
                    textAudioIndex.putIfAbsent(Lexicon.indexKey(language, node.npcText), path)
                }
                if (node.npcReading.isNotBlank()) {
                    textAudioIndex.putIfAbsent(Lexicon.indexKey(language, node.npcReading), path)
                }
            }
        }
        Log.i(TAG, "Loaded ${language.code}: ${list.size} stories, ${nodeAudioIndex.size} clips")
        return LanguageStories(list, nodeAudioIndex, textAudioIndex)
    }

    internal fun parse(json: String, language: TargetLanguage): List<StoryDefinition> {
        val array = JSONArray(json)
        val drawable = drawableFor(language)
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val nodesArr = o.getJSONArray("nodes")
            val nodesMap = linkedMapOf<String, StoryNode>()
            for (nIdx in 0 until nodesArr.length()) {
                val nObj = nodesArr.getJSONObject(nIdx)
                val choicesArr = nObj.optJSONArray("choices") ?: JSONArray()
                val choices = (0 until choicesArr.length()).map { cIdx ->
                    val cObj = choicesArr.getJSONObject(cIdx)
                    StoryChoice(
                        text = cObj.getString("text"),
                        reading = cObj.optString("reading"),
                        phonetic = cObj.optString("phonetic"),
                        english = cObj.getString("english"),
                        naturalness = cObj.optInt("naturalness", 90).coerceIn(0, 100),
                        feedback = cObj.optString("feedback"),
                        nextNodeId = cObj.getString("nextNodeId")
                    )
                }
                val node = StoryNode(
                    id = nObj.getString("id"),
                    npcText = nObj.getString("npcText"),
                    npcReading = nObj.optString("npcReading"),
                    npcPhonetic = nObj.optString("npcPhonetic"),
                    npcEnglish = nObj.optString("npcEnglish"),
                    choices = choices
                )
                nodesMap[node.id] = node
            }
            StoryDefinition(
                id = o.getString("id"),
                language = language,
                level = DifficultyLevel.fromCode(o.optString("level")),
                title = o.getString("title"),
                setting = o.getString("setting"),
                category = o.optString("category", "Culture"),
                drawableRes = drawable,
                startNodeId = o.optString("startNodeId", "n0"),
                nodes = nodesMap
            )
        }
    }

    private fun drawableFor(language: TargetLanguage): Int = when (language) {
        TargetLanguage.JAPANESE -> R.drawable.img_story_japan
        TargetLanguage.SPANISH -> R.drawable.img_story_spain
        TargetLanguage.FRENCH -> R.drawable.img_story_france
    }

    private const val SPEAKER_KEY_PREFIX = "story_node_"
}
