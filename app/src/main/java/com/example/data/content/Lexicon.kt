package com.example.data.content

import android.content.Context
import android.util.Log
import com.example.data.model.DifficultyLevel
import com.example.data.model.TargetLanguage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap

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
 * assets/audio/<language>/<id>.m4a (word), <id>_ex.m4a (example sentence), and curated
 * Speech Guide / Voice Arena clips listed in assets/curated_audio.json.
 *
 * Call [init] once with the application context, and call [preloadAsync] on [Dispatchers.IO]
 * to warm caches off the main thread. Any language accessed before preloading finishes is
 * loaded lazily on demand without parsing the other languages.
 */
object Lexicon {
    private const val TAG = "Lexicon"
    private const val CURATED_MANIFEST = "curated_audio.json"
    private val INDEX_KEY_PUNCT_REGEX = Regex("[\\p{Punct}¡¿「」。、！？・…\\s]+")

    @Volatile private var appContext: Context? = null
    @Volatile private var curatedManifest: Map<String, List<CuratedClipSpec>>? = null
    private val curatedLock = Any()
    private val languageCache = ConcurrentHashMap<TargetLanguage, LanguageData>()
    private val languageLocks = TargetLanguage.entries.associateWith { Any() }

    private data class CuratedClipSpec(
        val asset: String,
        val texts: List<String>
    )

    private class LanguageData(
        val entries: List<LexiconEntry>,
        /** "<language code>|<normalized text>" -> asset path of its clip. */
        val audioIndex: Map<String, String>
    )

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** Warms all language lexicons and audio indices on a background dispatcher. */
    suspend fun preloadAsync(dispatcher: CoroutineDispatcher = Dispatchers.IO) {
        withContext(dispatcher) {
            for (language in TargetLanguage.entries) {
                loaded(language)
            }
        }
    }

    fun entries(language: TargetLanguage): List<LexiconEntry> = loaded(language)?.entries.orEmpty()

    fun entries(language: TargetLanguage, level: DifficultyLevel): List<LexiconEntry> =
        entries(language).filter { it.level == level }

    /** Asset path of a recorded clip whose text matches [text], or null if none exists. */
    fun audioAssetFor(language: TargetLanguage, text: String): String? =
        loaded(language)?.audioIndex?.get(indexKey(language, text))

    internal fun resetForTest() {
        languageCache.clear()
        synchronized(curatedLock) { curatedManifest = null }
    }

    internal fun loadedLanguagesForTest(): Set<TargetLanguage> = languageCache.keys.toSet()

    private fun loaded(language: TargetLanguage): LanguageData? {
        languageCache[language]?.let { return it }
        val context = appContext ?: return null
        val lock = languageLocks.getValue(language)
        return synchronized(lock) {
            languageCache[language] ?: loadLanguage(context, language).also {
                languageCache[language] = it
            }
        }
    }

    private fun loadCuratedManifest(context: Context): Map<String, List<CuratedClipSpec>> {
        curatedManifest?.let { return it }
        return synchronized(curatedLock) {
            curatedManifest ?: parseCuratedManifest(context).also { curatedManifest = it }
        }
    }

    private fun parseCuratedManifest(context: Context): Map<String, List<CuratedClipSpec>> {
        return try {
            val raw = context.assets.open(CURATED_MANIFEST).bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(raw)
            val result = mutableMapOf<String, List<CuratedClipSpec>>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val langCode = keys.next()
                val arr = root.getJSONArray(langCode)
                val specs = (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    val textsArr = o.getJSONArray("texts")
                    val texts = (0 until textsArr.length()).map { t -> textsArr.getString(t) }
                    CuratedClipSpec(asset = o.getString("asset"), texts = texts)
                }
                result[langCode] = specs
            }
            result
        } catch (e: Exception) {
            Log.w(TAG, "No curated audio manifest ($CURATED_MANIFEST)", e)
            emptyMap()
        }
    }

    private fun loadLanguage(context: Context, language: TargetLanguage): LanguageData {
        val list = try {
            val json = context.assets.open("lexicon/${language.code}.json")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            parse(json, language)
        } catch (e: Exception) {
            Log.w(TAG, "No lexicon for ${language.code}", e)
            emptyList()
        }

        val audioIndex = mutableMapOf<String, String>()
        val clips = try {
            context.assets.list("audio/${language.code}")?.toSet().orEmpty()
        } catch (_: Exception) {
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

        val curatedClips = try {
            context.assets.list("audio/curated/${language.code}")?.toSet().orEmpty()
        } catch (_: Exception) {
            emptySet()
        }
        val curatedSpecs = loadCuratedManifest(context)[language.code].orEmpty()
        for (spec in curatedSpecs) {
            val fileName = spec.asset.substringAfterLast('/')
            if (fileName !in curatedClips) continue
            spec.texts.filter { it.isNotBlank() }.forEach { text ->
                audioIndex.putIfAbsent(indexKey(language, text), spec.asset)
            }
        }

        Log.i(TAG, "Loaded ${language.code}: ${list.size} words, ${audioIndex.values.toSet().size} clips")
        return LanguageData(list, audioIndex)
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
            .replace(INDEX_KEY_PUNCT_REGEX, " ")
            .trim()
        return "${language.code}|$normalized"
    }
}
