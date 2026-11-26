package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import com.example.data.model.TargetLanguage
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContrastTest {

    @Test
    fun all_languages_light_and_dark_meet_wcag_aa_contrast_4_5_to_1() {
        for (dark in listOf(false, true)) {
            val mode = if (dark) "Dark" else "Light"
            for (lang in TargetLanguage.entries) {
                val scheme = studioColorSchemeFor(lang, dark)
                val palette = studioPaletteFor(lang, dark)

                assertWcagAa("$mode/${lang.code} onPrimary on primary", scheme.onPrimary, scheme.primary)
                assertWcagAa("$mode/${lang.code} onLanguageAccent on languageAccent", palette.onLanguageAccent, palette.languageAccent)
                assertWcagAa("$mode/${lang.code} languageAccent on cardSurface", palette.languageAccent, palette.cardSurface)
                assertWcagAa("$mode/${lang.code} languageAccent on languageTint", palette.languageAccent, palette.languageTint)
                assertWcagAa("$mode/${lang.code} onSuccess on success", palette.onSuccess, palette.success)
                assertWcagAa("$mode/${lang.code} success on successSoft", palette.success, palette.successSoft)
                assertWcagAa("$mode/${lang.code} onDanger on danger", palette.onDanger, palette.danger)
                assertWcagAa("$mode/${lang.code} danger on dangerSoft", palette.danger, palette.dangerSoft)
                assertWcagAa("$mode/${lang.code} onAiAccent on aiAccent", palette.onAiAccent, palette.aiAccent)
                assertWcagAa("$mode/${lang.code} aiAccent on aiSoft", palette.aiAccent, palette.aiSoft)
                assertWcagAa("$mode/${lang.code} onSurface on cardSurface", scheme.onSurface, palette.cardSurface)
                assertWcagAa("$mode/${lang.code} onSurfaceVariant on subtleSurface", scheme.onSurfaceVariant, palette.subtleSurface)
            }
        }
    }

    private fun assertWcagAa(label: String, fg: Color, bg: Color) {
        val ratio = contrastRatio(fg, bg)
        assertTrue(
            "$label contrast ratio %.2f:1 is below WCAG AA 4.5:1".format(ratio),
            ratio >= 4.5
        )
    }
}
