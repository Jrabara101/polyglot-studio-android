package com.example.ui.games

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.ai.FirebaseAiRepository
import com.example.data.content.CuratedContent
import com.example.data.model.DifficultyLevel
import com.example.data.model.GameType
import com.example.data.model.TargetLanguage
import com.example.ui.components.StudioIcons
import com.example.ui.components.StudioBadge
import com.example.ui.components.TactileButton
import com.example.ui.components.TactileCard
import com.example.ui.theme.LocalStudioPalette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun QuizGameScreen(
    language: TargetLanguage,
    difficulty: DifficultyLevel,
    aiRepository: FirebaseAiRepository,
    speakingKey: String?,
    onSpeak: (key: String, text: String, language: TargetLanguage, slow: Boolean) -> Unit,
    onCompleteSession: suspend (GameType, TargetLanguage, DifficultyLevel, Int, Int, Int, String) -> Unit,
    onBack: () -> Unit
) {
    val palette = LocalStudioPalette.current
    val scope = rememberCoroutineScope()

    var questions by remember(language, difficulty) {
        mutableStateOf(CuratedContent.quizQuestions(language, difficulty))
    }
    var currentIdx by remember(language, difficulty) { mutableIntStateOf(0) }
    var selectedOption by remember(currentIdx, questions) { mutableStateOf<Int?>(null) }
    var correctCount by remember(language, difficulty) { mutableIntStateOf(0) }
    var streak by remember(language, difficulty) { mutableIntStateOf(0) }
    var isGeneratingAiRound by remember { mutableStateOf(false) }
    var aiError by remember { mutableStateOf<String?>(null) }
    var deepExplanation by remember(currentIdx, questions) { mutableStateOf<String?>(null) }
    var isLoadingDeep by remember { mutableStateOf(false) }

    var roundKey by remember(language, difficulty) { mutableIntStateOf(0) }
    var startTimeMs by remember(roundKey, language, difficulty) { mutableLongStateOf(System.currentTimeMillis()) }
    var completedResult by remember(roundKey, language, difficulty) { mutableStateOf<Pair<Int, Int>?>(null) }
    var completedDurationSec by remember(roundKey, language, difficulty) { mutableIntStateOf(5) }
    var saveState by remember(roundKey, language, difficulty) { mutableStateOf(CompletionSaveState.Idle) }

    fun saveQuizResult() {
        val (accuracy, xp) = completedResult ?: return
        if (saveState == CompletionSaveState.Saving || saveState == CompletionSaveState.Saved) return
        saveState = CompletionSaveState.Saving
        scope.launch {
            try {
                onCompleteSession(
                    GameType.QUIZ,
                    language,
                    difficulty,
                    accuracy,
                    xp,
                    completedDurationSec,
                    "${language.displayName} Quiz ($correctCount/${questions.size})"
                )
                saveState = CompletionSaveState.Saved
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                saveState = CompletionSaveState.Failed
            }
        }
    }

    val question = questions[currentIdx.coerceIn(0, questions.lastIndex)]
    val isAnswered = selectedOption != null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            GameTopBar(
                title = GameType.QUIZ.title,
                language = language,
                skillLabel = "Grammar",
                onBack = onBack,
                trailingContent = {
                    StudioBadge(
                        text = "Streak $streak",
                        containerColor = if (streak >= 2) palette.successSoft else palette.subtleSurface,
                        contentColor = if (streak >= 2) palette.success else MaterialTheme.colorScheme.onSurface
                    )
                }
            )

            if (completedResult != null) {
                val (accuracy, xp) = completedResult!!
                Box(modifier = Modifier.padding(16.dp)) {
                    GameCompletionCard(
                        gameType = GameType.QUIZ,
                        scorePercent = accuracy,
                        xpEarned = xp,
                        summaryText = "Answered $correctCount/${questions.size} ${language.displayName} syntax challenges accurately.",
                        isSaving = saveState == CompletionSaveState.Saving,
                        syncLabel = if (saveState == CompletionSaveState.Failed) "Not saved yet" else null,
                        saveError = if (saveState == CompletionSaveState.Failed) {
                            "Couldn't save your result. Check your connection and retry."
                        } else null,
                        onRetrySave = if (saveState == CompletionSaveState.Failed) ::saveQuizResult else null,
                        onPlayAgain = {
                            currentIdx = 0
                            correctCount = 0
                            streak = 0
                            selectedOption = null
                            roundKey++
                        },
                        onBackToStudio = onBack
                    )
                }
            } else {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    // Progress + AI Generate button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Question ${currentIdx + 1} of ${questions.size}",
                            style = MaterialTheme.typography.labelLarge
                        )
                        TactileButton(
                            onClick = {
                                aiError = null
                                isGeneratingAiRound = true
                                scope.launch {
                                    try {
                                        val generated = aiRepository.generateQuizQuestions(language, difficulty, 4)
                                        if (generated.isNotEmpty()) {
                                            questions = generated
                                            currentIdx = 0
                                            correctCount = 0
                                            streak = 0
                                            selectedOption = null
                                            startTimeMs = System.currentTimeMillis()
                                        }
                                    } catch (e: Exception) {
                                        aiError = e.localizedMessage ?: "Could not generate AI quiz round."
                                    } finally {
                                        isGeneratingAiRound = false
                                    }
                                }
                            },
                            enabled = !isGeneratingAiRound,
                            containerColor = palette.aiSoft,
                            contentColor = palette.aiAccent,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("generate_ai_quiz_button")
                        ) {
                            if (isGeneratingAiRound) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = palette.aiAccent,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Generating…", style = MaterialTheme.typography.labelLarge)
                            } else {
                                Icon(StudioIcons.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Fresh AI Set", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (currentIdx + 1).toFloat() / questions.size.toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(50)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = palette.subtleSurface
                    )

                    aiError?.let { msg ->
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = palette.dangerSoft,
                            border = BorderStroke(1.dp, palette.danger),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = msg,
                                color = palette.danger,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    TactileCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text(
                                text = question.prompt,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = palette.languageTint,
                                    border = BorderStroke(1.dp, palette.languageAccent.copy(alpha = 0.4f)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = question.targetSnippet,
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(14.dp)
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                val completedSentence = question.targetSnippet.replace(
                                    "___",
                                    question.options.getOrElse(question.correctIndex) { "" }
                                )
                                IconButton(
                                    onClick = {
                                        onSpeak("quiz_${question.id}", completedSentence, language, false)
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("quiz_speak_button")
                                ) {
                                    Icon(
                                        imageVector = StudioIcons.VolumeUp,
                                        contentDescription = "Hear sentence",
                                        tint = if (speakingKey == "quiz_${question.id}") palette.languageAccent else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    question.options.forEachIndexed { idx, option ->
                        val isSelected = selectedOption == idx
                        val isCorrect = idx == question.correctIndex
                        val bg = when {
                            !isAnswered -> palette.cardSurface
                            isCorrect -> palette.successSoft
                            isSelected -> palette.dangerSoft
                            else -> palette.cardSurface.copy(alpha = 0.6f)
                        }
                        val border = when {
                            !isAnswered -> palette.inkBorder
                            isCorrect -> palette.success
                            isSelected -> palette.danger
                            else -> palette.inkBorder.copy(alpha = 0.4f)
                        }

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = bg,
                            border = BorderStroke(1.5.dp, border),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("quiz_option_$idx")
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(enabled = !isAnswered) {
                                    selectedOption = idx
                                    if (isCorrect) {
                                        correctCount++
                                        streak++
                                    } else {
                                        streak = 0
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${('A' + idx)}.",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = option,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isAnswered && isCorrect) {
                                    Icon(Icons.Rounded.CheckCircle, contentDescription = "Correct", tint = palette.success)
                                } else if (isAnswered && isSelected) {
                                    Icon(StudioIcons.Cancel, contentDescription = "Incorrect", tint = palette.danger)
                                }
                            }
                        }
                    }

                    AnimatedVisibility(visible = isAnswered) {
                        TactileCard(
                            containerColor = palette.subtleSurface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text(
                                    text = question.grammarRule,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (deepExplanation != null) {
                                    Spacer(Modifier.height(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = palette.aiSoft,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = deepExplanation ?: "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (deepExplanation == null) {
                                        TactileButton(
                                            onClick = {
                                                isLoadingDeep = true
                                                scope.launch {
                                                    try {
                                                        deepExplanation = aiRepository.explainGrammarDetail(
                                                            language = language,
                                                            snippet = question.targetSnippet,
                                                            correctOption = question.options.getOrElse(question.correctIndex) { "" }
                                                        )
                                                    } catch (e: Exception) {
                                                        deepExplanation = e.localizedMessage ?: "Explanation unavailable."
                                                    } finally {
                                                        isLoadingDeep = false
                                                    }
                                                }
                                            },
                                            enabled = !isLoadingDeep,
                                            containerColor = palette.aiSoft,
                                            contentColor = palette.aiAccent,
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                            modifier = Modifier.testTag("ask_why_button")
                                        ) {
                                            Icon(StudioIcons.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(if (isLoadingDeep) "Thinking…" else "Ask Gemini Why")
                                        }
                                    }
                                    Spacer(Modifier.weight(1f))
                                    TactileButton(
                                        onClick = {
                                            if (currentIdx < questions.lastIndex) {
                                                currentIdx++
                                            } else {
                                                val accuracy = ((correctCount.toFloat() / questions.size.toFloat()) * 100f)
                                                    .toInt().coerceIn(0, 100)
                                                val xp = 25 + (correctCount * 12)
                                                completedDurationSec = ((System.currentTimeMillis() - startTimeMs) / 1000L).toInt().coerceAtLeast(5)
                                                completedResult = accuracy to xp
                                                saveQuizResult()
                                            }
                                        },
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = Color.White,
                                        modifier = Modifier.testTag("quiz_next_button")
                                    ) {
                                        Text(if (currentIdx < questions.lastIndex) "Next" else "Finish")
                                        Spacer(Modifier.width(4.dp))
                                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}
