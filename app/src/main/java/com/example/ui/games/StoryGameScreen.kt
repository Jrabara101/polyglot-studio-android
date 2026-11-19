package com.example.ui.games

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.ai.FirebaseAiRepository
import com.example.data.content.CuratedContent
import com.example.data.content.StoryChoiceSeed
import com.example.data.model.DifficultyLevel
import com.example.data.model.GameType
import com.example.data.model.TargetLanguage
import com.example.ui.components.StudioBadge
import com.example.ui.components.TactileButton
import com.example.ui.components.TactileCard
import com.example.ui.theme.GoldStreak
import com.example.ui.theme.LocalStudioPalette
import kotlinx.coroutines.launch

private data class StoryTurnItem(
    val isNpc: Boolean,
    val text: String,
    val phonetic: String,
    val english: String,
    val feedbackNote: String? = null,
    val score: Int? = null
)

@Composable
fun StoryGameScreen(
    language: TargetLanguage,
    difficulty: DifficultyLevel,
    aiRepository: FirebaseAiRepository,
    speakingKey: String?,
    onSpeak: (key: String, text: String, language: TargetLanguage, slow: Boolean) -> Unit,
    onSavePhrase: (TargetLanguage, String, String, String, String) -> Unit,
    onCompleteSession: suspend (GameType, TargetLanguage, DifficultyLevel, Int, Int, Int, String) -> Unit,
    onBack: () -> Unit
) {
    val palette = LocalStudioPalette.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var roundKey by remember(language, difficulty) { mutableIntStateOf(0) }
    val scenario = remember(language) { CuratedContent.storyScenarios(language).first() }

    val turns = remember(roundKey, language, difficulty) {
        mutableStateListOf(
            StoryTurnItem(
                isNpc = true,
                text = scenario.openingNpcLine,
                phonetic = scenario.openingNpcPhonetic,
                english = scenario.openingNpcEnglish
            )
        )
    }
    var choices by remember(roundKey, language, difficulty) { mutableStateOf(scenario.initialChoices) }
    var customReply by remember(roundKey, language, difficulty) { mutableStateOf("") }
    var isThinking by remember(roundKey, language, difficulty) { mutableStateOf(false) }
    var errorMessage by remember(roundKey, language, difficulty) { mutableStateOf<String?>(null) }
    var naturalnessScores = remember(roundKey, language, difficulty) { mutableStateListOf<Int>() }
    var userTurnCount by remember(roundKey, language, difficulty) { mutableIntStateOf(0) }
    var savedIndices by remember(roundKey, language, difficulty) { mutableStateOf(setOf<Int>()) }
    var startTimeMs by remember(roundKey, language, difficulty) { mutableLongStateOf(System.currentTimeMillis()) }
    var isSavingResult by remember { mutableStateOf(false) }
    var completedResult by remember(roundKey, language, difficulty) { mutableStateOf<Pair<Int, Int>?>(null) }

    LaunchedEffect(turns.size) {
        if (turns.isNotEmpty()) {
            listState.animateScrollToItem(turns.lastIndex)
        }
    }

    fun submitReply(targetText: String, phonetic: String, english: String) {
        val clean = targetText.trim()
        if (clean.isEmpty() || isThinking) return
        errorMessage = null
        turns.add(
            StoryTurnItem(
                isNpc = false,
                text = clean,
                phonetic = phonetic,
                english = english
            )
        )
        customReply = ""
        userTurnCount++
        isThinking = true
        val historySummary = turns.takeLast(6).joinToString("\n") {
            val speaker = if (it.isNpc) "Local" else "Learner"
            "$speaker: ${it.text}"
        }
        scope.launch {
            try {
                val res = aiRepository.continueStoryTurn(
                    scenarioTitle = scenario.title,
                    language = language,
                    difficulty = difficulty,
                    historySummary = historySummary,
                    userReply = clean
                )
                naturalnessScores.add(res.naturalnessScore)
                turns.add(
                    StoryTurnItem(
                        isNpc = true,
                        text = res.npcLine,
                        phonetic = res.npcPhonetic,
                        english = res.npcEnglish,
                        feedbackNote = res.replyFeedback,
                        score = res.naturalnessScore
                    )
                )
                if (res.suggestedReplies.isNotEmpty()) {
                    choices = res.suggestedReplies
                }
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Could not continue story turn."
            } finally {
                isThinking = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
        ) {
            GameTopBar(
                title = GameType.STORY.title,
                language = language,
                skillLabel = "Pragmatics & Discourse",
                onBack = onBack,
                trailingContent = {
                    if (completedResult == null) {
                        StudioBadge(
                            text = "Turn $userTurnCount/3+",
                            containerColor = if (userTurnCount >= 3) palette.successSoft else palette.subtleSurface,
                            contentColor = if (userTurnCount >= 3) palette.success else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )

            if (completedResult != null) {
                val (avgScore, xp) = completedResult!!
                Box(modifier = Modifier.padding(16.dp)) {
                    GameCompletionCard(
                        gameType = GameType.STORY,
                        scorePercent = avgScore,
                        xpEarned = xp,
                        summaryText = "Completed $userTurnCount conversational turns in \"${scenario.title}\" (${language.displayName}).",
                        isSaving = isSavingResult,
                        onPlayAgain = { roundKey++ },
                        onBackToStudio = onBack
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        TactileCard(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Column {
                                Image(
                                    painter = painterResource(id = scenario.drawableRes),
                                    contentDescription = scenario.title,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(140.dp)
                                        .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = scenario.title,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = scenario.setting,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    itemsIndexed(turns) { index, turn ->
                        val isSaved = index in savedIndices
                        if (turn.isNpc) {
                            if (turn.feedbackNote != null && turn.score != null) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = palette.aiSoft,
                                    border = BorderStroke(1.dp, palette.aiAccent.copy(alpha = 0.45f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp)
                                ) {
                                    Text(
                                        text = "Naturalness ${turn.score}% • ${turn.feedbackNote}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = palette.aiAccent,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }

                            TactileCard(
                                modifier = Modifier.fillMaxWidth(0.94f),
                                containerColor = palette.cardSurface,
                                contentPadding = PaddingValues(12.dp)
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "LOCAL HOST",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = palette.languageAccent,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = {
                                                onSpeak("story_npc_$index", turn.text, language, false)
                                            },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .testTag("story_speak_$index")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.VolumeUp,
                                                contentDescription = "Hear dialogue line",
                                                tint = if (speakingKey == "story_npc_$index") GoldStreak else MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                onSavePhrase(
                                                    language,
                                                    turn.text,
                                                    turn.phonetic,
                                                    turn.english,
                                                    GameType.STORY.code
                                                )
                                                savedIndices = savedIndices + index
                                            },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .testTag("story_bookmark_$index")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.BookmarkAdd,
                                                contentDescription = "Save dialogue phrase",
                                                tint = if (isSaved) palette.success else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                    Text(
                                        text = turn.text,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (turn.phonetic.isNotBlank()) {
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = turn.phonetic,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = palette.languageAccent
                                        )
                                    }
                                    if (turn.english.isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = turn.english,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = palette.languageTint,
                                    border = BorderStroke(1.5.dp, palette.languageAccent),
                                    modifier = Modifier.fillMaxWidth(0.86f)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "YOU",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = palette.languageAccent
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = turn.text,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (turn.english.isNotBlank()) {
                                            Text(
                                                text = turn.english,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (isThinking) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 6.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Story host is replying…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (errorMessage != null) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = palette.dangerSoft,
                                border = BorderStroke(1.dp, palette.danger),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = errorMessage ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.danger,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }

                // Bottom Choice Cards + Custom Reply + Complete Button
                Surface(
                    color = palette.cardSurface,
                    border = BorderStroke(1.5.dp, palette.inkBorder),
                    shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        choices.take(2).forEachIndexed { cIdx, choice ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = palette.subtleSurface,
                                border = BorderStroke(1.5.dp, palette.inkBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("story_choice_$cIdx")
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable(enabled = !isThinking) {
                                        submitReply(choice.targetText, choice.phonetic, choice.englishMeaning)
                                    }
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Text(
                                        text = choice.targetText,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${choice.phonetic} • ${choice.englishMeaning}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = customReply,
                                onValueChange = { customReply = it },
                                placeholder = {
                                    Text(
                                        text = "Or type your own reply…",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = palette.languageAccent,
                                    unfocusedBorderColor = palette.inkBorder.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("story_custom_input")
                            )
                            IconButton(
                                onClick = { submitReply(customReply, "", "Custom response") },
                                enabled = customReply.isNotBlank() && !isThinking,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("story_send_custom_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = "Send custom reply",
                                    tint = palette.languageAccent
                                )
                            }
                        }

                        if (userTurnCount >= 2) {
                            TactileButton(
                                onClick = {
                                    val avg = if (naturalnessScores.isEmpty()) 85 else naturalnessScores.average().toInt().coerceIn(50, 100)
                                    val xp = 45 + (userTurnCount * 8)
                                    val duration = ((System.currentTimeMillis() - startTimeMs) / 1000L).toInt().coerceAtLeast(10)
                                    completedResult = avg to xp
                                    scope.launch {
                                        isSavingResult = true
                                        try {
                                            onCompleteSession(
                                                GameType.STORY,
                                                language,
                                                difficulty,
                                                avg,
                                                xp,
                                                duration,
                                                "${language.displayName} Story Café ($userTurnCount turns, $avg%)"
                                            )
                                        } finally {
                                            isSavingResult = false
                                        }
                                    }
                                },
                                enabled = !isThinking,
                                containerColor = palette.success,
                                contentColor = palette.onLanguageAccent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("story_complete_button")
                            ) {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Complete Story (+XP)", style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
