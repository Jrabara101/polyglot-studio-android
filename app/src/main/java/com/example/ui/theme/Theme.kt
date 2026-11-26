package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

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
    val onSuccess: Color = Color.White,
    val danger: Color,
    val dangerSoft: Color,
    val onDanger: Color = Color.White,
    val aiAccent: Color,
    val aiSoft: Color,
    val onAiAccent: Color = Color.White,
    val isDark: Boolean
)

val LocalStudioPalette = staticCompositionLocalOf {
    studioPaletteFor(TargetLanguage.JAPANESE, darkTheme = false)
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
        TargetLanguage.JAPANESE ->
            (if (darkTheme) JapanPersimmonDark else JapanPersimmon) to
                (if (darkTheme) JapanDarkTint else JapanSakuraMist)
        TargetLanguage.SPANISH ->
            (if (darkTheme) SpainSaffronDark else SpainSaffron) to
                (if (darkTheme) SpainDarkTint else SpainSunlitCream)
        TargetLanguage.FRENCH ->
            (if (darkTheme) FranceCobaltDark else FranceCobalt) to
                (if (darkTheme) FranceDarkTint else FranceSkyTint)
    }

fun studioColorSchemeFor(
    activeLanguage: TargetLanguage,
    darkTheme: Boolean
): ColorScheme {
    val (langAccent, langTint) = accentForLanguage(activeLanguage, darkTheme)
    return if (darkTheme) {
        darkColorScheme(
            primary = langAccent,
            onPrimary = StudioInk,
            primaryContainer = langTint,
            onPrimaryContainer = StudioDarkText,
            secondary = AiVioletDark,
            onSecondary = StudioInk,
            secondaryContainer = AiVioletSoftDark,
            onSecondaryContainer = Color(0xFFE7DEFF),
            tertiary = MintEmeraldDark,
            onTertiary = StudioInk,
            background = StudioDarkCanvas,
            onBackground = StudioDarkText,
            surface = StudioDarkSurface,
            onSurface = StudioDarkText,
            surfaceVariant = StudioDarkSurfaceAlt,
            onSurfaceVariant = StudioDarkMutedText,
            outline = Color(0xFF494458),
            error = CoralAlertDark,
            onError = StudioInk
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
}

fun studioPaletteFor(
    activeLanguage: TargetLanguage,
    darkTheme: Boolean
): StudioPalette {
    val (langAccent, langTint) = accentForLanguage(activeLanguage, darkTheme)
    val onAccent = if (darkTheme) StudioInk else Color.White
    return StudioPalette(
        inkBorder = if (darkTheme) Color(0xFF4B4659) else StudioInk,
        shadowColor = if (darkTheme) Color(0xFF0B0A0F) else StudioInk,
        languageAccent = langAccent,
        languageTint = langTint,
        onLanguageAccent = onAccent,
        cardSurface = if (darkTheme) StudioDarkSurface else StudioPaper,
        subtleSurface = if (darkTheme) StudioDarkSurfaceAlt else StudioWarmAlt,
        success = if (darkTheme) MintEmeraldDark else MintEmerald,
        successSoft = if (darkTheme) MintSoftDark else MintSoft,
        onSuccess = onAccent,
        danger = if (darkTheme) CoralAlertDark else CoralAlert,
        dangerSoft = if (darkTheme) CoralSoftDark else CoralSoft,
        onDanger = onAccent,
        aiAccent = if (darkTheme) AiVioletDark else AiViolet,
        aiSoft = if (darkTheme) AiVioletSoftDark else AiVioletSoft,
        onAiAccent = onAccent,
        isDark = darkTheme
    )
}

/** Computes WCAG 2.1 contrast ratio between two opaque sRGB [Color] values (range 1.0..21.0). */
internal fun contrastRatio(foreground: Color, background: Color): Double {
    val l1 = relativeLuminance(foreground)
    val l2 = relativeLuminance(background)
    return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
}

private fun relativeLuminance(color: Color): Double {
    fun linearize(channel: Float): Double {
        val c = channel.toDouble()
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * linearize(color.red) +
        0.7152 * linearize(color.green) +
        0.0722 * linearize(color.blue)
}

@Composable
fun PolyglotStudioTheme(
    activeLanguage: TargetLanguage = TargetLanguage.JAPANESE,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = studioColorSchemeFor(activeLanguage, darkTheme)
    val studioPalette = studioPaletteFor(activeLanguage, darkTheme)

    CompositionLocalProvider(LocalStudioPalette provides studioPalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PolyglotTypography,
            shapes = StudioShapes,
            content = content
        )
    }
}
