package com.example.ui.progress

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.GameSessionRecord
import com.example.data.model.GameType
import com.example.data.model.LanguageMastery
import com.example.data.model.SavedPhrase
import com.example.data.model.TargetLanguage
import com.example.data.model.UserProfile
import com.example.ui.components.LanguageSelectorRow
import com.example.ui.components.StudioBadge
import com.example.ui.components.TactileButton
import com.example.ui.components.TactileCard
import com.example.ui.studio.StudioUiState
import com.example.ui.theme.GoldStreak
import com.example.ui.theme.LocalStudioPalette

internal fun formatCountLabel(count: Long, singular: String, plural: String): String =
    "$count ${if (count == 1L) singular else plural}"

@Composable
fun ProgressScreen(
    profileState: StudioUiState<UserProfile>,
    masteryState: StudioUiState<Map<TargetLanguage, LanguageMastery>>,
    sessionsState: StudioUiState<List<GameSessionRecord>>,
    savedPhrasesState: StudioUiState<List<SavedPhrase>>,
    selectedLanguage: TargetLanguage,
    onSelectLanguage: (TargetLanguage) -> Unit,
    speakingKey: String?,
    onSpeak: (key: String, text: String, language: TargetLanguage, slow: Boolean) -> Unit,
    onDeletePhrase: (String) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalStudioPalette.current
    val profile = (profileState as? StudioUiState.Success)?.data
    val mastery = (masteryState as? StudioUiState.Success)?.data?.get(selectedLanguage)
    val sessions = (sessionsState as? StudioUiState.Success)?.data.orEmpty()
    val phrases = (savedPhrasesState as? StudioUiState.Success)?.data.orEmpty()
        .filter { it.languageCode == selectedLanguage.code }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Progress & Phrasebook",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = profile?.displayName ?: "Cloud Synced Profile",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TactileButton(
                        onClick = onSignOut,
                        containerColor = palette.cardSurface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Sign Out", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TactileCard(modifier = Modifier.weight(1f)) {
                        Column(horizontalAlignment = Alignment.Start) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Bolt,
                                    contentDescription = null,
                                    tint = palette.languageAccent
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Total XP", style = MaterialTheme.typography.labelMedium)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "${profile?.totalXp ?: 0}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    TactileCard(modifier = Modifier.weight(1f)) {
                        Column(horizontalAlignment = Alignment.Start) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.LocalFireDepartment,
                                    contentDescription = null,
                                    tint = GoldStreak
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Day Streak", style = MaterialTheme.typography.labelMedium)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = formatCountLabel(profile?.currentStreak ?: 0L, "day", "days"),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            item {
                LanguageSelectorRow(
                    selectedLanguage = selectedLanguage,
                    onSelectLanguage = onSelectLanguage
                )
            }

            item {
                TactileCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${selectedLanguage.displayName} 5-Skill Mastery",
                                style = MaterialTheme.typography.titleMedium
                            )
                            StudioBadge(
                                text = formatCountLabel(mastery?.gamesPlayed ?: 0L, "game", "games"),
                                containerColor = palette.languageTint,
                                contentColor = palette.languageAccent
                            )
                        }

                        if (masteryState is StudioUiState.Loading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            GameType.entries.forEach { game ->
                                val score = mastery?.scoreFor(game) ?: 0
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = game.linguisticSkill,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                        Text(
                                            text = "$score%",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { (score / 100f).coerceIn(0.03f, 1f) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(50)),
                                        color = palette.languageAccent,
                                        trackColor = palette.subtleSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Bookmark,
                        contentDescription = null,
                        tint = palette.languageAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${selectedLanguage.displayName} Phrasebook (${phrases.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            if (phrases.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = palette.subtleSurface,
                        border = BorderStroke(1.dp, palette.inkBorder.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Bookmark phrases inside Card Match, Voice Arena, Story Café, or Speech Guide to review them here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            } else {
                items(phrases, key = { it.id }) { phrase ->
                    TactileCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = phrase.targetText,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                if (phrase.phonetic.isNotBlank()) {
                                    Text(
                                        text = phrase.phonetic,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = palette.languageAccent
                                    )
                                }
                                Text(
                                    text = phrase.englishMeaning,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = {
                                    onSpeak("pb_${phrase.id}", phrase.targetText, selectedLanguage, false)
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("phrasebook_speak_${phrase.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.VolumeUp,
                                    contentDescription = "Speak saved phrase",
                                    tint = if (speakingKey == "pb_${phrase.id}") GoldStreak else MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = { onDeletePhrase(phrase.id) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("phrasebook_delete_${phrase.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Delete saved phrase",
                                    tint = palette.danger
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Recent Sessions",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            if (sessions.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = palette.subtleSurface,
                        border = BorderStroke(1.dp, palette.inkBorder.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Play any of the 5 games to record your cloud XP history.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            } else {
                items(sessions, key = { it.id }) { rec ->
                    val lang = TargetLanguage.fromCode(rec.languageCode)
                    TactileCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = rec.summary.ifBlank { GameType.fromCode(rec.gameType).title },
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "${lang.displayName} • ${rec.difficulty.replaceFirstChar { it.uppercase() }} • ${rec.scorePercent}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StudioBadge(
                                text = "+${rec.xpEarned} XP",
                                containerColor = palette.successSoft,
                                contentColor = palette.success
                            )
                        }
                    }
                }
            }
        }
    }
}
