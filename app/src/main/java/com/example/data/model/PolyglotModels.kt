package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class TargetLanguage(
    val code: String,
    val displayName: String,
    val nativeLabel: String,
    val shortBadge: String,
    val ttsLanguageHint: String
) {
    JAPANESE("japanese", "Japanese", "日本語", "JP", "Japanese (Tokyo standard)"),
    SPANISH("spanish", "Spanish", "Español", "ES", "Spanish (clear neutral)"),
    FRENCH("french", "French", "Français", "FR", "French (Parisian standard)");

    companion object {
        fun fromCode(code: String?): TargetLanguage =
            entries.firstOrNull { it.code == code } ?: JAPANESE
    }
}

enum class DifficultyLevel(
    val code: String,
    val label: String
) {
    BEGINNER("beginner", "Beginner"),
    INTERMEDIATE("intermediate", "Intermediate"),
    ADVANCED("advanced", "Advanced");

    companion object {
        fun fromCode(code: String?): DifficultyLevel =
            entries.firstOrNull { it.code == code } ?: BEGINNER
    }
}

enum class GameType(
    val code: String,
    val title: String,
    val subtitle: String,
    val linguisticSkill: String
) {
    CARDS(
        code = "cards",
        title = "LexiMatch Cards",
        subtitle = "Pair scripts & meanings under combo streak pressure",
        linguisticSkill = "Vocabulary & Orthography"
    ),
    CALLIGRAPHY(
        code = "calligraphy",
        title = "Ink & Stroke Studio",
        subtitle = "Draw characters on canvas with Gemini Vision grading",
        linguisticSkill = "Writing & Script Form"
    ),
    QUIZ(
        code = "quiz",
        title = "Syntax Sprint Quiz",
        subtitle = "Fast grammar, particles & sentence-building rounds",
        linguisticSkill = "Grammar & Syntax"
    ),
    VOICE(
        code = "voice",
        title = "Echo Voice Arena",
        subtitle = "Hear native TTS prompts and speak back for feedback",
        linguisticSkill = "Listening & Phonetics"
    ),
    STORY(
        code = "story",
        title = "Story Café Dialogues",
        subtitle = "Navigate branching cultural conversations in real life",
        linguisticSkill = "Pragmatics & Discourse"
    );

    companion object {
        fun fromCode(code: String?): GameType =
            entries.firstOrNull { it.code == code } ?: CARDS
    }
}

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Learner",
    val activeLanguage: String = TargetLanguage.JAPANESE.code,
    val difficulty: String = DifficultyLevel.BEGINNER.code,
    val totalXp: Long = 0L,
    val currentStreak: Long = 0L,
    val lastActiveDate: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class LanguageMastery(
    @DocumentId val id: String = "",
    val languageCode: String = TargetLanguage.JAPANESE.code,
    val vocabularyScore: Long = 0L,
    val writingScore: Long = 0L,
    val grammarScore: Long = 0L,
    val phoneticsScore: Long = 0L,
    val pragmaticsScore: Long = 0L,
    val languageXp: Long = 0L,
    val gamesPlayed: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun scoreFor(gameType: GameType): Int = when (gameType) {
        GameType.CARDS -> vocabularyScore.toInt()
        GameType.CALLIGRAPHY -> writingScore.toInt()
        GameType.QUIZ -> grammarScore.toInt()
        GameType.VOICE -> phoneticsScore.toInt()
        GameType.STORY -> pragmaticsScore.toInt()
    }
}

@IgnoreExtraProperties
data class GameSessionRecord(
    @DocumentId val id: String = "",
    val gameType: String = GameType.CARDS.code,
    val languageCode: String = TargetLanguage.JAPANESE.code,
    val difficulty: String = DifficultyLevel.BEGINNER.code,
    val scorePercent: Long = 0L,
    val xpEarned: Long = 0L,
    val durationSeconds: Long = 0L,
    val summary: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SavedPhrase(
    @DocumentId val id: String = "",
    val languageCode: String = TargetLanguage.JAPANESE.code,
    val targetText: String = "",
    val phonetic: String = "",
    val englishMeaning: String = "",
    val sourceGame: String = "cards",
    @ServerTimestamp val createdAt: Timestamp? = null
)
