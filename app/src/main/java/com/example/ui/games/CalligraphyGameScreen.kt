package com.example.ui.games

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.ai.CalligraphyEvaluation
import com.example.data.ai.CalligraphyRequest
import com.example.data.content.CalligraphyPromptItem
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
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Width : height of the drawing pad. */
internal const val PAD_ASPECT = 1.15f

/** Stroke width as a fraction of the pad width (same look at any pad size). */
private const val STROKE_WIDTH_FRACTION = 0.018f

/** One finger stroke. Points are normalized: x and y run from 0 to 1 across the pad. */
internal data class InkStroke(val points: List<Offset>)

private enum class SaveState { Idle, Saving, Saved, Failed }

private val StrokesSaver = listSaver<SnapshotStateList<InkStroke>, FloatArray>(
    save = { strokes ->
        strokes.map { stroke ->
            FloatArray(stroke.points.size * 2).also { arr ->
                stroke.points.forEachIndexed { i, p ->
                    arr[2 * i] = p.x
                    arr[2 * i + 1] = p.y
                }
            }
        }
    },
    restore = { saved ->
        mutableStateListOf<InkStroke>().apply {
            saved.forEach { arr -> add(InkStroke((arr.indices step 2).map { Offset(arr[it], arr[it + 1]) })) }
        }
    }
)

private val EvaluationSaver = listSaver<CalligraphyEvaluation?, Any>(
    save = { e -> if (e == null) emptyList() else listOf(e.score, e.isPass, e.strokeFeedback, e.improvementTip) },
    restore = { l ->
        if (l.isEmpty()) null
        else CalligraphyEvaluation(l[0] as Int, l[1] as Boolean, l[2] as String, l[3] as String)
    }
)

/** Stroke count stated in a hint like "3 strokes: ...", or null if the hint has none. */
internal fun CalligraphyPromptItem.expectedStrokeCount(): Int? =
    Regex("""^(\d+) strokes?""").find(strokeHint)?.groupValues?.get(1)?.toIntOrNull()

/** Reliable, local stroke-count feedback (the AI is not asked about stroke order). */
internal fun strokeCountNote(item: CalligraphyPromptItem, drawn: Int): String? {
    val expected = item.expectedStrokeCount() ?: return null
    return if (drawn == expected) "Strokes: you drew $drawn of $expected ✓"
    else "You drew $drawn stroke${if (drawn == 1) "" else "s"}; '${item.character}' has $expected."
}

/**
 * What the sound button says. Single symbols ("ñ", "¿?", "ç") are read by their name instead,
 * which the TTS can pronounce. Japanese characters and whole words are read as written.
 */
internal fun CalligraphyPromptItem.speechText(language: TargetLanguage): String =
    if (language != TargetLanguage.JAPANESE && character.count { it.isLetter() } < 2) phonetic else character

@Composable
fun CalligraphyGameScreen(
    language: TargetLanguage,
    difficulty: DifficultyLevel,
    onEvaluate: suspend (CalligraphyRequest) -> CalligraphyEvaluation,
    speakingKey: String?,
    onSpeak: (key: String, text: String, language: TargetLanguage, slow: Boolean) -> Unit,
    onCompleteSession: suspend (GameType, TargetLanguage, DifficultyLevel, Int, Int, Int, String) -> Unit,
    onBack: () -> Unit
) {
    val palette = LocalStudioPalette.current
    val scope = rememberCoroutineScope()
    val prompts = remember(language, difficulty) { CuratedContent.calligraphyItems(language, difficulty) }

    // Game state survives rotation (rememberSaveable). In-flight work (grading, saving) does not.
    var index by rememberSaveable(language, difficulty) { mutableStateOf(0) }
    var showGuide by rememberSaveable { mutableStateOf(true) }
    val strokes = rememberSaveable(language, difficulty, saver = StrokesSaver) { mutableStateListOf() }
    val activePoints = remember { mutableStateListOf<Offset>() }
    /** Best score per character; -1 = not graded (or skipped). */
    var bestScores by rememberSaveable(language, difficulty) { mutableStateOf(IntArray(prompts.size) { -1 }) }
    var evaluation by rememberSaveable(language, difficulty, stateSaver = EvaluationSaver) { mutableStateOf(null) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var startTimeMs by rememberSaveable(language, difficulty) { mutableStateOf(System.currentTimeMillis()) }
    var completedResult by rememberSaveable(language, difficulty) { mutableStateOf<Pair<Int, Int>?>(null) }
    var saveStateName by rememberSaveable(language, difficulty) { mutableStateOf(SaveState.Idle.name) }
    var isEvaluating by remember { mutableStateOf(false) }
    var isSavingNow by remember { mutableStateOf(false) }
    /** Strokes in the drawing that produced [evaluation]. */
    var gradedStrokeCount by rememberSaveable(language, difficulty) { mutableStateOf(0) }

    // A save that was running when the screen was recreated did not finish: offer a retry.
    val saveState = SaveState.valueOf(saveStateName).let { if (it == SaveState.Saving && !isSavingNow) SaveState.Failed else it }

    val currentItem = prompts[index.coerceIn(0, prompts.lastIndex)]
    val gradedCount = bestScores.count { it >= 0 }

    fun clearCanvas() {
        strokes.clear()
        activePoints.clear()
        evaluation = null
        errorMessage = null
    }

    fun saveResult() {
        val (avg, xp) = completedResult ?: return
        saveStateName = SaveState.Saving.name
        isSavingNow = true
        val duration = ((System.currentTimeMillis() - startTimeMs) / 1000L).toInt().coerceAtLeast(5)
        scope.launch {
            try {
                onCompleteSession(
                    GameType.CALLIGRAPHY, language, difficulty, avg, xp, duration,
                    "${language.displayName} Calligraphy ($avg% avg, $gradedCount/${prompts.size} graded)"
                )
                saveStateName = SaveState.Saved.name
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                saveStateName = SaveState.Failed.name
            } finally {
                isSavingNow = false
            }
        }
    }

    fun finishRound() {
        val graded = bestScores.filter { it >= 0 }
        if (graded.isEmpty()) {
            // Everything was skipped: nothing to score or save.
            completedResult = 0 to 0
            saveStateName = SaveState.Saved.name
            return
        }
        val avg = graded.average().roundToInt().coerceIn(0, 100)
        val xp = (40 + avg / 3) * graded.size / prompts.size
        completedResult = avg to xp
        saveResult()
    }

    fun goToNext() {
        if (index < prompts.lastIndex) {
            index++
            clearCanvas()
        } else {
            finishRound()
        }
    }

    fun checkDrawing(padAspect: Float) {
        if (strokes.isEmpty()) {
            errorMessage = "Draw '${currentItem.character}' on the pad first!"
            return
        }
        errorMessage = null
        isEvaluating = true
        val gradedIndex = index
        val snapshot = strokes.toList()
        scope.launch {
            try {
                val result = onEvaluate(
                    CalligraphyRequest(
                        bitmap = renderStrokesToBitmap(snapshot, padAspect),
                        targetChar = currentItem.character,
                        reading = currentItem.phonetic,
                        strokeHint = currentItem.strokeHint,
                        strokeDescription = describeStrokes(snapshot, padAspect),
                        language = language
                    )
                )
                evaluation = result
                gradedStrokeCount = snapshot.size
                bestScores = bestScores.copyOf().also { it[gradedIndex] = maxOf(it[gradedIndex], result.score) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = e.message ?: "Grading failed. Please try again, or tap Skip."
            } finally {
                isEvaluating = false
            }
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            GameTopBar(
                title = GameType.CALLIGRAPHY.title,
                language = language,
                skillLabel = "Writing",
                onBack = onBack,
                trailingContent = {
                    StudioBadge(
                        text = "${index + 1}/${prompts.size}",
                        containerColor = palette.subtleSurface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                }
            )

            val result = completedResult
            if (result != null) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 680.dp)
                        .align(Alignment.CenterHorizontally)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    GameCompletionCard(
                        gameType = GameType.CALLIGRAPHY,
                        scorePercent = result.first,
                        xpEarned = result.second,
                        summaryText = if (gradedCount == 0) {
                            "No characters were graded this round, so nothing was saved."
                        } else {
                            "Graded $gradedCount of ${prompts.size} ${language.displayName} scripts with AI stroke review."
                        },
                        isSaving = saveState == SaveState.Saving,
                        syncLabel = when {
                            gradedCount == 0 -> "Nothing to save"
                            saveState == SaveState.Failed -> "Not saved yet"
                            else -> null
                        },
                        onPlayAgain = {
                            bestScores = IntArray(prompts.size) { -1 }
                            index = 0
                            completedResult = null
                            saveStateName = SaveState.Idle.name
                            startTimeMs = System.currentTimeMillis()
                            clearCanvas()
                        },
                        onBackToStudio = onBack
                    )
                    if (saveState == SaveState.Failed) {
                        Spacer(Modifier.height(10.dp))
                        ErrorBanner("Couldn't save your result. Check your connection and try again.")
                        Spacer(Modifier.height(8.dp))
                        TactileButton(
                            onClick = { saveResult() },
                            containerColor = palette.inkBorder,
                            contentColor = Color.White,
                            modifier = Modifier.testTag("retry_save_button")
                        ) { Text("Retry save") }
                    }
                }
            } else {
                val pad: @Composable (Modifier) -> Unit = { mod ->
                    InkPad(
                        modifier = mod,
                        character = currentItem.character,
                        showGuide = showGuide,
                        strokes = strokes,
                        activePoints = activePoints,
                        enabled = !isEvaluating,
                        onStrokeFinished = { strokes.add(it) }
                    )
                }
                val prompt: @Composable () -> Unit = {
                    PromptCard(
                        item = currentItem,
                        isSpeaking = speakingKey == "calli_${currentItem.id}",
                        onSpeak = {
                            onSpeak("calli_${currentItem.id}", currentItem.speechText(language), language, true)
                        }
                    )
                }
                val panel: @Composable () -> Unit = {
                    PadControls(
                        showGuide = showGuide,
                        hasStrokes = strokes.isNotEmpty(),
                        hasEvaluation = evaluation != null,
                        isEvaluating = isEvaluating,
                        onToggleGuide = { showGuide = !showGuide },
                        onUndo = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) },
                        onClear = { clearCanvas() },
                        onCheck = { checkDrawing(PAD_ASPECT) }
                    )
                    errorMessage?.let { msg ->
                        Spacer(Modifier.height(10.dp))
                        ErrorBanner(msg)
                    }
                    val eval = evaluation
                    if (eval != null) {
                        Spacer(Modifier.height(10.dp))
                        EvaluationCard(
                            eval = eval,
                            strokeNote = strokeCountNote(currentItem, gradedStrokeCount),
                            isLast = index == prompts.lastIndex,
                            onNext = { goToNext() }
                        )
                    } else {
                        TextButton(
                            onClick = { goToNext() },
                            enabled = !isEvaluating,
                            modifier = Modifier
                                .align(Alignment.End)
                                .testTag("skip_character_button")
                        ) {
                            Text(if (index < prompts.lastIndex) "Skip this one" else "Skip and finish")
                        }
                    }
                }

                if (isLandscape) {
                    // Side by side: the pad uses the full height, controls stay visible on the right.
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        BoxWithConstraints(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            val padWidth = minOf(maxWidth, maxHeight * PAD_ASPECT)
                            pad(Modifier.width(padWidth).aspectRatio(PAD_ASPECT))
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                        ) {
                            prompt()
                            Spacer(Modifier.height(10.dp))
                            panel()
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 680.dp)
                            .align(Alignment.CenterHorizontally)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                    ) {
                        // Keep the pad from growing taller than about half the screen on tall/wide windows.
                        val maxPadWidth: Dp = (configuration.screenHeightDp * 0.55f * PAD_ASPECT).dp
                        prompt()
                        Spacer(Modifier.height(10.dp))
                        pad(
                            Modifier
                                .align(Alignment.CenterHorizontally)
                                .widthIn(max = maxPadWidth)
                                .fillMaxWidth()
                                .aspectRatio(PAD_ASPECT)
                        )
                        Spacer(Modifier.height(10.dp))
                        panel()
                        Spacer(Modifier.height(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PromptCard(item: CalligraphyPromptItem, isSpeaking: Boolean, onSpeak: () -> Unit) {
    val palette = LocalStudioPalette.current
    TactileCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = palette.languageTint,
                border = BorderStroke(1.5.dp, palette.languageAccent)
            ) {
                Text(
                    text = item.character,
                    style = MaterialTheme.typography.displaySmall,
                    color = palette.languageAccent,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${item.meaning} (${item.phonetic})",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = item.strokeHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onSpeak,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("calligraphy_speak_button")
            ) {
                Icon(
                    imageVector = StudioIcons.VolumeUp,
                    contentDescription = "Hear character",
                    tint = if (isSpeaking) palette.languageAccent else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun InkPad(
    modifier: Modifier,
    character: String,
    showGuide: Boolean,
    strokes: List<InkStroke>,
    activePoints: SnapshotStateList<Offset>,
    enabled: Boolean,
    onStrokeFinished: (InkStroke) -> Unit
) {
    val palette = LocalStudioPalette.current
    val canDraw by rememberUpdatedState(enabled)
    val finishStroke by rememberUpdatedState(onStrokeFinished)
    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFFFFFDF9))
            .border(2.dp, palette.inkBorder, RoundedCornerShape(22.dp))
            .semantics { contentDescription = "Drawing pad for $character" }
            .pointerInput(Unit) {
                // Raw pointer handling so a tap becomes a dot and strokes start exactly where the finger lands.
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (!canDraw) return@awaitEachGesture
                    down.consume()
                    fun norm(p: Offset) = Offset(
                        (p.x / size.width).coerceIn(0f, 1f),
                        (p.y / size.height).coerceIn(0f, 1f)
                    )
                    activePoints.clear()
                    activePoints.add(norm(down.position))
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        if (change.positionChange() != Offset.Zero) {
                            activePoints.add(norm(change.position))
                            change.consume()
                        }
                    }
                    if (activePoints.isNotEmpty()) finishStroke(InkStroke(activePoints.toList()))
                    activePoints.clear()
                }
            }
            .testTag("calligraphy_canvas"),
        contentAlignment = Alignment.Center
    ) {
        if (showGuide) {
            // Size the faded guide to the pad so it fits in portrait and landscape alike.
            val density = LocalDensity.current
            val letters = character.length.coerceAtLeast(1)
            val byHeight = maxHeight * 0.62f
            val byWidth = maxWidth * 0.9f / (if (letters <= 2) 1f else letters * 0.6f)
            val guideSize = with(density) { minOf(byHeight, byWidth).toSp() }
            Text(
                text = character,
                fontSize = guideSize,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1E1B24).copy(alpha = 0.11f),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
            val guideCol = Color(0xFFD5CCBE)
            drawLine(guideCol, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), 2f, pathEffect = dash)
            drawLine(guideCol, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), 2f, pathEffect = dash)

            val inkCol = Color(0xFF1E1B24)
            val strokeWidth = size.width * STROKE_WIDTH_FRACTION
            fun px(p: Offset) = Offset(p.x * size.width, p.y * size.height)
            fun drawStrokePoints(pts: List<Offset>) {
                if (pts.isEmpty()) return
                if (pts.size == 1) {
                    drawCircle(inkCol, radius = strokeWidth * 0.75f, center = px(pts[0]))
                    return
                }
                val path = Path().apply {
                    val first = px(pts.first())
                    moveTo(first.x, first.y)
                    for (i in 1 until pts.size) {
                        val p = px(pts[i])
                        lineTo(p.x, p.y)
                    }
                }
                drawPath(path, inkCol, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            strokes.forEach { drawStrokePoints(it.points) }
            if (activePoints.isNotEmpty()) drawStrokePoints(activePoints)
        }
    }
}

@Composable
private fun PadControls(
    showGuide: Boolean,
    hasStrokes: Boolean,
    hasEvaluation: Boolean,
    isEvaluating: Boolean,
    onToggleGuide: () -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onCheck: () -> Unit
) {
    val palette = LocalStudioPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TactileButton(
            onClick = onToggleGuide,
            containerColor = palette.cardSurface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            modifier = Modifier.testTag("toggle_guide_button")
        ) {
            Icon(
                if (showGuide) StudioIcons.Visibility else StudioIcons.VisibilityOff,
                contentDescription = "Toggle guide",
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text("Guide", style = MaterialTheme.typography.labelLarge)
        }
        TactileButton(
            onClick = onUndo,
            enabled = hasStrokes && !isEvaluating,
            containerColor = palette.cardSurface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            modifier = Modifier.testTag("undo_stroke_button")
        ) {
            Icon(StudioIcons.Undo, contentDescription = "Undo", modifier = Modifier.size(18.dp))
        }
        TactileButton(
            onClick = onClear,
            enabled = (hasStrokes || hasEvaluation) && !isEvaluating,
            containerColor = palette.cardSurface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            modifier = Modifier.testTag("clear_canvas_button")
        ) {
            Icon(StudioIcons.DeleteSweep, contentDescription = "Clear", modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.weight(1f))
        TactileButton(
            onClick = onCheck,
            enabled = !isEvaluating,
            containerColor = palette.aiAccent,
            contentColor = palette.onAiAccent,
            modifier = Modifier.testTag("check_calligraphy_button")
        ) {
            if (isEvaluating) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = palette.onAiAccent, strokeWidth = 2.dp)
                Spacer(Modifier.width(6.dp))
                Text("Grading…")
            } else {
                Icon(StudioIcons.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Check")
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    val palette = LocalStudioPalette.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = palette.dangerSoft,
        border = BorderStroke(1.5.dp, palette.danger),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            color = palette.danger,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun EvaluationCard(eval: CalligraphyEvaluation, strokeNote: String?, isLast: Boolean, onNext: () -> Unit) {
    val palette = LocalStudioPalette.current
    TactileCard(
        containerColor = if (eval.isPass) palette.successSoft else palette.languageTint,
        borderColor = if (eval.isPass) palette.success else palette.languageAccent,
        contentPadding = PaddingValues(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (eval.isPass) "Great Form! ${eval.score}/100" else "Keep Practicing • ${eval.score}/100",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (eval.isPass) palette.success else palette.languageAccent,
                    modifier = Modifier.weight(1f)
                )
                TactileButton(
                    onClick = onNext,
                    containerColor = palette.inkBorder,
                    contentColor = Color.White,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("next_character_button")
                ) {
                    Text(if (isLast) "Finish" else "Next")
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
            if (strokeNote != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = strokeNote,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.testTag("stroke_count_note")
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(text = eval.strokeFeedback, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Tip: ${eval.improvementTip}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Where normalized pad points land inside a square image, scaled evenly and centered. */
internal data class PadTransform(val scale: Float, val offsetX: Float, val offsetY: Float, val padAspect: Float) {
    fun map(p: Offset): Offset = Offset(offsetX + p.x * padAspect * scale, offsetY + p.y * scale)
}

internal fun padTransform(padAspect: Float, outSize: Int): PadTransform {
    val scale = outSize / maxOf(padAspect, 1f)
    return PadTransform(
        scale = scale,
        offsetX = (outSize - padAspect * scale) / 2f,
        offsetY = (outSize - scale) / 2f,
        padAspect = padAspect
    )
}

internal fun renderStrokesToBitmap(strokes: List<InkStroke>, padAspect: Float, outSize: Int = 384): Bitmap {
    val bitmap = Bitmap.createBitmap(outSize, outSize, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)
    val t = padTransform(padAspect, outSize)
    val strokeWidth = padAspect * t.scale * STROKE_WIDTH_FRACTION
    val paint = Paint().apply {
        color = android.graphics.Color.BLACK
        isAntiAlias = true
        style = Paint.Style.STROKE
        this.strokeWidth = strokeWidth
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val dotPaint = Paint(paint).apply { style = Paint.Style.FILL }
    strokes.forEach { stroke ->
        val pts = stroke.points.map(t::map)
        when {
            pts.size == 1 -> canvas.drawCircle(pts[0].x, pts[0].y, strokeWidth * 0.75f, dotPaint)
            pts.size > 1 -> {
                val path = AndroidPath().apply {
                    moveTo(pts.first().x, pts.first().y)
                    for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                }
                canvas.drawPath(path, paint)
            }
        }
    }
    return bitmap
}

/**
 * Plain-text stroke list for the grader, in drawing order, e.g.
 * "1. line from (20,30) to (80,30), going right; 2. dot at (50,50)".
 * Coordinates are 0–100 with the pad's real proportions (x spans 0–100 of the width).
 */
internal fun describeStrokes(strokes: List<InkStroke>, padAspect: Float): String {
    if (strokes.isEmpty()) return "none"
    fun pct(v: Float) = (v * 100).roundToInt()
    return strokes.mapIndexed { i, stroke ->
        val pts = stroke.points
        val first = pts.first()
        val last = pts.last()
        // Compare directions in real pad units so a "horizontal" stroke is horizontal on screen.
        val dx = (last.x - first.x) * padAspect
        val dy = last.y - first.y
        val straight = hypot(dx, dy)
        val length = pts.zipWithNext { a, b -> hypot((b.x - a.x) * padAspect, b.y - a.y) }.sum()
        if (pts.size == 1 || length < 0.02f) {
            "${i + 1}. dot at (${pct(first.x)},${pct(first.y)})"
        } else {
            val direction = when {
                abs(dx) > 2 * abs(dy) -> if (dx > 0) "going right" else "going left"
                abs(dy) > 2 * abs(dx) -> if (dy > 0) "going down" else "going up"
                else -> (if (dy > 0) "down" else "up") + "-" + (if (dx > 0) "right" else "left")
            }
            val shape = if (straight < 0.05f) "loop" else if (length > straight * 1.3f) "curve" else "line"
            "${i + 1}. $shape from (${pct(first.x)},${pct(first.y)}) to (${pct(last.x)},${pct(last.y)}), $direction"
        }
    }.joinToString("; ")
}
