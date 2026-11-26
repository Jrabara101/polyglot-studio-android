package com.example.ui.tutor

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ai.FirebaseAiRepository
import com.example.data.model.DifficultyLevel
import com.example.data.model.TargetLanguage
import com.example.ui.components.LanguageSelectorRow
import com.example.ui.components.StudioBadge
import com.example.ui.components.TactileButton
import com.example.ui.components.TactileCard
import com.example.ui.theme.GoldStreak
import com.example.ui.theme.LocalStudioPalette
import com.google.firebase.ai.type.PublicPreviewAPI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private data class TutorChatMessage(
    val isUser: Boolean,
    val text: String
)

@OptIn(PublicPreviewAPI::class, ExperimentalLayoutApi::class)
@Composable
fun TutorHubScreen(
    selectedLanguage: TargetLanguage,
    selectedDifficulty: DifficultyLevel,
    onSelectLanguage: (TargetLanguage) -> Unit,
    aiRepository: FirebaseAiRepository,
    speakingKey: String?,
    onSpeak: (key: String, text: String, language: TargetLanguage, slow: Boolean) -> Unit,
    liveSessionController: LiveSessionController = remember { LiveSessionController() },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalStudioPalette.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var chatSession by remember(selectedLanguage, selectedDifficulty) {
        mutableStateOf(aiRepository.startTutorChat(selectedLanguage, selectedDifficulty))
    }
    val messages = remember(selectedLanguage, selectedDifficulty) {
        mutableStateListOf(
            TutorChatMessage(
                isUser = false,
                text = "Hi! I'm Lumi, your ${selectedLanguage.displayName} (${selectedLanguage.nativeLabel}) studio coach. Ask me about grammar, slang, pitch, or start a live voice chat above!"
            )
        )
    }
    var inputText by remember { mutableStateOf("") }
    var isStreaming by remember { mutableStateOf(false) }
    var chatError by remember { mutableStateOf<String?>(null) }

    // Live Gemini Audio Session state owned by controller (survives composable disposal for safe cleanup)
    val isConnectingLive by liveSessionController.isConnecting.collectAsStateWithLifecycle()
    val isLiveActive by liveSessionController.isActive.collectAsStateWithLifecycle()
    val liveError by liveSessionController.error.collectAsStateWithLifecycle()

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    @SuppressLint("MissingPermission")
    fun startLiveSession() {
        liveSessionController.start {
            FirebaseLiveSessionAdapter(
                aiRepository.connectLiveTutorSession(selectedLanguage, selectedDifficulty)
            )
        }
    }

    fun stopLiveSession() {
        liveSessionController.stop()
    }

    DisposableEffect(selectedLanguage, selectedDifficulty) {
        onDispose {
            liveSessionController.stop()
        }
    }

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) {
            startLiveSession()
        } else {
            liveSessionController.setError("Microphone permission is required for real-time voice tutoring.")
        }
    }

    LaunchedEffect(messages.size, messages.lastOrNull()?.text) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    fun sendPrompt(rawText: String) {
        val clean = rawText.trim()
        if (clean.isEmpty() || isStreaming) return
        chatError = null
        inputText = ""
        messages.add(TutorChatMessage(isUser = true, text = clean))
        val assistantIndex = messages.size
        messages.add(TutorChatMessage(isUser = false, text = ""))
        isStreaming = true

        scope.launch {
            val sb = StringBuilder()
            try {
                aiRepository.sendTutorMessageStream(chatSession, clean).collect { chunk ->
                    sb.append(chunk)
                    if (assistantIndex < messages.size) {
                        messages[assistantIndex] = TutorChatMessage(isUser = false, text = sb.toString())
                    }
                }
                if (sb.isBlank() && assistantIndex < messages.size) {
                    messages[assistantIndex] = TutorChatMessage(
                        isUser = false,
                        text = "I didn't catch that—could you rephrase your question?"
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                chatError = e.localizedMessage ?: "Tutor response failed."
                if (assistantIndex < messages.size && messages[assistantIndex].text.isBlank()) {
                    messages.removeAt(assistantIndex)
                }
            } finally {
                isStreaming = false
            }
        }
    }

    val pulseTransition = rememberInfiniteTransition(label = "livePulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(750), RepeatMode.Reverse),
        label = "pulseScale"
    )

    val quickPrompts = remember(selectedLanguage) {
        when (selectedLanguage) {
            TargetLanguage.JAPANESE -> listOf(
                "Explain は vs が simply",
                "3 polite Tokyo café phrases",
                "Difference between ありがとう & すみません"
            )
            TargetLanguage.SPANISH -> listOf(
                "When to use ser vs estar?",
                "How do I roll the Spanish 'rr'?",
                "3 friendly tapas ordering phrases"
            )
            TargetLanguage.FRENCH -> listOf(
                "How do nasal vowels work?",
                "Explain tu vs vous politely",
                "3 natural bakery phrases in Paris"
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
        ) {
            // Header + Language Switcher + Live Voice Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = palette.aiAccent
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Lumi AI Tutor",
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Text(
                                text = "${selectedLanguage.displayName} • ${selectedDifficulty.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            chatSession = aiRepository.startTutorChat(selectedLanguage, selectedDifficulty)
                            messages.clear()
                            messages.add(
                                TutorChatMessage(
                                    isUser = false,
                                    text = "Fresh ${selectedLanguage.displayName} session ready! What would you like to practice?"
                                )
                            )
                            chatError = null
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("tutor_reset_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "New tutor chat",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                LanguageSelectorRow(
                    selectedLanguage = selectedLanguage,
                    onSelectLanguage = {
                        stopLiveSession()
                        onSelectLanguage(it)
                    }
                )

                // Live Gemini Voice Mode Banner
                TactileCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = if (isLiveActive) palette.aiSoft else palette.cardSurface,
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isLiveActive) palette.aiAccent else palette.aiSoft,
                                    border = BorderStroke(1.dp, palette.aiAccent),
                                    modifier = Modifier
                                        .size(38.dp)
                                        .scale(if (isLiveActive) pulseScale else 1f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (isLiveActive) Icons.Rounded.GraphicEq else Icons.Rounded.Mic,
                                            contentDescription = null,
                                            tint = if (isLiveActive) Color.White else palette.aiAccent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Live Voice Tutor",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        StudioBadge(
                                            text = if (isLiveActive) "LIVE" else "GEMINI LIVE",
                                            containerColor = if (isLiveActive) palette.successSoft else palette.aiSoft,
                                            contentColor = if (isLiveActive) palette.success else palette.aiAccent
                                        )
                                    }
                                    Text(
                                        text = if (isLiveActive) {
                                            "Speak freely in ${selectedLanguage.displayName} or English…"
                                        } else {
                                            "Real-time hands-free voice conversation"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(Modifier.width(8.dp))
                            TactileButton(
                                onClick = {
                                    if (isLiveActive) {
                                        stopLiveSession()
                                    } else if (!hasMicPermission) {
                                        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    } else {
                                        startLiveSession()
                                    }
                                },
                                enabled = !isConnectingLive,
                                containerColor = if (isLiveActive) palette.danger else palette.aiAccent,
                                contentColor = Color.White,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                modifier = Modifier.testTag("tutor_live_toggle_button")
                            ) {
                                if (isConnectingLive) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (isLiveActive) Icons.Rounded.CallEnd else Icons.Rounded.Mic,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = if (isLiveActive) "End" else "Talk",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }

                        if (liveError != null) {
                            Text(
                                text = liveError ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.danger
                            )
                        }
                    }
                }
            }

            // Streaming Chat Transcript
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        quickPrompts.forEachIndexed { idx, chip ->
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = palette.subtleSurface,
                                border = BorderStroke(1.dp, palette.inkBorder.copy(alpha = 0.45f)),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("tutor_quick_chip_$idx")
                                    .clip(RoundedCornerShape(50))
                                    .clickable(enabled = !isStreaming) {
                                        sendPrompt(chip)
                                    }
                            ) {
                                Text(
                                    text = chip,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }

                itemsIndexed(messages) { idx, msg ->
                    if (msg.isUser) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = palette.languageTint,
                                border = BorderStroke(1.5.dp, palette.languageAccent),
                                modifier = Modifier.fillMaxWidth(0.84f)
                            ) {
                                Text(
                                    text = msg.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    } else {
                        TactileCard(
                            modifier = Modifier.fillMaxWidth(0.92f),
                            containerColor = palette.cardSurface,
                            contentPadding = PaddingValues(12.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "LUMI COACH",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = palette.aiAccent
                                    )
                                    if (msg.text.isNotBlank()) {
                                        IconButton(
                                            onClick = {
                                                onSpeak("tutor_$idx", msg.text, selectedLanguage, false)
                                            },
                                            modifier = Modifier
                                                .size(40.dp)
                                                .testTag("tutor_speak_$idx")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.VolumeUp,
                                                contentDescription = "Listen to tutor reply",
                                                tint = if (speakingKey == "tutor_$idx") GoldStreak else palette.aiAccent,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = msg.text.ifBlank { "Thinking…" },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                if (chatError != null) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = palette.dangerSoft,
                            border = BorderStroke(1.dp, palette.danger),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = chatError ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.danger,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }

            // Input Bar
            Surface(
                color = palette.cardSurface,
                border = BorderStroke(1.5.dp, palette.inkBorder),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text(
                                text = "Ask about ${selectedLanguage.displayName}…",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = palette.aiAccent,
                            unfocusedBorderColor = palette.inkBorder.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tutor_chat_input")
                    )
                    IconButton(
                        onClick = { sendPrompt(inputText) },
                        enabled = inputText.isNotBlank() && !isStreaming,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("tutor_send_button")
                    ) {
                        if (isStreaming) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = "Send message",
                                tint = palette.aiAccent
                            )
                        }
                    }
                }
            }
        }
    }
}
