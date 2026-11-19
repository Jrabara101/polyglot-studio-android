package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.DifficultyLevel
import com.example.data.model.TargetLanguage
import com.example.ui.theme.LocalStudioPalette
import com.example.ui.theme.accentForLanguage

@Composable
fun TactileCard(
    modifier: Modifier = Modifier,
    containerColor: Color = LocalStudioPalette.current.cardSurface,
    borderColor: Color = LocalStudioPalette.current.inkBorder,
    shadowColor: Color = LocalStudioPalette.current.shadowColor,
    cornerRadius: Dp = 20.dp,
    shadowOffset: Dp = 4.dp,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val shape: Shape = RoundedCornerShape(cornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val animatedOffset by animateDpAsState(
        targetValue = if (onClick != null && isPressed) 1.dp else shadowOffset,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tactilePressOffset"
    )
    val topShift = shadowOffset - animatedOffset

    Box(
        modifier = modifier
            .padding(bottom = shadowOffset, end = 2.dp)
    ) {
        // Solid tactile bottom-offset plate
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 2.dp, y = shadowOffset)
                .clip(shape)
                .background(shadowColor)
        )
        Surface(
            shape = shape,
            color = containerColor,
            border = BorderStroke(1.5.dp, borderColor),
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = topShift * 0.5f, y = topShift)
                .then(
                    if (onClick != null) {
                        Modifier
                            .minimumInteractiveComponentSize()
                            .clip(shape)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = onClick
                            )
                    } else Modifier
                )
        ) {
            Box(
                modifier = Modifier.padding(contentPadding),
                content = content
            )
        }
    }
}

@Composable
fun TactileButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    shadowOffset: Dp = 3.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
    content: @Composable RowScope.() -> Unit
) {
    val palette = LocalStudioPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val animatedOffset by animateDpAsState(
        targetValue = if (enabled && isPressed) 0.dp else shadowOffset,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "buttonPressOffset"
    )
    val shift = shadowOffset - animatedOffset
    val shape = RoundedCornerShape(16.dp)
    val effectiveBg = if (enabled) containerColor else containerColor.copy(alpha = 0.45f)
    val effectiveContent = if (enabled) contentColor else contentColor.copy(alpha = 0.7f)

    Box(modifier = modifier.padding(bottom = shadowOffset)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(y = shadowOffset)
                .clip(shape)
                .background(if (enabled) palette.shadowColor else palette.shadowColor.copy(alpha = 0.35f))
        )
        Surface(
            shape = shape,
            color = effectiveBg,
            contentColor = effectiveContent,
            border = BorderStroke(1.5.dp, palette.inkBorder),
            modifier = Modifier
                .offset(y = shift)
                .minimumInteractiveComponentSize()
                .clip(shape)
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = androidx.compose.material3.ripple(),
                    onClick = onClick
                )
        ) {
            Row(
                modifier = Modifier.padding(contentPadding),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    }
}

@Composable
fun StudioBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    val palette = LocalStudioPalette.current
    Surface(
        shape = RoundedCornerShape(50),
        color = containerColor,
        border = BorderStroke(1.dp, palette.inkBorder.copy(alpha = 0.45f)),
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun LanguageSelectorRow(
    selectedLanguage: TargetLanguage,
    onSelectLanguage: (TargetLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalStudioPalette.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TargetLanguage.entries.forEach { lang ->
            val selected = lang == selectedLanguage
            val (accent, tint) = accentForLanguage(lang, palette.isDark)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (selected) accent else palette.cardSurface,
                border = BorderStroke(1.5.dp, palette.inkBorder),
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("lang_chip_${lang.code}")
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onSelectLanguage(lang) }
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (selected) Color.White.copy(alpha = 0.22f) else tint)
                            .border(1.dp, palette.inkBorder.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = lang.shortBadge,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (selected) Color.White else accent
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = lang.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Text(
                        text = lang.nativeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) Color.White.copy(alpha = 0.88f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun DifficultySelectorRow(
    selectedDifficulty: DifficultyLevel,
    onSelectDifficulty: (DifficultyLevel) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalStudioPalette.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DifficultyLevel.entries.forEach { level ->
            val selected = level == selectedDifficulty
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (selected) palette.inkBorder else palette.cardSurface,
                border = BorderStroke(1.5.dp, palette.inkBorder),
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("diff_chip_${level.code}")
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelectDifficulty(level) }
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = level.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) palette.cardSurface else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
