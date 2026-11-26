package com.example.ui.games

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.content.CardPairItem
import com.example.data.content.CuratedContent
import com.example.data.model.DifficultyLevel
import com.example.data.model.GameType
import com.example.data.model.TargetLanguage
import com.example.ui.components.StudioBadge
import com.example.ui.components.TactileCard
import com.example.ui.theme.GoldStreak
import com.example.ui.theme.LocalStudioPalette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class MemoryCardTile(
    val tileId: String,
    val pairId: String,
    val isTargetSide: Boolean,
    val mainText: String,
    val subText: String,
    val item: CardPairItem
)

@Composable
fun CardMatchGameScreen(
    language: TargetLanguage,
    difficulty: DifficultyLevel,
    speakingKey: String?,
    onSpeak: (key: String, text: String, language: TargetLanguage, slow: Boolean) -> Unit,
    onSavePhrase: suspend (TargetLanguage, String, String, String, String) -> Result<String>,
    onCompleteSession: suspend (GameType, TargetLanguage, DifficultyLevel, Int, Int, Int, String) -> Unit,
    onBack: () -> Unit
) {
    val palette = LocalStudioPalette.current
    val scope = rememberCoroutineScope()

    var roundKey by remember(language, difficulty) { mutableIntStateOf(0) }
    val deck = remember(language, difficulty, roundKey) {
        val pairs = CuratedContent.cardPairs(language, difficulty).shuffled().take(6)
        pairs.flatMap { item ->
            listOf(
                MemoryCardTile(
                    tileId = "${item.id}_t",
                    pairId = item.id,
                    isTargetSide = true,
                    mainText = item.targetText,
                    subText = item.phonetic,
                    item = item
                ),
                MemoryCardTile(
                    tileId = "${item.id}_e",
                    pairId = item.id,
                    isTargetSide = false,
                    mainText = item.englishMeaning,
                    subText = item.category,
                    item = item
                )
            )
        }.shuffled()
    }

    var matchedPairIds by remember(roundKey, language, difficulty) { mutableStateOf(setOf<String>()) }
    var selectedTiles by remember(roundKey, language, difficulty) { mutableStateOf(listOf<MemoryCardTile>()) }
    var lastRevealedTarget by remember(roundKey, language, difficulty) { mutableStateOf<CardPairItem?>(null) }
    var attempts by remember(roundKey, language, difficulty) { mutableIntStateOf(0) }
    var combo by remember(roundKey, language, difficulty) { mutableIntStateOf(0) }
    var bestCombo by remember(roundKey, language, difficulty) { mutableIntStateOf(0) }
    var isInputLocked by remember(roundKey, language, difficulty) { mutableStateOf(false) }
    var isSavedFeedback by remember { mutableStateOf<String?>(null) }
    var startTimeMs by remember(roundKey, language, difficulty) { mutableLongStateOf(System.currentTimeMillis()) }
    var saveState by remember(roundKey, language, difficulty) { mutableStateOf(CompletionSaveState.Idle) }
    var completedResult by remember(roundKey, language, difficulty) { mutableStateOf<Pair<Int, Int>?>(null) }
    var completedDurationSec by remember(roundKey, language, difficulty) { mutableIntStateOf(5) }

    fun saveCardResult() {
        val (accuracy, xp) = completedResult ?: return
        if (saveState == CompletionSaveState.Saving || saveState == CompletionSaveState.Saved) return
        saveState = CompletionSaveState.Saving
        scope.launch {
            try {
                onCompleteSession(
                    GameType.CARDS,
                    language,
                    difficulty,
                    accuracy,
                    xp,
                    completedDurationSec,
                    "${language.displayName} Card Match ($accuracy%, ${bestCombo}x streak)"
                )
                saveState = CompletionSaveState.Saved
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                saveState = CompletionSaveState.Failed
            }
        }
    }

    LaunchedEffect(selectedTiles) {
        if (selectedTiles.size == 2) {
            isInputLocked = true
            attempts++
            val first = selectedTiles[0]
            val second = selectedTiles[1]
            if (first.pairId == second.pairId) {
                combo++
                if (combo > bestCombo) bestCombo = combo
                delay(280)
                matchedPairIds = matchedPairIds + first.pairId
                lastRevealedTarget = first.item
                selectedTiles = emptyList()
                isInputLocked = false
            } else {
                combo = 0
                delay(750)
                selectedTiles = emptyList()
                isInputLocked = false
            }
        }
    }

    LaunchedEffect(matchedPairIds.size) {
        if (deck.isNotEmpty() && matchedPairIds.size == deck.size / 2 && completedResult == null) {
            val totalPairs = deck.size / 2
            val accuracy = ((totalPairs.toFloat() / attempts.coerceAtLeast(totalPairs)) * 100f)
                .toInt().coerceIn(45, 100)
            val xp = 35 + (accuracy / 4) + (bestCombo * 5)
            completedDurationSec = ((System.currentTimeMillis() - startTimeMs) / 1000L).toInt().coerceAtLeast(5)
            completedResult = accuracy to xp
            saveCardResult()
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
                title = GameType.CARDS.title,
                language = language,
                skillLabel = "Vocabulary",
                onBack = onBack,
                trailingContent = {
                    StudioBadge(
                        text = "${combo}x Combo",
                        containerColor = if (combo >= 2) palette.successSoft else palette.subtleSurface,
                        contentColor = if (combo >= 2) palette.success else MaterialTheme.colorScheme.onSurface
                    )
                }
            )

            // Stats strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Matched ${matchedPairIds.size}/${deck.size / 2} pairs",
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = "Moves: $attempts",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (completedResult != null) {
                val (accuracy, xp) = completedResult!!
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    GameCompletionCard(
                        gameType = GameType.CARDS,
                        scorePercent = accuracy,
                        xpEarned = xp,
                        summaryText = "Paired all ${deck.size / 2} ${language.displayName} cards with a best combo of ${bestCombo}x.",
                        isSaving = saveState == CompletionSaveState.Saving,
                        syncLabel = if (saveState == CompletionSaveState.Failed) "Not saved yet" else null,
                        saveError = if (saveState == CompletionSaveState.Failed) {
                            "Couldn't save your result. Check your connection and retry."
                        } else null,
                        onRetrySave = if (saveState == CompletionSaveState.Failed) ::saveCardResult else null,
                        onPlayAgain = { roundKey++ },
                        onBackToStudio = onBack
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(deck, key = { it.tileId }) { tile ->
                        val isMatched = tile.pairId in matchedPairIds
                        val isFlipped = isMatched || selectedTiles.any { it.tileId == tile.tileId }
                        val cardBg by animateColorAsState(
                            targetValue = when {
                                isMatched -> palette.successSoft
                                isFlipped && tile.isTargetSide -> palette.languageTint
                                isFlipped -> palette.cardSurface
                                else -> palette.subtleSurface
                            },
                            label = "cardBg"
                        )
                        val borderCol = when {
                            isMatched -> palette.success
                            isFlipped -> palette.languageAccent
                            else -> palette.inkBorder
                        }

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = cardBg,
                            border = BorderStroke(1.5.dp, borderCol),
                            modifier = Modifier
                                .height(112.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("card_tile_${tile.tileId}")
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(enabled = !isInputLocked && !isFlipped) {
                                    selectedTiles = selectedTiles + tile
                                    if (tile.isTargetSide) {
                                        lastRevealedTarget = tile.item
                                        onSpeak("card_${tile.tileId}", tile.item.targetText, language, false)
                                    }
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!isFlipped) {
                                    Icon(
                                        imageVector = Icons.Rounded.HelpOutline,
                                        contentDescription = "Hidden card",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(28.dp)
                                    )
                                } else {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        if (isMatched) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = "Matched",
                                                tint = palette.success,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                        Text(
                                            text = tile.mainText,
                                            style = if (tile.isTargetSide) {
                                                MaterialTheme.typography.titleMedium
                                            } else {
                                                MaterialTheme.typography.titleSmall
                                            },
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            text = tile.subText,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Live Audio & Phrase Inspector Bar
                lastRevealedTarget?.let { item ->
                    TactileCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${item.targetText}  •  ${item.englishMeaning}",
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (isSavedFeedback == item.id) "Saved to Phrasebook!" else item.phonetic,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSavedFeedback == item.id) palette.success else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = {
                                    onSpeak("inspect_${item.id}", item.targetText, language, false)
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("card_speak_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.VolumeUp,
                                    contentDescription = "Hear pronunciation",
                                    tint = if (speakingKey == "inspect_${item.id}") GoldStreak else MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        onSavePhrase(
                                            language,
                                            item.targetText,
                                            item.phonetic,
                                            item.englishMeaning,
                                            GameType.CARDS.code
                                        ).onSuccess {
                                            isSavedFeedback = item.id
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("card_bookmark_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.BookmarkAdd,
                                    contentDescription = "Save phrase",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
