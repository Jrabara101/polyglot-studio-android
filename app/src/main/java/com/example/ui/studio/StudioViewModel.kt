package com.example.ui.studio

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.PcmAudioPlayer
import com.example.data.ai.FirebaseAiRepository
import com.example.data.model.DifficultyLevel
import com.example.data.model.GameSessionRecord
import com.example.data.model.GameType
import com.example.data.model.LanguageMastery
import com.example.data.model.SavedPhrase
import com.example.data.model.TargetLanguage
import com.example.data.model.UserProfile
import com.example.data.repository.ProgressRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface StudioUiState<out T> {
    data object Loading : StudioUiState<Nothing>
    data class Success<T>(val data: T) : StudioUiState<T>
    data class Error(val message: String) : StudioUiState<Nothing>
}

class StudioViewModel(
    private val progressRepository: ProgressRepository,
    private val aiRepository: FirebaseAiRepository,
    private val audioPlayer: PcmAudioPlayer,
    private val currentUserId: String
) : ViewModel() {

    private val _speakingKey = MutableStateFlow<String?>(null)
    val speakingKey: StateFlow<String?> = _speakingKey.asStateFlow()

    private val _ttsError = MutableStateFlow<String?>(null)
    val ttsError: StateFlow<String?> = _ttsError.asStateFlow()

    private var ttsJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                val displayName = Firebase.auth.currentUser?.displayName ?: "Polyglot Learner"
                progressRepository.ensureUserInitialized(displayName)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize user profile", e)
            }
        }
    }

    val profileState: StateFlow<StudioUiState<UserProfile>> =
        progressRepository.observeUserProfile(currentUserId)
            .map<UserProfile?, StudioUiState<UserProfile>> { profile ->
                StudioUiState.Success(
                    profile ?: UserProfile(
                        userId = currentUserId,
                        displayName = Firebase.auth.currentUser?.displayName ?: "Polyglot Learner"
                    )
                )
            }
            .catch { error ->
                emit(StudioUiState.Error(unwrapFirestoreMessage(error, "Failed to load profile")))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = StudioUiState.Loading
            )

    val masteryState: StateFlow<StudioUiState<Map<TargetLanguage, LanguageMastery>>> =
        progressRepository.observeAllMastery(currentUserId)
            .map<Map<TargetLanguage, LanguageMastery>, StudioUiState<Map<TargetLanguage, LanguageMastery>>> {
                StudioUiState.Success(it)
            }
            .catch { error ->
                emit(StudioUiState.Error(unwrapFirestoreMessage(error, "Failed to load mastery")))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = StudioUiState.Loading
            )

    val sessionsState: StateFlow<StudioUiState<List<GameSessionRecord>>> =
        progressRepository.observeRecentSessions(currentUserId)
            .map<List<GameSessionRecord>, StudioUiState<List<GameSessionRecord>>> {
                StudioUiState.Success(it)
            }
            .catch { error ->
                emit(StudioUiState.Error(unwrapFirestoreMessage(error, "Failed to load history")))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = StudioUiState.Loading
            )

    val savedPhrasesState: StateFlow<StudioUiState<List<SavedPhrase>>> =
        progressRepository.observeSavedPhrases(currentUserId)
            .map<List<SavedPhrase>, StudioUiState<List<SavedPhrase>>> {
                StudioUiState.Success(it)
            }
            .catch { error ->
                emit(StudioUiState.Error(unwrapFirestoreMessage(error, "Failed to load saved phrases")))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = StudioUiState.Loading
            )

    fun selectLanguage(language: TargetLanguage) {
        viewModelScope.launch {
            try {
                progressRepository.setActiveLanguage(language)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update active language", e)
            }
        }
    }

    fun selectDifficulty(difficulty: DifficultyLevel) {
        viewModelScope.launch {
            try {
                progressRepository.setDifficulty(difficulty)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update difficulty", e)
            }
        }
    }

    fun speakText(key: String, text: String, language: TargetLanguage, slow: Boolean = false) {
        ttsJob?.cancel()
        audioPlayer.stop()
        _ttsError.value = null
        ttsJob = viewModelScope.launch {
            _speakingKey.value = key
            try {
                val pcm = aiRepository.synthesizeSpeechPcm(text = text, language = language, slow = slow)
                audioPlayer.playPcm24k(pcm)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "TTS playback failed", e)
                _ttsError.value = e.localizedMessage ?: "Speech audio unavailable right now."
            } finally {
                if (_speakingKey.value == key) {
                    _speakingKey.value = null
                }
            }
        }
    }

    fun clearTtsError() {
        _ttsError.value = null
    }

    fun savePhrase(
        language: TargetLanguage,
        targetText: String,
        phonetic: String,
        englishMeaning: String,
        sourceGame: String
    ) {
        viewModelScope.launch {
            try {
                progressRepository.savePhrase(
                    language = language,
                    targetText = targetText,
                    phonetic = phonetic,
                    englishMeaning = englishMeaning,
                    sourceGame = sourceGame
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save phrase", e)
            }
        }
    }

    fun deleteSavedPhrase(phraseId: String) {
        viewModelScope.launch {
            try {
                progressRepository.deleteSavedPhrase(phraseId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete phrase", e)
            }
        }
    }

    suspend fun recordCompletion(
        gameType: GameType,
        language: TargetLanguage,
        difficulty: DifficultyLevel,
        scorePercent: Int,
        xpEarned: Int,
        durationSeconds: Int,
        summary: String
    ) {
        progressRepository.recordGameCompletion(
            gameType = gameType,
            language = language,
            difficulty = difficulty,
            scorePercent = scorePercent,
            xpEarned = xpEarned,
            durationSeconds = durationSeconds,
            summary = summary
        )
    }

    private fun unwrapFirestoreMessage(error: Throwable, fallback: String): String {
        val fsError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (fsError == null && error is CancellationException) throw error
        return (fsError ?: error).localizedMessage ?: fallback
    }

    override fun onCleared() {
        super.onCleared()
        ttsJob?.cancel()
        audioPlayer.stop()
    }

    private companion object {
        const val TAG = "StudioViewModel"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
