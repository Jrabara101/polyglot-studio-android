package com.example.data.content

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.model.DifficultyLevel
import com.example.data.model.TargetLanguage
import org.json.JSONArray

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
 * Bundled offline branching stories loaded lazily from assets/stories/<language>.json,
 * paired with pre-generated Kokoro pronunciation clips in
 * assets/audio/stories/<language>/<storyId>_<nodeId>.m4a.
 */
object StoryCatalog {
    private const val TAG = "StoryCatalog"

    @Volatile private var appContext: Context? = null
    @Volatile private var data: Loaded? = null

    private class Loaded(
        val stories: Map<TargetLanguage, List<StoryDefinition>>,
        /** "<language code>|<storyId>|<nodeId>" -> asset path */
        val nodeAudioIndex: Map<String, String>,
        /** "<language code>|<normalized npcText/npcReading>" -> asset path */
        val textAudioIndex: Map<String, String>
    )

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun stories(language: TargetLanguage): List<StoryDefinition> =
        loaded()?.stories?.get(language).orEmpty()

    fun stories(language: TargetLanguage, level: DifficultyLevel): List<StoryDefinition> =
        stories(language).filter { it.level == level }

    fun audioAssetFor(language: TargetLanguage, storyId: String, nodeId: String): String? =
        loaded()?.nodeAudioIndex?.get("${language.code}|$storyId|$nodeId")

    fun audioAssetFor(language: TargetLanguage, text: String): String? =
        loaded()?.textAudioIndex?.get(Lexicon.indexKey(language, text))

    /** Resolves a speaker key like "story_node_<storyId>_<nodeId>" to its exact bundled clip. */
    fun audioAssetForSpeakerKey(language: TargetLanguage, key: String): String? {
        if (!key.startsWith(SPEAKER_KEY_PREFIX)) return null
        val rest = key.removePrefix(SPEAKER_KEY_PREFIX)
        val sep = rest.indexOf('|')
        if (sep <= 0 || sep >= rest.lastIndex) return null
        return audioAssetFor(language, rest.substring(0, sep), rest.substring(sep + 1))
    }

    fun speakerKey(storyId: String, nodeId: String): String = "$SPEAKER_KEY_PREFIX$storyId|$nodeId"

    private fun loaded(): Loaded? {
        data?.let { return it }
        val context = appContext ?: return null
        return synchronized(this) {
            data ?: load(context).also { data = it }
        }
    }

    private fun load(context: Context): Loaded {
        val storiesByLang = mutableMapOf<TargetLanguage, List<StoryDefinition>>()
        val nodeAudioIndex = mutableMapOf<String, String>()
        val textAudioIndex = mutableMapOf<String, String>()

        for (language in TargetLanguage.entries) {
            val list = try {
                val json = context.assets.open("stories/${language.code}.json")
                    .bufferedReader(Charsets.UTF_8).use { it.readText() }
                parse(json, language)
            } catch (e: Exception) {
                Log.w(TAG, "No bundled stories for ${language.code}", e)
                emptyList()
            }
            storiesByLang[language] = list

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
        }
        Log.i(TAG, "Loaded ${storiesByLang.values.sumOf { it.size }} stories, ${nodeAudioIndex.size} clips")
        return Loaded(storiesByLang, nodeAudioIndex, textAudioIndex)
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
                    npcEnglish = nObj.getString("npcEnglish"),
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
