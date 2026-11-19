package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.DifficultyLevel
import com.example.data.model.GameSessionRecord
import com.example.data.model.GameType
import com.example.data.model.LanguageMastery
import com.example.data.model.SavedPhrase
import com.example.data.model.TargetLanguage
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class ProgressRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = Firebase.auth
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun userDoc(uid: String) = db.collection("users").document(uid)
    private fun masteryDoc(uid: String, languageCode: String) =
        userDoc(uid).collection("mastery").document(languageCode)

    private fun sessionsCol(uid: String) = userDoc(uid).collection("sessions")
    private fun phrasesCol(uid: String) = userDoc(uid).collection("savedPhrases")

    suspend fun ensureUserInitialized(displayName: String) {
        val uid = requireUserId()
        val ref = userDoc(uid)
        val safeName = displayName.trim().ifBlank { "Polyglot Learner" }.take(80)
        try {
            val snap = ref.get().await()
            if (!snap.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "activeLanguage" to TargetLanguage.JAPANESE.code,
                    "difficulty" to DifficultyLevel.BEGINNER.code,
                    "totalXp" to 0L,
                    "currentStreak" to 1L,
                    "lastActiveDate" to todayIso(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                ref.set(payload).await()
            }
            for (lang in TargetLanguage.entries) {
                val mRef = masteryDoc(uid, lang.code)
                val mSnap = mRef.get().await()
                if (!mSnap.exists()) {
                    val mPayload = mapOf(
                        "languageCode" to lang.code,
                        "vocabularyScore" to 0L,
                        "writingScore" to 0L,
                        "grammarScore" to 0L,
                        "phoneticsScore" to 0L,
                        "pragmaticsScore" to 0L,
                        "languageXp" to 0L,
                        "gamesPlayed" to 0L,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    mRef.set(mPayload).await()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
            throw e
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> =
        userDoc(userId).snapshots()
            .map { snapshot ->
                if (!snapshot.exists()) null
                else snapshot.toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                logAndRethrowIfNeeded(error, OperationType.GET, userDoc(userId).path, userId)
            }

    fun observeAllMastery(userId: String): Flow<Map<TargetLanguage, LanguageMastery>> =
        userDoc(userId).collection("mastery").snapshots()
            .map { snapshot ->
                val list = snapshot.toObjects(
                    LanguageMastery::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                TargetLanguage.entries.associateWith { lang ->
                    list.firstOrNull { it.languageCode == lang.code }
                        ?: LanguageMastery(languageCode = lang.code)
                }
            }
            .catch { error ->
                logAndRethrowIfNeeded(
                    error,
                    OperationType.LIST,
                    "users/$userId/mastery",
                    userId
                )
            }

    fun observeRecentSessions(userId: String, limit: Long = 25L): Flow<List<GameSessionRecord>> =
        sessionsCol(userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    GameSessionRecord::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                logAndRethrowIfNeeded(error, OperationType.LIST, sessionsCol(userId).path, userId)
            }

    fun observeSavedPhrases(userId: String): Flow<List<SavedPhrase>> =
        phrasesCol(userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    SavedPhrase::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                logAndRethrowIfNeeded(error, OperationType.LIST, phrasesCol(userId).path, userId)
            }

    suspend fun setActiveLanguage(language: TargetLanguage) {
        val uid = requireUserId()
        val ref = userDoc(uid)
        try {
            ref.update(
                mapOf(
                    "activeLanguage" to language.code,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            throw e
        }
    }

    suspend fun setDifficulty(difficulty: DifficultyLevel) {
        val uid = requireUserId()
        val ref = userDoc(uid)
        try {
            ref.update(
                mapOf(
                    "difficulty" to difficulty.code,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            throw e
        }
    }

    suspend fun recordGameCompletion(
        gameType: GameType,
        language: TargetLanguage,
        difficulty: DifficultyLevel,
        scorePercent: Int,
        xpEarned: Int,
        durationSeconds: Int,
        summary: String
    ): String {
        val uid = requireUserId()
        val clampedScore = scorePercent.coerceIn(0, 100)
        val clampedXp = xpEarned.coerceIn(0, 10000)
        val clampedDuration = durationSeconds.coerceIn(0, 86400)
        val safeSummary = summary.trim().take(160)

        val profileRef = userDoc(uid)
        val mRef = masteryDoc(uid, language.code)
        val sRef = sessionsCol(uid).document()

        try {
            val today = todayIso()
            val yesterday = yesterdayIso()
            db.runTransaction { tx ->
                val pSnap = tx.get(profileRef)
                val mSnap = tx.get(mRef)

                val prevXp = pSnap.getLong("totalXp") ?: 0L
                val prevStreak = pSnap.getLong("currentStreak") ?: 0L
                val lastDate = pSnap.getString("lastActiveDate") ?: ""

                val newStreak = when (lastDate) {
                    today -> maxOf(1L, prevStreak)
                    yesterday -> prevStreak + 1L
                    else -> 1L
                }

                if (pSnap.exists()) {
                    tx.update(
                        profileRef,
                        mapOf(
                            "totalXp" to (prevXp + clampedXp),
                            "currentStreak" to newStreak,
                            "lastActiveDate" to today,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    tx.set(
                        profileRef,
                        mapOf(
                            "userId" to uid,
                            "displayName" to (auth.currentUser?.displayName ?: "Polyglot Learner").take(80),
                            "activeLanguage" to language.code,
                            "difficulty" to difficulty.code,
                            "totalXp" to clampedXp.toLong(),
                            "currentStreak" to 1L,
                            "lastActiveDate" to today,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                val currentSkillField = when (gameType) {
                    GameType.CARDS -> "vocabularyScore"
                    GameType.CALLIGRAPHY -> "writingScore"
                    GameType.QUIZ -> "grammarScore"
                    GameType.VOICE -> "phoneticsScore"
                    GameType.STORY -> "pragmaticsScore"
                }

                if (mSnap.exists()) {
                    val prevSkill = (mSnap.getLong(currentSkillField) ?: 0L).toInt()
                    val blendedSkill = maxOf(
                        prevSkill,
                        ((prevSkill * 0.4) + (clampedScore * 0.6)).toInt()
                    ).coerceIn(0, 100).toLong()
                    val prevLangXp = mSnap.getLong("languageXp") ?: 0L
                    val prevGames = mSnap.getLong("gamesPlayed") ?: 0L
                    tx.update(
                        mRef,
                        mapOf(
                            currentSkillField to blendedSkill,
                            "languageXp" to (prevLangXp + clampedXp),
                            "gamesPlayed" to (prevGames + 1L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    tx.set(
                        mRef,
                        mapOf(
                            "languageCode" to language.code,
                            "vocabularyScore" to if (gameType == GameType.CARDS) clampedScore.toLong() else 0L,
                            "writingScore" to if (gameType == GameType.CALLIGRAPHY) clampedScore.toLong() else 0L,
                            "grammarScore" to if (gameType == GameType.QUIZ) clampedScore.toLong() else 0L,
                            "phoneticsScore" to if (gameType == GameType.VOICE) clampedScore.toLong() else 0L,
                            "pragmaticsScore" to if (gameType == GameType.STORY) clampedScore.toLong() else 0L,
                            "languageXp" to clampedXp.toLong(),
                            "gamesPlayed" to 1L,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                tx.set(
                    sRef,
                    mapOf(
                        "gameType" to gameType.code,
                        "languageCode" to language.code,
                        "difficulty" to difficulty.code,
                        "scorePercent" to clampedScore.toLong(),
                        "xpEarned" to clampedXp.toLong(),
                        "durationSeconds" to clampedDuration.toLong(),
                        "summary" to safeSummary,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
            return sRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, sRef.path)
            throw e
        }
    }

    suspend fun savePhrase(
        language: TargetLanguage,
        targetText: String,
        phonetic: String,
        englishMeaning: String,
        sourceGame: String
    ): String {
        val uid = requireUserId()
        val ref = phrasesCol(uid).document()
        val payload = mapOf(
            "languageCode" to language.code,
            "targetText" to targetText.trim().take(200),
            "phonetic" to phonetic.trim().take(200),
            "englishMeaning" to englishMeaning.trim().take(200),
            "sourceGame" to sourceGame,
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            ref.set(payload).await()
            return ref.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            throw e
        }
    }

    suspend fun deleteSavedPhrase(phraseId: String) {
        val uid = requireUserId()
        require(phraseId.isNotBlank()) { "Phrase ID must not be blank" }
        val ref = phrasesCol(uid).document(phraseId)
        try {
            ref.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, ref.path)
            throw e
        }
    }

    private fun logAndRethrowIfNeeded(
        error: Throwable,
        op: OperationType,
        path: String,
        uid: String
    ): Nothing {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, op, path)
        }
        throw error
    }

    private fun todayIso(): String = isoDateOffset(0)
    private fun yesterdayIso(): String = isoDateOffset(-1)

    private fun isoDateOffset(daysOffset: Int): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.add(Calendar.DAY_OF_YEAR, daysOffset)
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(cal.time)
    }
}
