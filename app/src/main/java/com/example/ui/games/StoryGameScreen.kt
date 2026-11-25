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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.content.CuratedContent
import com.example.data.content.StoryCatalog
import com.example.data.content.StoryChoice
import com.example.data.content.StoryDefinition
import com.example.data.content.StoryNode
import com.example.data.model.DifficultyLevel
import com.example.data.model.GameType
import com.example.data.model.TargetLanguage
import com.example.ui.components.StudioBadge
import com.example.ui.components.TactileButton
import com.example.ui.components.TactileCard
import com.example.ui.theme.GoldStreak
import com.example.ui.theme.LocalStudioPalette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private data class StoryTurnItem(
    val isNpc: Boolean,
    val nodeId: String? = null,
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
    speakingKey: String?,
    onSpeak: (key: String, text: String, language: TargetLanguage, slow: Boolean) -> Unit,
    onSavePhrase: (TargetLanguage, String, String, String, String) -> Unit,
    onCompleteSession: suspend (GameType, TargetLanguage, DifficultyLevel, Int, Int, Int, String) -> Unit,
    onBack: () -> Unit,
    storiesOverride: List<StoryDefinition>? = null
) {
    val palette = LocalStudioPalette.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val stories = remember(language, difficulty, storiesOverride) {
        val loaded = storiesOverride ?: StoryCatalog.stories(language, difficulty)
        if (loaded.isNotEmpty()) loaded else fallbackStories(language, difficulty)
    }

    var storyIndex by rememberSaveable(language, difficulty) { mutableIntStateOf(0) }
    val safeIndex = storyIndex.coerceIn(0, stories.lastIndex)
    val story = stories[safeIndex]

    var roundKey by remember(language, difficulty, story.id) { mutableIntStateOf(0) }
    var showStoryPicker by remember(language, difficulty) { mutableStateOf(false) }

    val openingNode = remember(story) {
        story.nodes[story.startNodeId] ?: story.nodes.values.first()
    }

    var currentNodeId by remember(roundKey, language, difficulty, story.id) {
        mutableStateOf(openingNode.id)
    }
    val currentNode = story.nodes[currentNodeId] ?: openingNode

    val turns = remember(roundKey, language, difficulty, story.id) {
        mutableStateListOf(
            StoryTurnItem(
                isNpc = true,
                nodeId = openingNode.id,
                text = openingNode.npcText,
                phonetic = openingNode.npcPhonetic,
                english = openingNode.npcEnglish
            )
        )
    }
    val naturalnessScores = remember(roundKey, language, difficulty, story.id) {
        mutableStateListOf<Int>()
    }
    var userTurnCount by remember(roundKey, language, difficulty, story.id) { mutableIntStateOf(0) }
    var savedIndices by remember(roundKey, language, difficulty, story.id) { mutableStateOf(setOf<Int>()) }
    var startTimeMs by remember(roundKey, language, difficulty, story.id) {
        mutableLongStateOf(System.currentTimeMillis())
    }
    var completedResult by remember(roundKey, language, difficulty, story.id) {
        mutableStateOf<Pair<Int, Int>?>(null)
    }
    var completedDurationSec by remember(roundKey, language, difficulty, story.id) {
        mutableIntStateOf(10)
    }
    var saveState by remember(roundKey, language, difficulty, story.id) {
        mutableStateOf(CompletionSaveState.Idle)
    }
    var saveError by remember(roundKey, language, difficulty, story.id) {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(turns.size, story.id, roundKey) {
        if (turns.isNotEmpty()) {
            // Item 0 in LazyColumn is the scenario header card, so the last turn is at index turns.size.
            listState.animateScrollToItem(turns.size)
        }
    }

    fun selectChoice(choice: StoryChoice) {
        if (completedResult != null || currentNode.choices.isEmpty()) return
        turns.add(
            StoryTurnItem(
                isNpc = false,
                text = choice.text,
                phonetic = choice.phonetic,
                english = choice.english
            )
        )
        userTurnCount++
        naturalnessScores.add(choice.naturalness)
        val nextNode = story.nodes[choice.nextNodeId]
        if (nextNode != null) {
            currentNodeId = nextNode.id
            turns.add(
                StoryTurnItem(
                    isNpc = true,
                    nodeId = nextNode.id,
                    text = nextNode.npcText,
                    phonetic = nextNode.npcPhonetic,
                    english = nextNode.npcEnglish,
                    feedbackNote = choice.feedback.takeIf { it.isNotBlank() },
                    score = choice.naturalness
                )
            )
        }
    }

    fun saveStoryResult(avg: Int, xp: Int, durationSec: Int) {
        if (saveState == CompletionSaveState.Saving || saveState == CompletionSaveState.Saved) return
        saveState = CompletionSaveState.Saving
        saveError = null
        scope.launch {
            try {
                onCompleteSession(
                    GameType.STORY,
                    language,
                    difficulty,
                    avg,
                    xp,
                    durationSec,
                    "${language.displayName} Story Café: ${story.title} ($userTurnCount turns, $avg%)"
                )
                saveState = CompletionSaveState.Saved
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                saveState = CompletionSaveState.Failed
                saveError = e.localizedMessage?.takeIf { it.isNotBlank() }
                    ?: "Could not sync story progress while offline. Tap Retry Save when connected."
            }
        }
    }

    if (showStoryPicker) {
        AlertDialog(
            onDismissRequest = { showStoryPicker = false },
            title = {
                Text(
                    text = "${language.displayName} Stories (${difficulty.label})",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(stories) { idx, item ->
                        val isSelected = idx == safeIndex
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) palette.languageTint else palette.subtleSurface,
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) palette.languageAccent else palette.inkBorder.copy(alpha = 0.45f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("story_picker_item_$idx")
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    storyIndex = idx
                                    showStoryPicker = false
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "${idx + 1}. ${item.title}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    StudioBadge(
                                        text = item.category,
                                        containerColor = palette.cardSurface,
                                        contentColor = palette.languageAccent
                                    )
                                }
                                Text(
                                    text = item.setting,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showStoryPicker = false }) {
                    Text("Close")
                }
            }
        )
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
                skillLabel = "${difficulty.label} • Offline",
                onBack = onBack,
                trailingContent = {
                    if (completedResult == null) {
                        val isFinished = currentNode.choices.isEmpty()
                        StudioBadge(
                            text = "Turn $userTurnCount/3",
                            containerColor = if (isFinished) palette.successSoft else palette.subtleSurface,
                            contentColor = if (isFinished) palette.success else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )

            if (completedResult != null) {
                val (avgScore, xp) = completedResult!!
                val hasMultipleStories = stories.size > 1
                Box(modifier = Modifier.padding(16.dp)) {
                    GameCompletionCard(
                        gameType = GameType.STORY,
                        scorePercent = avgScore,
                        xpEarned = xp,
                        summaryText = "Completed $userTurnCount conversational turns in \"${story.title}\" (${language.displayName}).",
                        isSaving = saveState == CompletionSaveState.Saving,
                        syncLabel = when (saveState) {
                            CompletionSaveState.Saving -> "Syncing…"
                            CompletionSaveState.Saved -> "Synced to Cloud"
                            CompletionSaveState.Failed -> "Not saved yet"
                            CompletionSaveState.Idle -> null
                        },
                        saveError = saveError,
                        onRetrySave = if (saveState == CompletionSaveState.Failed) {
                            { saveStoryResult(avgScore, xp, completedDurationSec) }
                        } else {
                            null
                        },
                        primaryActionLabel = if (hasMultipleStories) "Next Story" else "Play Again",
                        primaryActionTag = if (hasMultipleStories) "next_story_button" else "play_again_button",
                        onPlayAgain = {
                            if (hasMultipleStories) {
                                storyIndex = (safeIndex + 1) % stories.size
                            } else {
                                roundKey++
                            }
                        },
                        onBackToStudio = onBack
                    )
                }
            } else {
                // Compact offline story switcher bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (stories.size > 1) {
                                storyIndex = (safeIndex - 1 + stories.size) % stories.size
                            }
                        },
                        enabled = stories.size > 1,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("story_prev_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ChevronLeft,
                            contentDescription = "Previous story"
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = palette.cardSurface,
                        border = BorderStroke(1.5.dp, palette.inkBorder),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(14.dp))
                            .testTag("story_picker_button")
                            .clickable { showStoryPicker = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MenuBook,
                                contentDescription = null,
                                tint = palette.languageAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Story ${safeIndex + 1}/${stories.size} · Choose",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            if (stories.size > 1) {
                                storyIndex = (safeIndex + 1) % stories.size
                            }
                        },
                        enabled = stories.size > 1,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("story_next_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = "Next story"
                        )
                    }
                }

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
                                    painter = painterResource(id = story.drawableRes),
                                    contentDescription = story.title,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(140.dp)
                                        .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = story.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        StudioBadge(
                                            text = story.category,
                                            containerColor = palette.languageTint,
                                            contentColor = palette.languageAccent
                                        )
                                    }
                                    Text(
                                        text = story.setting,
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
                            val speakerKey = turn.nodeId?.let { StoryCatalog.speakerKey(story.id, it) }
                                ?: "story_npc_$index"
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
                                                onSpeak(speakerKey, turn.text, language, false)
                                            },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .testTag("story_speak_$index")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.VolumeUp,
                                                contentDescription = "Hear dialogue line",
                                                tint = if (speakingKey == speakerKey) GoldStreak else MaterialTheme.colorScheme.primary
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
                }

                // Bottom Instant Offline Branch Choices or Complete Button
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
                        if (currentNode.choices.isNotEmpty()) {
                            currentNode.choices.take(2).forEachIndexed { cIdx, choice ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = palette.subtleSurface,
                                    border = BorderStroke(1.5.dp, palette.inkBorder),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .minimumInteractiveComponentSize()
                                        .testTag("story_choice_$cIdx")
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { selectChoice(choice) }
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                        Text(
                                            text = choice.text,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${choice.phonetic} • ${choice.english}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else {
                            TactileButton(
                                onClick = {
                                    val avg = if (naturalnessScores.isEmpty()) 85
                                    else naturalnessScores.average().toInt().coerceIn(50, 100)
                                    val xp = 45 + (userTurnCount * 8)
                                    val duration = ((System.currentTimeMillis() - startTimeMs) / 1000L)
                                        .toInt().coerceAtLeast(10)
                                    completedResult = avg to xp
                                    completedDurationSec = duration
                                    saveStoryResult(avg, xp, duration)
                                },
                                containerColor = palette.success,
                                contentColor = palette.onLanguageAccent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("story_complete_button")
                            ) {
                                Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
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

private fun fallbackStories(language: TargetLanguage, difficulty: DifficultyLevel): List<StoryDefinition> {
    val seed = CuratedContent.storyScenarios(language).first()
    val closing = StoryNode(
        id = "n3",
        npcText = seed.openingNpcLine,
        npcReading = "",
        npcPhonetic = seed.openingNpcPhonetic,
        npcEnglish = seed.openingNpcEnglish,
        choices = emptyList()
    )
    val opening = StoryNode(
        id = "n0",
        npcText = seed.openingNpcLine,
        npcReading = "",
        npcPhonetic = seed.openingNpcPhonetic,
        npcEnglish = seed.openingNpcEnglish,
        choices = seed.initialChoices.map {
            StoryChoice(
                text = it.targetText,
                reading = "",
                phonetic = it.phonetic,
                english = it.englishMeaning,
                naturalness = 92,
                feedback = "Natural offline reply.",
                nextNodeId = "n3"
            )
        }
    )
    return listOf(
        StoryDefinition(
            id = "${language.code}_fallback",
            language = language,
            level = difficulty,
            title = seed.title,
            setting = seed.setting,
            category = "Culture",
            drawableRes = seed.drawableRes,
            startNodeId = "n0",
            nodes = mapOf("n0" to opening, "n3" to closing)
        )
    )
}
