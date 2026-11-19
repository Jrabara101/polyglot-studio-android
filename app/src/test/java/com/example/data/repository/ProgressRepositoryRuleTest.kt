package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.DifficultyLevel
import com.example.data.model.GameType
import com.example.data.model.TargetLanguage
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class ProgressRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ensureUserInitialized_and_recordGameCompletion_updatesProfileMasteryAndSessions() = runBlocking {
        val uid = signInTestUser("alice_${UUID.randomUUID()}@test.com")
        val repo = ProgressRepository(firestore, auth)

        withTimeout(TIMEOUT_MS) {
            repo.ensureUserInitialized("Alice Learner")
            repo.setActiveLanguage(TargetLanguage.SPANISH)
            repo.setDifficulty(DifficultyLevel.INTERMEDIATE)
            val sessionId = repo.recordGameCompletion(
                gameType = GameType.CARDS,
                language = TargetLanguage.SPANISH,
                difficulty = DifficultyLevel.INTERMEDIATE,
                scorePercent = 85,
                xpEarned = 60,
                durationSeconds = 40,
                summary = "Spanish Cards 85%"
            )
            assertTrue(sessionId.isNotBlank())
        }

        val profile = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeUserProfile(uid).first { it != null && it.totalXp >= 60L }
        }
        assertNotNull(profile)
        assertEquals(TargetLanguage.SPANISH.code, profile!!.activeLanguage)
        assertEquals(DifficultyLevel.INTERMEDIATE.code, profile.difficulty)
        assertEquals(60L, profile.totalXp)

        val mastery = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeAllMastery(uid).first {
                (it[TargetLanguage.SPANISH]?.gamesPlayed ?: 0L) >= 1L
            }
        }
        assertEquals(51L, mastery[TargetLanguage.SPANISH]?.vocabularyScore)

        val sessions = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeRecentSessions(uid).first { it.isNotEmpty() }
        }
        assertEquals(1, sessions.size)
        assertEquals("Spanish Cards 85%", sessions.first().summary)
    }

    @Test
    fun saveAndDeletePhrase_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser("phrase_${UUID.randomUUID()}@test.com")
        val repo = ProgressRepository(firestore, auth)

        val phraseId = withTimeout(TIMEOUT_MS) {
            repo.savePhrase(
                language = TargetLanguage.FRENCH,
                targetText = "Bonjour",
                phonetic = "bohn-ZHOOR",
                englishMeaning = "Hello",
                sourceGame = "speech"
            )
        }
        assertTrue(phraseId.isNotBlank())

        val phrases = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeSavedPhrases(uid).first { list -> list.any { it.id == phraseId } }
        }
        assertTrue(phrases.any { it.targetText == "Bonjour" })

        withTimeout(TIMEOUT_MS) {
            repo.deleteSavedPhrase(phraseId)
        }
        val afterDelete = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeSavedPhrases(uid).first { list -> list.none { it.id == phraseId } }
        }
        assertTrue(afterDelete.none { it.id == phraseId })
    }

    @Test
    fun crossUserRead_isRejectedWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser("alice_iso_${UUID.randomUUID()}@test.com")
        val aliceRepo = ProgressRepository(firestore, auth)
        withTimeout(TIMEOUT_MS) {
            aliceRepo.ensureUserInitialized("Alice")
        }

        signInTestUser("bob_iso_${UUID.randomUUID()}@test.com")
        try {
            withTimeout(TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid).get().await()
            }
            fail("Expected PERMISSION_DENIED when reading another user's profile")
        } catch (e: Exception) {
            val fsError = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException, got $e", fsError)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError!!.code)
        }
    }

    @Test
    fun unauthenticatedObserve_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = ProgressRepository(firestore, auth)
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeRecentSessions("some_uid_123").first()
            }
            fail("Expected PERMISSION_DENIED for unauthenticated caller")
        } catch (e: Exception) {
            val fsError = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", fsError)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError!!.code)
        }
    }

    private companion object {
        const val TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
