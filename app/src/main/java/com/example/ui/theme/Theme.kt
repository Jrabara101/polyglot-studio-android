package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.model.TargetLanguage

@Immutable
data class StudioPalette(
    val inkBorder: Color,
    val shadowColor: Color,
    val languageAccent: Color,
    val languageTint: Color,
    val onLanguageAccent: Color,
    val cardSurface: Color,
    val subtleSurface: Color,
    val success: Color,
    val successSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
    val aiAccent: Color,
    val aiSoft: Color,
    val isDark: Boolean
)

val LocalStudioPalette = staticCompositionLocalOf {
    StudioPalette(
        inkBorder = StudioInk,
        shadowColor = StudioInk,
        languageAccent = JapanPersimmon,
        languageTint = JapanSakuraMist,
        onLanguageAccent = Color.White,
        cardSurface = StudioPaper,
        subtleSurface = StudioWarmAlt,
        success = MintEmerald,
        successSoft = MintSoft,
        danger = CoralAlert,
        dangerSoft = CoralSoft,
        aiAccent = AiViolet,
        aiSoft = AiVioletSoft,
        isDark = false
    )
}

private val StudioShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

fun accentForLanguage(language: TargetLanguage, darkTheme: Boolean): Pair<Color, Color> =
    when (language) {
        TargetLanguage.JAPANESE -> JapanPersimmon to if (darkTheme) JapanDarkTint else JapanSakuraMist
        TargetLanguage.SPANISH -> SpainSaffron to if (darkTheme) SpainDarkTint else SpainSunlitCream
        TargetLanguage.FRENCH -> FranceCobalt to if (darkTheme) FranceDarkTint else FranceSkyTint
    }

@Composable
fun PolyglotStudioTheme(
    activeLanguage: TargetLanguage = TargetLanguage.JAPANESE,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val (langAccent, langTint) = accentForLanguage(activeLanguage, darkTheme)

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = langAccent,
            onPrimary = Color.White,
            primaryContainer = langTint,
            onPrimaryContainer = StudioDarkText,
            secondary = AiViolet,
            onSecondary = Color.White,
            secondaryContainer = Color(0xFF33265A),
            onSecondaryContainer = Color(0xFFE7DEFF),
            tertiary = MintEmerald,
            onTertiary = Color.White,
            background = StudioDarkCanvas,
            onBackground = StudioDarkText,
            surface = StudioDarkSurface,
            onSurface = StudioDarkText,
            surfaceVariant = StudioDarkSurfaceAlt,
            onSurfaceVariant = StudioDarkMutedText,
            outline = Color(0xFF494458),
            error = CoralAlert,
            onError = Color.White
        )
    } else {
        lightColorScheme(
            primary = langAccent,
            onPrimary = Color.White,
            primaryContainer = langTint,
            onPrimaryContainer = StudioInk,
            secondary = AiViolet,
            onSecondary = Color.White,
            secondaryContainer = AiVioletSoft,
            onSecondaryContainer = StudioInk,
            tertiary = MintEmerald,
            onTertiary = Color.White,
            background = StudioParchment,
            onBackground = StudioInk,
            surface = StudioPaper,
            onSurface = StudioInk,
            surfaceVariant = StudioWarmAlt,
            onSurfaceVariant = StudioMutedInk,
            outline = StudioInk,
            error = CoralAlert,
            onError = Color.White
        )
    }

    val studioPalette = StudioPalette(
        inkBorder = if (darkTheme) Color(0xFF4B4659) else StudioInk,
        shadowColor = if (darkTheme) Color(0xFF0B0A0F) else StudioInk,
        languageAccent = langAccent,
        languageTint = langTint,
        onLanguageAccent = Color.White,
        cardSurface = if (darkTheme) StudioDarkSurface else StudioPaper,
        subtleSurface = if (darkTheme) StudioDarkSurfaceAlt else StudioWarmAlt,
        success = MintEmerald,
        successSoft = if (darkTheme) Color(0xFF173D31) else MintSoft,
        danger = CoralAlert,
        dangerSoft = if (darkTheme) Color(0xFF471C22) else CoralSoft,
        aiAccent = AiViolet,
        aiSoft = if (darkTheme) Color(0xFF2D2252) else AiVioletSoft,
        isDark = darkTheme
    )

    CompositionLocalProvider(LocalStudioPalette provides studioPalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PolyglotTypography,
            shapes = StudioShapes,
            content = content
        )
    }
}
