package com.example.data.content

import android.content.Context
import android.util.Log
import com.example.data.model.DifficultyLevel
import com.example.data.model.TargetLanguage
import org.json.JSONArray
import java.text.Normalizer

/** One vocabulary item from assets/lexicon/<language>.json. */
data class LexiconEntry(
    val id: String,
    val language: TargetLanguage,
    val level: DifficultyLevel,
    val text: String,
    val reading: String,
    val romanization: String,
    val ipa: String,
    val english: String,
    val partOfSpeech: String,
    val gender: String?,
    val category: String,
    val exampleSentence: String,
    val exampleReading: String,
    val exampleEnglish: String
)

/**
 * Reviewed vocabulary bundled with the app, plus the pre-generated pronunciation clips in
 * assets/audio/<language>/<id>.m4a (word) and <id>_ex.m4a (example sentence).
 *
 * Call [init] once with the application context; data loads lazily on first use.
 */
object Lexicon {
    private const val TAG = "Lexicon"

    @Volatile private var appContext: Context? = null
    @Volatile private var data: Loaded? = null

    private class Loaded(
        val entries: Map<TargetLanguage, List<LexiconEntry>>,
        /** "<language code>|<normalized text>" -> asset path of its clip. */
        val audioIndex: Map<String, String>
    )

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun entries(language: TargetLanguage): List<LexiconEntry> = loaded()?.entries?.get(language).orEmpty()

    fun entries(language: TargetLanguage, level: DifficultyLevel): List<LexiconEntry> =
        entries(language).filter { it.level == level }

    /** Asset path of a recorded clip whose text matches [text], or null if none exists. */
    fun audioAssetFor(language: TargetLanguage, text: String): String? =
        loaded()?.audioIndex?.get(indexKey(language, text))

    private fun loaded(): Loaded? {
        data?.let { return it }
        val context = appContext ?: return null
        return synchronized(this) {
            data ?: load(context).also { data = it }
        }
    }

    private fun load(context: Context): Loaded {
        val entries = mutableMapOf<TargetLanguage, List<LexiconEntry>>()
        val audioIndex = mutableMapOf<String, String>()
        for (language in TargetLanguage.entries) {
            val list = try {
                val json = context.assets.open("lexicon/${language.code}.json")
                    .bufferedReader(Charsets.UTF_8).use { it.readText() }
                parse(json, language)
            } catch (e: Exception) {
                Log.w(TAG, "No lexicon for ${language.code}", e)
                emptyList()
            }
            entries[language] = list

            val clips = try {
                context.assets.list("audio/${language.code}")?.toSet().orEmpty()
            } catch (e: Exception) {
                emptySet()
            }
            for (entry in list) {
                fun register(fileName: String, vararg texts: String) {
                    if (fileName !in clips) return
                    val path = "audio/${language.code}/$fileName"
                    texts.filter { it.isNotBlank() }.forEach { audioIndex[indexKey(language, it)] = path }
                }
                register("${entry.id}.m4a", entry.text, entry.reading)
                register("${entry.id}_ex.m4a", entry.exampleSentence, entry.exampleReading)
            }
        }
        Log.i(TAG, "Loaded ${entries.values.sumOf { it.size }} words, ${audioIndex.values.toSet().size} clips")
        return Loaded(entries, audioIndex)
    }

    internal fun parse(json: String, language: TargetLanguage): List<LexiconEntry> {
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            LexiconEntry(
                id = o.getString("id"),
                language = language,
                level = DifficultyLevel.fromCode(o.optString("level")),
                text = o.getString("text"),
                reading = o.optString("reading"),
                romanization = o.optString("romanization"),
                ipa = o.optString("ipa"),
                english = o.getString("english"),
                partOfSpeech = o.optString("partOfSpeech"),
                gender = if (o.isNull("gender")) null else o.optString("gender"),
                category = o.optString("category"),
                exampleSentence = o.optString("exampleSentence"),
                exampleReading = o.optString("exampleReading"),
                exampleEnglish = o.optString("exampleEnglish")
            )
        }
    }

    /** Case-, accent-form- and punctuation-insensitive key so "¡Hola!" in a game still finds "hola". */
    internal fun indexKey(language: TargetLanguage, text: String): String {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFC)
            .lowercase()
            .replace(Regex("[\\p{Punct}¡¿「」。、！？・…\\s]+"), " ")
            .trim()
        return "${language.code}|$normalized"
    }
}
