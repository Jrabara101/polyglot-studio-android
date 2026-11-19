package com.example.ui.games

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ai.CalligraphyEvaluation
import com.example.data.ai.FirebaseAiRepository
import com.example.data.content.CuratedContent
import com.example.data.model.DifficultyLevel
import com.example.data.model.GameType
import com.example.data.model.TargetLanguage
import com.example.ui.components.StudioBadge
import com.example.ui.components.TactileButton
import com.example.ui.components.TactileCard
import com.example.ui.theme.LocalStudioPalette
import kotlinx.coroutines.launch

private data class InkStroke(val points: List<Offset>)

@Composable
fun CalligraphyGameScreen(
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
    val prompts = remember(language, difficulty) { CuratedContent.calligraphyItems(language, difficulty) }

    var index by remember(language, difficulty) { mutableIntStateOf(0) }
    var showGuide by remember { mutableStateOf(true) }
    val strokes = remember { mutableStateListOf<InkStroke>() }
    var activePoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }

    var isEvaluating by remember { mutableStateOf(false) }
    var evaluation by remember { mutableStateOf<CalligraphyEvaluation?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scores = remember(language, difficulty) { mutableStateListOf<Int>() }
    var startTimeMs by remember(language, difficulty) { mutableLongStateOf(System.currentTimeMillis()) }
    var completedResult by remember(language, difficulty) { mutableStateOf<Pair<Int, Int>?>(null) }
    var isSavingResult by remember { mutableStateOf(false) }

    val currentItem = prompts[index.coerceIn(0, prompts.lastIndex)]

    fun clearCanvas() {
        strokes.clear()
        activePoints = emptyList()
        evaluation = null
        errorMessage = null
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
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
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

            if (completedResult != null) {
                val (avgScore, xp) = completedResult!!
                Box(modifier = Modifier.padding(16.dp)) {
                    GameCompletionCard(
                        gameType = GameType.CALLIGRAPHY,
                        scorePercent = avgScore,
                        xpEarned = xp,
                        summaryText = "Practiced ${prompts.size} ${language.displayName} scripts with Gemini Vision stroke review.",
                        isSaving = isSavingResult,
                        onPlayAgain = {
                            scores.clear()
                            index = 0
                            completedResult = null
                            startTimeMs = System.currentTimeMillis()
                            clearCanvas()
                        },
                        onBackToStudio = onBack
                    )
                }
            } else {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    // Prompt Card
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
                                    text = currentItem.character,
                                    style = MaterialTheme.typography.displaySmall,
                                    color = palette.languageAccent,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${currentItem.meaning} (${currentItem.phonetic})",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = currentItem.strokeHint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = {
                                    onSpeak("calli_${currentItem.id}", currentItem.character, language, true)
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("calligraphy_speak_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.VolumeUp,
                                    contentDescription = "Hear character",
                                    tint = if (speakingKey == "calli_${currentItem.id}") palette.languageAccent else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Ink Drawing Pad
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.15f)
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color(0xFFFFFDF9))
                            .border(2.dp, palette.inkBorder, RoundedCornerShape(22.dp))
                            .onSizeChanged { canvasSize = it }
                            .pointerInput(currentItem.id) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        activePoints = listOf(offset)
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        activePoints = activePoints + change.position
                                    },
                                    onDragEnd = {
                                        if (activePoints.isNotEmpty()) {
                                            strokes.add(InkStroke(activePoints))
                                            activePoints = emptyList()
                                        }
                                    },
                                    onDragCancel = {
                                        activePoints = emptyList()
                                    }
                                )
                            }
                            .testTag("calligraphy_canvas"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (showGuide) {
                            Text(
                                text = currentItem.character,
                                fontSize = if (currentItem.character.length <= 2) 150.sp else 72.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF1E1B24).copy(alpha = 0.11f),
                                textAlign = TextAlign.Center
                            )
                        }

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                            val guideCol = Color(0xFFD5CCBE)
                            drawLine(
                                color = guideCol,
                                start = Offset(size.width / 2f, 0f),
                                end = Offset(size.width / 2f, size.height),
                                strokeWidth = 2f,
                                pathEffect = dash
                            )
                            drawLine(
                                color = guideCol,
                                start = Offset(0f, size.height / 2f),
                                end = Offset(size.width, size.height / 2f),
                                strokeWidth = 2f,
                                pathEffect = dash
                            )

                            val inkCol = Color(0xFF1E1B24)
                            fun drawStrokePoints(pts: List<Offset>) {
                                if (pts.size < 2) {
                                    pts.firstOrNull()?.let { p ->
                                        drawCircle(inkCol, radius = 9f, center = p)
                                    }
                                    return
                                }
                                val path = Path().apply {
                                    moveTo(pts.first().x, pts.first().y)
                                    for (i in 1 until pts.size) {
                                        lineTo(pts[i].x, pts[i].y)
                                    }
                                }
                                drawPath(
                                    path = path,
                                    color = inkCol,
                                    style = Stroke(
                                        width = 18f,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }

                            strokes.forEach { drawStrokePoints(it.points) }
                            if (activePoints.isNotEmpty()) drawStrokePoints(activePoints)
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Canvas Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TactileButton(
                            onClick = { showGuide = !showGuide },
                            containerColor = palette.cardSurface,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier.testTag("toggle_guide_button")
                        ) {
                            Icon(
                                if (showGuide) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                contentDescription = "Toggle guide",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Guide", style = MaterialTheme.typography.labelLarge)
                        }
                        TactileButton(
                            onClick = {
                                if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex)
                            },
                            enabled = strokes.isNotEmpty(),
                            containerColor = palette.cardSurface,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier.testTag("undo_stroke_button")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = "Undo", modifier = Modifier.size(18.dp))
                        }
                        TactileButton(
                            onClick = { clearCanvas() },
                            enabled = strokes.isNotEmpty() || evaluation != null,
                            containerColor = palette.cardSurface,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier.testTag("clear_canvas_button")
                        ) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        TactileButton(
                            onClick = {
                                if (strokes.isEmpty()) {
                                    errorMessage = "Draw '${currentItem.character}' on the pad first!"
                                    return@TactileButton
                                }
                                errorMessage = null
                                isEvaluating = true
                                scope.launch {
                                    try {
                                        val bmp = renderStrokesToBitmap(
                                            strokes = strokes.toList(),
                                            srcWidth = canvasSize.width.coerceAtLeast(1),
                                            srcHeight = canvasSize.height.coerceAtLeast(1)
                                        )
                                        val result = aiRepository.evaluateCalligraphy(
                                            bitmap = bmp,
                                            targetChar = currentItem.character,
                                            reading = currentItem.phonetic,
                                            language = language
                                        )
                                        evaluation = result
                                        scores.add(result.score)
                                    } catch (e: Exception) {
                                        errorMessage = e.localizedMessage ?: "Vision evaluation failed."
                                    } finally {
                                        isEvaluating = false
                                    }
                                }
                            },
                            enabled = !isEvaluating,
                            containerColor = palette.aiAccent,
                            contentColor = Color.White,
                            modifier = Modifier.testTag("check_calligraphy_button")
                        ) {
                            if (isEvaluating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Grading…")
                            } else {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Check")
                            }
                        }
                    }

                    errorMessage?.let { msg ->
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = palette.dangerSoft,
                            border = BorderStroke(1.5.dp, palette.danger),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = msg,
                                color = palette.danger,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    evaluation?.let { eval ->
                        Spacer(Modifier.height(10.dp))
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
                                        color = if (eval.isPass) palette.success else palette.languageAccent
                                    )
                                    TactileButton(
                                        onClick = {
                                            if (index < prompts.lastIndex) {
                                                index++
                                                clearCanvas()
                                            } else {
                                                val avg = if (scores.isEmpty()) 75 else scores.average().toInt().coerceIn(40, 100)
                                                val xp = 40 + (avg / 3)
                                                val dur = ((System.currentTimeMillis() - startTimeMs) / 1000L).toInt().coerceAtLeast(5)
                                                completedResult = avg to xp
                                                isSavingResult = true
                                                scope.launch {
                                                    try {
                                                        onCompleteSession(
                                                            GameType.CALLIGRAPHY,
                                                            language,
                                                            difficulty,
                                                            avg,
                                                            xp,
                                                            dur,
                                                            "${language.displayName} Calligraphy ($avg% avg)"
                                                        )
                                                    } finally {
                                                        isSavingResult = false
                                                    }
                                                }
                                            }
                                        },
                                        containerColor = palette.inkBorder,
                                        contentColor = Color.White,
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                        modifier = Modifier.testTag("next_character_button")
                                    ) {
                                        Text(if (index < prompts.lastIndex) "Next" else "Finish")
                                        Spacer(Modifier.width(4.dp))
                                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
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
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

private fun renderStrokesToBitmap(
    strokes: List<InkStroke>,
    srcWidth: Int,
    srcHeight: Int
): Bitmap {
    val outSize = 384
    val bitmap = Bitmap.createBitmap(outSize, outSize, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)

    val scaleX = outSize.toFloat() / srcWidth.toFloat()
    val scaleY = outSize.toFloat() / srcHeight.toFloat()
    val scale = minOf(scaleX, scaleY)

    val paint = Paint().apply {
        color = android.graphics.Color.BLACK
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 18f * scale
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    strokes.forEach { stroke ->
        val pts = stroke.points
        if (pts.size == 1) {
            canvas.drawCircle(pts[0].x * scaleX, pts[0].y * scaleY, 9f * scale, paint)
        } else if (pts.size > 1) {
            val path = AndroidPath().apply {
                moveTo(pts.first().x * scaleX, pts.first().y * scaleY)
                for (i in 1 until pts.size) {
                    lineTo(pts[i].x * scaleX, pts[i].y * scaleY)
                }
            }
            canvas.drawPath(path, paint)
        }
    }
    return bitmap
}
