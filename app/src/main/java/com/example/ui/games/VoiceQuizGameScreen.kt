package com.example.ui.games

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material.icons.rounded.Stop
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audio.WavAudioRecorder
import com.example.data.ai.FirebaseAiRepository
import com.example.data.ai.VoiceEvaluation
import com.example.data.content.CuratedContent
import com.example.data.model.DifficultyLevel
import com.example.data.model.GameType
import com.example.data.model.TargetLanguage
import com.example.ui.components.StudioBadge
import com.example.ui.components.TactileButton
import com.example.ui.components.TactileCard
import com.example.ui.theme.GoldStreak
import com.example.ui.theme.LocalStudioPalette
import kotlinx.coroutines.launch

@Composable
fun VoiceQuizGameScreen(
    language: TargetLanguage,
    difficulty: DifficultyLevel,
    aiRepository: FirebaseAiRepository,
    speakingKey: String?,
    onSpeak: (key: String, text: String, language: TargetLanguage, slow: Boolean) -> Unit,
    onSavePhrase: (TargetLanguage, String, String, String, String) -> Unit,
    onCompleteSession: suspend (GameType, TargetLanguage, DifficultyLevel, Int, Int, Int, String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val palette = LocalStudioPalette.current
    val scope = rememberCoroutineScope()
    val recorder = remember { WavAudioRecorder() }

    val isRecording by recorder.isRecording.collectAsStateWithLifecycle()
    val liveAmplitude by recorder.amplitude.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        onDispose { recorder.cancel() }
    }

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDeniedNote by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        permissionDeniedNote = !granted
        if (granted) {
            recorder.start(scope)
        }
    }

    var roundKey by remember(language, difficulty) { mutableIntStateOf(0) }
    val challenges = remember(language, difficulty, roundKey) {
        CuratedContent.voiceChallenges(language, difficulty)
    }

    var currentIndex by remember(roundKey, language, difficulty) { mutableIntStateOf(0) }
    val scores = remember(roundKey, language, difficulty) { mutableStateListOf<Int>() }
    var evaluation by remember(roundKey, language, difficulty, currentIndex) {
        mutableStateOf<VoiceEvaluation?>(null)
    }
    var isEvaluating by remember(roundKey, language, difficulty) { mutableStateOf(false) }
    var errorText by remember(roundKey, language, difficulty) { mutableStateOf<String?>(null) }
    var savedThisPrompt by remember(roundKey, language, difficulty, currentIndex) { mutableStateOf(false) }
    var startTimeMs by remember(roundKey, language, difficulty) { mutableLongStateOf(System.currentTimeMillis()) }
    var isSavingResult by remember { mutableStateOf(false) }
    var completedResult by remember(roundKey, language, difficulty) { mutableStateOf<Pair<Int, Int>?>(null) }

    val smoothAmp by animateFloatAsState(targetValue = liveAmplitude, label = "voiceAmp")
    val currentChallenge = challenges.getOrNull(currentIndex)

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
                title = GameType.VOICE.title,
                language = language,
                skillLabel = "Listening & Phonetics",
                onBack = onBack,
                trailingContent = {
                    if (challenges.isNotEmpty() && completedResult == null) {
                        StudioBadge(
                            text = "${currentIndex + 1}/${challenges.size}",
                            containerColor = palette.subtleSurface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )

            if (challenges.isNotEmpty() && completedResult == null) {
                LinearProgressIndicator(
                    progress = { (currentIndex + 1).toFloat() / challenges.size.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .height(8.dp)
                        .clip(RoundedCornerShape(50)),
                    color = palette.languageAccent,
                    trackColor = palette.subtleSurface
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (completedResult != null) {
                    val (avgScore, xp) = completedResult!!
                    GameCompletionCard(
                        gameType = GameType.VOICE,
                        scorePercent = avgScore,
                        xpEarned = xp,
                        summaryText = "Spoke ${challenges.size} ${language.displayName} phrases with ${avgScore}% average pronunciation clarity.",
                        isSaving = isSavingResult,
                        onPlayAgain = { roundKey++ },
                        onBackToStudio = onBack
                    )
                } else if (currentChallenge != null) {
                    // Challenge Prompt Card
                    TactileCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = palette.cardSurface
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                StudioBadge(
                                    text = currentChallenge.modeHint,
                                    containerColor = palette.languageTint,
                                    contentColor = palette.languageAccent
                                )
                                IconButton(
                                    onClick = {
                                        onSavePhrase(
                                            language,
                                            currentChallenge.targetPhrase,
                                            currentChallenge.phonetic,
                                            currentChallenge.englishMeaning,
                                            GameType.VOICE.code
                                        )
                                        savedThisPrompt = true
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("voice_bookmark_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.BookmarkAdd,
                                        contentDescription = "Save phrase to phrasebook",
                                        tint = if (savedThisPrompt) palette.success else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = currentChallenge.targetPhrase,
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = currentChallenge.phonetic,
                                style = MaterialTheme.typography.labelLarge,
                                color = palette.languageAccent
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Meaning: ${currentChallenge.englishMeaning}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (savedThisPrompt) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Saved to your phrasebook!",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.success
                                )
                            }

                            Spacer(Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                TactileButton(
                                    onClick = {
                                        onSpeak(
                                            "voice_norm_${currentChallenge.id}",
                                            currentChallenge.targetPhrase,
                                            language,
                                            false
                                        )
                                    },
                                    containerColor = palette.languageTint,
                                    contentColor = palette.languageAccent,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("voice_listen_normal_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.VolumeUp,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (speakingKey == "voice_norm_${currentChallenge.id}") "Speaking…" else "Listen",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                                TactileButton(
                                    onClick = {
                                        onSpeak(
                                            "voice_slow_${currentChallenge.id}",
                                            currentChallenge.targetPhrase,
                                            language,
                                            true
                                        )
                                    },
                                    containerColor = palette.subtleSurface,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("voice_listen_slow_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SlowMotionVideo,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (speakingKey == "voice_slow_${currentChallenge.id}") "Slow…" else "0.75x Slow",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }

                    // Microphone & Waveform Card
                    TactileCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = palette.cardSurface
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = when {
                                    isEvaluating -> "Gemini is analyzing your pronunciation…"
                                    isRecording -> "Listening… tap Stop when finished"
                                    else -> "Tap the mic and say the phrase aloud"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(12.dp))

                            // Live Waveform Canvas
                            val barColor = if (isRecording) palette.languageAccent else palette.inkBorder.copy(alpha = 0.25f)
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(palette.subtleSurface)
                            ) {
                                val barCount = 24
                                val spacing = size.width / (barCount + 1)
                                val barW = (spacing * 0.45f).coerceAtLeast(4f)
                                for (i in 0 until barCount) {
                                    val centerFactor = 1f - kotlin.math.abs(i - barCount / 2f) / (barCount / 2f)
                                    val wave = if (isRecording) {
                                        (0.18f + smoothAmp * 0.82f * (0.4f + 0.6f * centerFactor) *
                                            (0.65f + 0.35f * kotlin.math.sin(i * 1.1f + smoothAmp * 12f)))
                                            .coerceIn(0.12f, 1f)
                                    } else {
                                        0.16f
                                    }
                                    val h = size.height * wave * 0.8f
                                    val x = spacing * (i + 1) - barW / 2f
                                    val y = (size.height - h) / 2f
                                    drawRoundRect(
                                        color = barColor,
                                        topLeft = Offset(x, y),
                                        size = Size(barW, h),
                                        cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                                    )
                                }
                            }

                            Spacer(Modifier.height(14.dp))

                            if (permissionDeniedNote) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = palette.dangerSoft,
                                    border = BorderStroke(1.dp, palette.danger),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 10.dp)
                                ) {
                                    Text(
                                        text = "Microphone permission is needed for Voice Arena. Tap the mic button to grant access.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = palette.danger,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }

                            TactileButton(
                                onClick = {
                                    errorText = null
                                    if (!hasMicPermission) {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    } else if (isRecording) {
                                        scope.launch {
                                            isEvaluating = true
                                            try {
                                                val wavBytes = recorder.stopAndGetWav()
                                                if (wavBytes.size <= 44) {
                                                    errorText = "No speech captured. Hold for a second while speaking and try again."
                                                } else {
                                                    val res = aiRepository.evaluateSpokenAudio(
                                                        wavBytes = wavBytes,
                                                        expectedPhrase = currentChallenge.targetPhrase,
                                                        language = language
                                                    )
                                                    evaluation = res
                                                    if (scores.size == currentIndex) {
                                                        scores.add(res.accuracyScore)
                                                    } else if (currentIndex < scores.size) {
                                                        scores[currentIndex] = maxOf(scores[currentIndex], res.accuracyScore)
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                errorText = e.localizedMessage ?: "Could not grade voice recording."
                                            } finally {
                                                isEvaluating = false
                                            }
                                        }
                                    } else {
                                        recorder.start(scope)
                                    }
                                },
                                enabled = !isEvaluating,
                                containerColor = if (isRecording) palette.danger else MaterialTheme.colorScheme.primary,
                                contentColor = palette.onLanguageAccent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("voice_record_toggle_button")
                            ) {
                                if (isEvaluating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = palette.onLanguageAccent,
                                        strokeWidth = 2.5.dp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Analyzing Speech…")
                                } else {
                                    Surface(
                                        shape = CircleShape,
                                        color = palette.onLanguageAccent.copy(alpha = 0.2f),
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (isRecording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                                                contentDescription = if (isRecording) "Stop recording" else "Start recording",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = if (isRecording) "Stop & Grade" else "Record My Voice",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                }
                            }
                        }
                    }

                    if (errorText != null) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = palette.dangerSoft,
                            border = BorderStroke(1.5.dp, palette.danger),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = errorText ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.danger,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    evaluation?.let { eval ->
                        val good = eval.accuracyScore >= 70
                        TactileCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = if (good) palette.successSoft else palette.languageTint
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Rounded.GraphicEq,
                                            contentDescription = null,
                                            tint = if (good) palette.success else palette.languageAccent
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "Clarity Score",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                    StudioBadge(
                                        text = "${eval.accuracyScore}%",
                                        containerColor = palette.cardSurface,
                                        contentColor = if (good) palette.success else palette.languageAccent
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Heard: \"${eval.transcript}\"",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = eval.feedback,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(Modifier.height(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = palette.cardSurface,
                                    border = BorderStroke(1.dp, palette.inkBorder.copy(alpha = 0.25f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Phonetic Tip: ${eval.phoneticTip}",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Next / Skip or Finish Round
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TactileButton(
                            onClick = {
                                if (scores.size <= currentIndex) {
                                    scores.add(60)
                                }
                                if (currentIndex + 1 < challenges.size) {
                                    currentIndex++
                                } else {
                                    val avg = if (scores.isEmpty()) 75 else scores.average().toInt().coerceIn(0, 100)
                                    val xp = 40 + (avg / 3)
                                    val duration = ((System.currentTimeMillis() - startTimeMs) / 1000L).toInt().coerceAtLeast(8)
                                    completedResult = avg to xp
                                    scope.launch {
                                        isSavingResult = true
                                        try {
                                            onCompleteSession(
                                                GameType.VOICE,
                                                language,
                                                difficulty,
                                                avg,
                                                xp,
                                                duration,
                                                "${language.displayName} Voice Arena ($avg% avg clarity)"
                                            )
                                        } finally {
                                            isSavingResult = false
                                        }
                                    }
                                }
                            },
                            enabled = !isRecording && !isEvaluating,
                            containerColor = if (evaluation != null) palette.success else palette.subtleSurface,
                            contentColor = if (evaluation != null) palette.onLanguageAccent else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("voice_next_button")
                        ) {
                            Text(
                                text = when {
                                    currentIndex + 1 < challenges.size && evaluation != null -> "Next Phrase"
                                    currentIndex + 1 < challenges.size -> "Skip Phrase"
                                    else -> "Finish Voice Round"
                                },
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
