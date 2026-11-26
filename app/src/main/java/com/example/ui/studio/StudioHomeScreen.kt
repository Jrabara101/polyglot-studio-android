package com.example.ui.studio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.DifficultyLevel
import com.example.data.model.GameType
import com.example.data.model.LanguageMastery
import com.example.data.model.TargetLanguage
import com.example.data.model.UserProfile
import com.example.ui.components.StudioIcons
import com.example.ui.components.DifficultySelectorRow
import com.example.ui.components.LanguageSelectorRow
import com.example.ui.components.StudioBadge
import com.example.ui.components.TactileButton
import com.example.ui.components.TactileCard
import com.example.ui.theme.GoldStreak
import com.example.ui.theme.LocalStudioPalette

@Composable
fun StudioHomeScreen(
    profileState: StudioUiState<UserProfile>,
    masteryState: StudioUiState<Map<TargetLanguage, LanguageMastery>>,
    selectedLanguage: TargetLanguage,
    selectedDifficulty: DifficultyLevel,
    onSelectLanguage: (TargetLanguage) -> Unit,
    onSelectDifficulty: (DifficultyLevel) -> Unit,
    onLaunchGame: (GameType) -> Unit,
    onOpenSpeechGuide: () -> Unit,
    onOpenTutorHub: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalStudioPalette.current
    val profile = (profileState as? StudioUiState.Success)?.data
    val masteryMap = (masteryState as? StudioUiState.Success)?.data.orEmpty()
    val currentMastery = masteryMap[selectedLanguage]

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = if (profileState is StudioUiState.Loading) Alignment.Center else Alignment.TopCenter
    ) {
        if (profileState is StudioUiState.Loading) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                Text(
                    text = "Loading your studio…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Box
        }
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                // Header & Streak Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.5.dp, palette.inkBorder),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_studio_emblem),
                                contentDescription = "Polyglot Studio emblem",
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Polyglot Studio",
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Text(
                                text = "Hi ${profile?.displayName?.substringBefore(" ") ?: "Learner"} • English Base",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = palette.cardSurface,
                            border = BorderStroke(1.5.dp, palette.inkBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = StudioIcons.LocalFireDepartment,
                                    contentDescription = "Daily Streak",
                                    tint = GoldStreak,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "${profile?.currentStreak ?: 0}d",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = palette.cardSurface,
                            border = BorderStroke(1.5.dp, palette.inkBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = StudioIcons.Bolt,
                                    contentDescription = "Total XP",
                                    tint = palette.languageAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(2.dp))
                                Text(
                                    text = "${profile?.totalXp ?: 0} XP",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }

            if (profileState is StudioUiState.Error) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = palette.dangerSoft,
                        border = BorderStroke(1.dp, palette.danger),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = profileState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.danger,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Target Language",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                LanguageSelectorRow(
                    selectedLanguage = selectedLanguage,
                    onSelectLanguage = onSelectLanguage
                )
            }

            item {
                Text(
                    text = "Open Difficulty",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                DifficultySelectorRow(
                    selectedDifficulty = selectedDifficulty,
                    onSelectDifficulty = onSelectDifficulty
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "5 Skill Games",
                        style = MaterialTheme.typography.titleLarge
                    )
                    StudioBadge(
                        text = "${currentMastery?.languageXp ?: 0} ${selectedLanguage.shortBadge} XP",
                        containerColor = palette.languageTint,
                        contentColor = palette.languageAccent
                    )
                }
            }

            items(GameType.entries, key = { it.code }) { game ->
                val skillScore = currentMastery?.scoreFor(game) ?: 0
                TactileCard(
                    onClick = { onLaunchGame(game) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("game_card_${game.code}")
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = palette.languageTint,
                                border = BorderStroke(1.5.dp, palette.inkBorder),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = iconForGame(game),
                                        contentDescription = null,
                                        tint = palette.languageAccent,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = game.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = game.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = "Open ${game.title}",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StudioBadge(
                                text = game.linguisticSkill,
                                containerColor = palette.subtleSurface,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                            LinearProgressIndicator(
                                progress = { (skillScore / 100f).coerceIn(0.04f, 1f) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(50)),
                                color = palette.languageAccent,
                                trackColor = palette.subtleSurface
                            )
                            Text(
                                text = "$skillScore%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "AI Speech & Tutor Hub",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TactileButton(
                        onClick = onOpenSpeechGuide,
                        containerColor = palette.cardSurface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_open_speech_button")
                    ) {
                        Icon(
                            imageVector = StudioIcons.RecordVoiceOver,
                            contentDescription = null,
                            tint = palette.languageAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Speech Guide", style = MaterialTheme.typography.labelLarge)
                    }
                    TactileButton(
                        onClick = onOpenTutorHub,
                        containerColor = palette.aiAccent,
                        contentColor = palette.onAiAccent,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_open_tutor_button")
                    ) {
                        Icon(
                            imageVector = StudioIcons.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Live AI Tutor", style = MaterialTheme.typography.labelLarge)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

private fun iconForGame(gameType: GameType): ImageVector = when (gameType) {
    GameType.CARDS -> StudioIcons.Style
    GameType.CALLIGRAPHY -> StudioIcons.Brush
    GameType.QUIZ -> StudioIcons.Quiz
    GameType.VOICE -> StudioIcons.GraphicEq
    GameType.STORY -> StudioIcons.Forum
}
