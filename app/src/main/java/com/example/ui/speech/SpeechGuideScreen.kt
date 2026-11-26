package com.example.ui.speech

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audio.WavAudioRecorder
import com.example.data.ai.FirebaseAiRepository
import com.example.data.ai.VoiceEvaluation
import com.example.data.content.CuratedContent
import com.example.data.content.PhonemeGuideItem
import com.example.data.model.TargetLanguage
import com.example.ui.components.StudioIcons
import com.example.ui.components.LanguageSelectorRow
import com.example.ui.components.StudioBadge
import com.example.ui.components.TactileButton
import com.example.ui.components.TactileCard
import com.example.ui.theme.GoldStreak
import com.example.ui.theme.LocalStudioPalette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal fun PhonemeGuideItem.speechExampleText(): String =
    exampleWord.replace(" / ", ", ")

@Composable
fun SpeechGuideScreen(
    selectedLanguage: TargetLanguage,
    onSelectLanguage: (TargetLanguage) -> Unit,
    aiRepository: FirebaseAiRepository,
    speakingKey: String?,
    ttsError: String?,
    onSpeak: (key: String, text: String, language: TargetLanguage, slow: Boolean) -> Unit,
    onSavePhrase: suspend (TargetLanguage, String, String, String, String) -> Result<String>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalStudioPalette.current
    val scope = rememberCoroutineScope()
    val recorder = remember { WavAudioRecorder() }

    val isRecording by recorder.isRecording.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        onDispose { recorder.cancel() }
    }

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    var activeCheckItem by remember { mutableStateOf<PhonemeGuideItem?>(null) }
    var activeEval by remember { mutableStateOf<Pair<String, VoiceEvaluation>?>(null) }
    var evaluatingId by remember { mutableStateOf<String?>(null) }
    var checkError by remember { mutableStateOf<String?>(null) }
    var savedIds by remember { mutableStateOf(setOf<String>()) }

    fun stopAndGrade(item: PhonemeGuideItem) {
        if (evaluatingId != null) return
        scope.launch {
            evaluatingId = item.id
            try {
                val wav = recorder.stopAndGetWav()
                if (wav.size <= 44) {
                    checkError = "No speech detected—hold slightly longer while speaking."
                } else {
                    val res = aiRepository.evaluateSpokenAudio(
                        wavBytes = wav,
                        expectedPhrase = item.exampleWord,
                        language = selectedLanguage
                    )
                    activeEval = item.id to res
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                checkError = e.localizedMessage?.takeIf { it.isNotBlank() } ?: "Pronunciation check failed."
            } finally {
                evaluatingId = null
                activeCheckItem = null
            }
        }
    }

    fun startRecordingFor(item: PhonemeGuideItem) {
        if (isRecording) recorder.cancel()
        activeCheckItem = item
        val started = recorder.start(
            scope = scope,
            onAutoStop = { stopAndGrade(item) },
            onError = { msg ->
                checkError = msg
                activeCheckItem = null
            }
        )
        if (!started) {
            checkError = "Microphone unavailable right now. Close other apps using the mic and try again."
            activeCheckItem = null
        }
    }

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        val target = activeCheckItem
        if (granted && target != null) {
            startRecordingFor(target)
        } else if (!granted) {
            activeCheckItem = null
            checkError = "Microphone access is required to check your pronunciation."
        }
    }

    val phonemes = remember(selectedLanguage) {
        CuratedContent.phonemesFor(selectedLanguage)
    }

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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = palette.languageTint,
                        border = BorderStroke(1.5.dp, palette.inkBorder),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = StudioIcons.RecordVoiceOver,
                                contentDescription = null,
                                tint = palette.languageAccent
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Speech & Sound Guide",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "Tap any sound for native Gemini TTS or 0.75x slow breakdown",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                LanguageSelectorRow(
                    selectedLanguage = selectedLanguage,
                    onSelectLanguage = {
                        recorder.cancel()
                        activeCheckItem = null
                        activeEval = null
                        checkError = null
                        onSelectLanguage(it)
                    }
                )
            }

            if (ttsError != null || checkError != null) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = palette.dangerSoft,
                        border = BorderStroke(1.dp, palette.danger),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = checkError ?: ttsError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.danger,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }

            items(phonemes, key = { it.id }) { item ->
                val isThisRecording = isRecording && activeCheckItem?.id == item.id
                val isThisEvaluating = evaluatingId == item.id
                val evalForThis = activeEval?.takeIf { it.first == item.id }?.second
                val isSaved = item.id in savedIds

                TactileCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("phoneme_card_${item.id}")
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = palette.languageTint,
                                border = BorderStroke(1.5.dp, palette.inkBorder)
                            ) {
                                Text(
                                    text = item.symbol,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.languageAccent,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.phoneticCue,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Example: ${item.exampleWord} (${item.examplePhonetic}) — ${item.exampleMeaning}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = {
                                    checkError = null
                                    scope.launch {
                                        val res = onSavePhrase(
                                            selectedLanguage,
                                            item.exampleWord,
                                            item.examplePhonetic,
                                            item.exampleMeaning,
                                            "speech"
                                        )
                                        res.onSuccess {
                                            savedIds = savedIds + item.id
                                        }.onFailure { err ->
                                            checkError = err.localizedMessage?.takeIf { it.isNotBlank() }
                                                ?: "Couldn't save phrase to your phrasebook."
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("phoneme_bookmark_${item.id}")
                            ) {
                                Icon(
                                    imageVector = if (isSaved) Icons.Rounded.Check else StudioIcons.BookmarkAdd,
                                    contentDescription = "Save example word",
                                    tint = if (isSaved) palette.success else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = palette.subtleSurface,
                            border = BorderStroke(1.dp, palette.inkBorder.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = item.articulationTip,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }

                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TactileButton(
                                onClick = {
                                    onSpeak("sg_norm_${item.id}", item.speechExampleText(), selectedLanguage, false)
                                },
                                containerColor = palette.languageTint,
                                contentColor = palette.languageAccent,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("phoneme_play_${item.id}")
                            ) {
                                Icon(
                                    imageVector = StudioIcons.VolumeUp,
                                    contentDescription = null,
                                    tint = if (speakingKey == "sg_norm_${item.id}") GoldStreak else palette.languageAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Normal", style = MaterialTheme.typography.labelMedium)
                            }

                            TactileButton(
                                onClick = {
                                    onSpeak("sg_slow_${item.id}", item.speechExampleText(), selectedLanguage, true)
                                },
                                containerColor = palette.subtleSurface,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("phoneme_slow_${item.id}")
                            ) {
                                Icon(
                                    imageVector = StudioIcons.SlowMotionVideo,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Slow", style = MaterialTheme.typography.labelMedium)
                            }

                            TactileButton(
                                onClick = {
                                    checkError = null
                                    if (!hasMicPermission) {
                                        activeCheckItem = item
                                        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    } else if (isThisRecording) {
                                        stopAndGrade(item)
                                    } else {
                                        startRecordingFor(item)
                                    }
                                },
                                enabled = evaluatingId == null && (!isRecording || isThisRecording),
                                containerColor = if (isThisRecording) palette.danger else palette.aiAccent,
                                contentColor = if (isThisRecording) palette.onDanger else palette.onAiAccent,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("phoneme_mic_${item.id}")
                            ) {
                                if (isThisEvaluating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(15.dp),
                                        color = if (isThisRecording) palette.onDanger else palette.onAiAccent,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (isThisRecording) StudioIcons.Stop else StudioIcons.Mic,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = if (isThisRecording) "Grade" else "Try",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }

                        if (evalForThis != null) {
                            Spacer(Modifier.height(10.dp))
                            val good = evalForThis.accuracyScore >= 70
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (good) palette.successSoft else palette.languageTint,
                                border = BorderStroke(1.dp, if (good) palette.success else palette.languageAccent),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = StudioIcons.GraphicEq,
                                                contentDescription = null,
                                                tint = if (good) palette.success else palette.languageAccent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = "Heard: \"${evalForThis.transcript}\"",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        StudioBadge(
                                            text = "${evalForThis.accuracyScore}%",
                                            containerColor = palette.cardSurface,
                                            contentColor = if (good) palette.success else palette.languageAccent
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "${evalForThis.feedback} Tip: ${evalForThis.phoneticTip}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
