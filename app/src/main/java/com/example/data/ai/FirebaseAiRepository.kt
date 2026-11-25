package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.content.QuizQuestionItem
import com.example.data.content.StoryChoiceSeed
import com.example.data.model.DifficultyLevel
import com.example.data.model.TargetLanguage
import com.google.firebase.Firebase
import com.google.firebase.ai.Chat
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.liveGenerationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.Collections

private const val TAG = "FirebaseAiRepository"
private const val AI_TIMEOUT_MS = 60_000L

/** Each grading model gets this long before the next one is tried. */
private const val GRADING_TIMEOUT_MS = 20_000L

/** Fast model first; the larger model is a backup (it was overloaded during QA). */
private val GRADING_MODELS = listOf("gemini-3.5-flash-lite", "gemini-3.8-flash")

/** A drawing passes at or above this score. The app decides, not the model. */
const val CALLIGRAPHY_PASS_SCORE = 65

data class CalligraphyEvaluation(
    val score: Int,
    val isPass: Boolean,
    val strokeFeedback: String,
    val improvementTip: String
)

/** Everything the grader needs about one drawing. */
data class CalligraphyRequest(
    val bitmap: Bitmap,
    val targetChar: String,
    val reading: String,
    val strokeHint: String,
    /** Strokes in the order drawn, e.g. "1. line from (30,40) to (70,40), going right". */
    val strokeDescription: String,
    val language: TargetLanguage
)

class GradingUnavailableException(cause: Throwable?) : IllegalStateException(
    "Grading is busy right now. Please try again in a moment, or tap Skip.", cause
)

/** Parses the model's JSON. Throws if the score is missing so the next model can be tried. */
internal fun parseCalligraphyEvaluation(raw: String): CalligraphyEvaluation {
    val json = JSONObject(raw)
    require(json.has("score")) { "Grading response had no score" }
    val score = json.getInt("score").coerceIn(0, 100)
    return CalligraphyEvaluation(
        score = score,
        isPass = score >= CALLIGRAPHY_PASS_SCORE,
        strokeFeedback = json.optString("strokeFeedback").ifBlank { "Good effort on the main strokes." },
        improvementTip = json.optString("improvementTip").ifBlank { "Keep proportions balanced inside the square." }
    )
}

data class VoiceEvaluation(
    val transcript: String,
    val accuracyScore: Int,
    val feedback: String,
    val phoneticTip: String
)

data class StoryTurnResponse(
    val npcLine: String,
    val npcPhonetic: String,
    val npcEnglish: String,
    val replyFeedback: String,
    val naturalnessScore: Int,
    val suggestedReplies: List<StoryChoiceSeed>
)

@OptIn(PublicPreviewAPI::class)
class FirebaseAiRepository {

    private val ttsCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, ByteArray>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?): Boolean =
                size > 24
        }
    )

    private val flashModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel(modelName = "gemini-3.8-flash")
    }

    private val fastQuizModel by lazy {
        val quizSchema = Schema.obj(
            properties = mapOf(
                "questions" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "prompt" to Schema.string(description = "English question prompt"),
                            "targetSnippet" to Schema.string(description = "Target language sentence with ___ blank"),
                            "options" to Schema.array(items = Schema.string(), description = "4 answer choices"),
                            "correctIndex" to Schema.integer(description = "0-based index of the correct option"),
                            "grammarRule" to Schema.string(description = "Short 1-2 sentence explanation")
                        )
                    )
                )
            )
        )
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = quizSchema
            }
        )
    }

    private val calligraphyModels by lazy {
        val evalSchema = Schema.obj(
            properties = mapOf(
                "score" to Schema.integer(description = "Accuracy score 0 to 100"),
                "strokeFeedback" to Schema.string(description = "Specific observation of shape, proportions and accents"),
                "improvementTip" to Schema.string(description = "Actionable next-attempt tip")
            )
        )
        GRADING_MODELS.map { name ->
            name to Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
                modelName = name,
                generationConfig = generationConfig {
                    responseMimeType = "application/json"
                    responseSchema = evalSchema
                }
            )
        }
    }

    private val voiceEvalModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "transcript" to Schema.string(description = "What the learner actually said"),
                "accuracyScore" to Schema.integer(description = "Pronunciation & match score 0 to 100"),
                "feedback" to Schema.string(description = "Encouraging accuracy assessment"),
                "phoneticTip" to Schema.string(description = "Specific articulation or accent tip")
            )
        )
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    private val storyModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "npcLine" to Schema.string(description = "NPC response in the target language"),
                "npcPhonetic" to Schema.string(description = "Romaji or phonetic guide for the NPC line"),
                "npcEnglish" to Schema.string(description = "Natural English translation of NPC line"),
                "replyFeedback" to Schema.string(description = "Brief note on how natural the user's reply was"),
                "naturalnessScore" to Schema.integer(description = "Pragmatic naturalness score 0 to 100"),
                "suggestedReplies" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "targetText" to Schema.string(),
                            "phonetic" to Schema.string(),
                            "englishMeaning" to Schema.string()
                        )
                    ),
                    description = "2 to 3 natural reply choices for the learner's next turn"
                )
            )
        )
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    suspend fun synthesizeSpeechPcm(
        text: String,
        language: TargetLanguage,
        slow: Boolean = false
    ): ByteArray {
        val cacheKey = "${language.code}|$slow|$text"
        ttsCache[cacheKey]?.let { return it }

        val bcp47 = when (language) {
            TargetLanguage.JAPANESE -> "ja-JP"
            TargetLanguage.SPANISH -> "es-ES"
            TargetLanguage.FRENCH -> "fr-FR"
        }
        val voiceName = when (language) {
            TargetLanguage.JAPANESE -> "Kore"
            TargetLanguage.SPANISH -> "Aoede"
            TargetLanguage.FRENCH -> "Charon"
        }
        val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-3.1-flash-tts-preview",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.AUDIO)
                speechConfig = SpeechConfig(
                    voice = Voice(voiceName),
                    languageCode = bcp47
                )
            }
        )

        val paceInstruction = if (slow) {
            "Speak very slowly, clearly, and enunciate every syllable for a language learner"
        } else {
            "Speak naturally and clearly like a native ${language.displayName} tutor"
        }
        val prompt = "$paceInstruction: $text"

        return runWithTimeout("TTS") {
            val response = model.generateContent(prompt)
            val part = response.candidates.firstOrNull()?.content?.parts
                ?.filterIsInstance<InlineDataPart>()
                ?.firstOrNull()
                ?: throw IllegalStateException("TTS returned no audio data.")
            val bytes = part.inlineData
            if (bytes.isNotEmpty()) {
                ttsCache[cacheKey] = bytes
            }
            bytes
        }
    }

    /**
     * Grades a drawing. Tries each model in [GRADING_MODELS] with a [GRADING_TIMEOUT_MS] limit and
     * throws [GradingUnavailableException] (with a learner-friendly message) if none answers.
     */
    suspend fun evaluateCalligraphy(request: CalligraphyRequest): CalligraphyEvaluation {
        val prompt = content {
            image(request.bitmap)
            text(
                "You are a gentle, expert ${request.language.displayName} writing tutor. " +
                    "The learner attempted to write '${request.targetChar}' (${request.reading}) on a touch canvas. " +
                    "Expected strokes: ${request.strokeHint}. " +
                    "Strokes in the order the learner drew them (x,y from 0 to 100, origin top-left): " +
                    "${request.strokeDescription}. " +
                    "Evaluate legibility, proportions, stroke shapes and accents/diacritics. " +
                    "Do NOT comment on stroke order or stroke count; the app checks those separately. " +
                    "Score 65 or more only if the character is clearly recognizable. " +
                    "If the canvas is nearly blank, score below 20 and explain kindly."
            )
        }
        var lastError: Exception? = null
        for ((name, model) in calligraphyModels) {
            try {
                return withTimeout(GRADING_TIMEOUT_MS) {
                    val raw = model.generateContent(prompt).text
                        ?: throw IllegalStateException("Empty calligraphy evaluation.")
                    parseCalligraphyEvaluation(raw)
                }
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "Calligraphy grading with $name timed out", e)
                lastError = e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Calligraphy grading with $name failed", e)
                lastError = e
            }
        }
        throw GradingUnavailableException(lastError)
    }

    suspend fun generateQuizQuestions(
        language: TargetLanguage,
        difficulty: DifficultyLevel,
        count: Int = 4
    ): List<QuizQuestionItem> = runWithTimeout("Quiz generation") {
        val response = fastQuizModel.generateContent(
            "Generate $count unique, engaging ${difficulty.label}-level ${language.displayName} grammar & syntax " +
                "multiple-choice questions for an English speaker. Keep targetSnippet concise with '___' for the blank, " +
                "provide exactly 4 distinct options, and a crisp 1-sentence grammarRule."
        )
        val raw = response.text ?: throw IllegalStateException("Empty quiz response.")
        val json = JSONObject(raw)
        val arr = json.getJSONArray("questions")
        (0 until arr.length()).map { idx ->
            val obj = arr.getJSONObject(idx)
            val optsArr = obj.getJSONArray("options")
            val opts = (0 until optsArr.length()).map { optsArr.getString(it) }
            QuizQuestionItem(
                id = "ai_${language.code}_${System.currentTimeMillis()}_$idx",
                prompt = obj.getString("prompt"),
                targetSnippet = obj.getString("targetSnippet"),
                options = opts,
                correctIndex = obj.optInt("correctIndex", 0).coerceIn(0, (opts.size - 1).coerceAtLeast(0)),
                grammarRule = obj.getString("grammarRule")
            )
        }
    }

    suspend fun explainGrammarDetail(
        language: TargetLanguage,
        snippet: String,
        correctOption: String
    ): String = runWithTimeout("Grammar explanation") {
        val response = flashModel.generateContent(
            "In 3 concise bullet-free sentences, explain why '$correctOption' fits '$snippet' in ${language.displayName} " +
                "and give one bonus everyday example with English translation."
        )
        response.text?.trim().takeIf { !it.isNullOrBlank() }
            ?: "That choice matches the grammatical structure of the sentence."
    }

    suspend fun evaluateSpokenAudio(
        wavBytes: ByteArray,
        expectedPhrase: String,
        language: TargetLanguage
    ): VoiceEvaluation = runWithTimeout("Voice evaluation") {
        val response = voiceEvalModel.generateContent(
            content {
                inlineData(wavBytes, "audio/wav")
                text(
                    "Listen carefully to the learner speaking ${language.displayName}. " +
                        "They were trying to say: '$expectedPhrase'. " +
                        "Transcribe what you heard, score accuracy 0-100, and give friendly phonetic feedback."
                )
            }
        )
        val raw = response.text ?: throw IllegalStateException("Empty voice evaluation.")
        val json = JSONObject(raw)
        VoiceEvaluation(
            transcript = json.optString("transcript", "(inaudible)"),
            accuracyScore = json.optInt("accuracyScore", 65).coerceIn(0, 100),
            feedback = json.optString("feedback", "Nice attempt! Keep pacing steady."),
            phoneticTip = json.optString("phoneticTip", "Try elongating the key vowel sound slightly.")
        )
    }

    suspend fun continueStoryTurn(
        scenarioTitle: String,
        language: TargetLanguage,
        difficulty: DifficultyLevel,
        historySummary: String,
        userReply: String
    ): StoryTurnResponse = runWithTimeout("Story turn") {
        val response = storyModel.generateContent(
            "You are roleplaying a friendly local character in the ${language.displayName} scenario '$scenarioTitle' " +
                "at ${difficulty.label} level. Recent dialogue:\n$historySummary\n" +
                "The learner just replied: \"$userReply\".\n" +
                "Respond naturally in ${language.displayName}, rate their reply's naturalness 0-100 with a short tip, " +
                "and provide 2 distinct natural next reply options with phonetic guide and English translation."
        )
        val raw = response.text ?: throw IllegalStateException("Empty story turn.")
        val json = JSONObject(raw)
        val arr = json.getJSONArray("suggestedReplies")
        val replies = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            StoryChoiceSeed(
                targetText = o.getString("targetText"),
                phonetic = o.getString("phonetic"),
                englishMeaning = o.getString("englishMeaning")
            )
        }
        StoryTurnResponse(
            npcLine = json.getString("npcLine"),
            npcPhonetic = json.getString("npcPhonetic"),
            npcEnglish = json.getString("npcEnglish"),
            replyFeedback = json.optString("replyFeedback", "Natural phrasing!"),
            naturalnessScore = json.optInt("naturalnessScore", 85).coerceIn(0, 100),
            suggestedReplies = replies
        )
    }

    fun startTutorChat(language: TargetLanguage, difficulty: DifficultyLevel): Chat {
        val sys = content {
            text(
                "You are Lumi, an encouraging, playful ${language.displayName} (${language.nativeLabel}) tutor in Polyglot Studio. " +
                    "The learner's base language is English and their level is ${difficulty.label}. " +
                    "Always include the target script, a phonetic/romaji guide in parentheses, and a clear English translation for new phrases. " +
                    "Keep responses crisp, structured, and under 120 words unless asked for deeper grammar."
            )
        }
        val tutorModel = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = sys
        )
        return tutorModel.startChat()
    }

    fun sendTutorMessageStream(chat: Chat, message: String): Flow<String> =
        chat.sendMessageStream(message).map { chunk -> chunk.text.orEmpty() }

    suspend fun connectLiveTutorSession(
        language: TargetLanguage,
        difficulty: DifficultyLevel
    ): LiveSession {
        val sys = content {
            text(
                "You are Lumi, a warm spoken ${language.displayName} conversation partner at ${difficulty.label} level. " +
                    "Speak mostly in short, natural ${language.displayName} sentences followed by brief English encouragement or gentle corrections."
            )
        }
        val voice = when (language) {
            TargetLanguage.JAPANESE -> Voice("Kore")
            TargetLanguage.SPANISH -> Voice("Aoede")
            TargetLanguage.FRENCH -> Voice("Charon")
        }
        val liveModel = Firebase.ai(backend = GenerativeBackend.googleAI()).liveModel(
            modelName = "gemini-3.1-flash-live-preview",
            generationConfig = liveGenerationConfig {
                responseModality = ResponseModality.AUDIO
                speechConfig = SpeechConfig(voice = voice)
            },
            systemInstruction = sys
        )
        return withTimeout(AI_TIMEOUT_MS) {
            liveModel.connect()
        }
    }

    private suspend fun <T> runWithTimeout(label: String, block: suspend () -> T): T {
        return try {
            withTimeout(AI_TIMEOUT_MS) { block() }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "$label timed out after ${AI_TIMEOUT_MS}ms", e)
            throw IllegalStateException("$label timed out. Please try again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "$label failed", e)
            throw e
        }
    }
}
