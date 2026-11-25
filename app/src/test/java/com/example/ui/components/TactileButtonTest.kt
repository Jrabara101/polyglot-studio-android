package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.example.data.model.TargetLanguage
import com.example.ui.theme.PolyglotStudioTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class TactileButtonTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun full_width_and_weighted_tactile_buttons_fill_their_bounds() {
        var fullClicks = 0
        var leftClicks = 0
        var fullOuterCenterX = 0f
        var fullLabelCenterX = 0f
        var leftOuterCenterX = 0f
        var leftLabelCenterX = 0f

        compose.setContent {
            PolyglotStudioTheme(activeLanguage = TargetLanguage.SPANISH, darkTheme = false) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    TactileButton(
                        onClick = { fullClicks++ },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("full_btn")
                            .onGloballyPositioned { fullOuterCenterX = it.boundsInRoot().center.x }
                    ) {
                        Text(
                            "Skip",
                            modifier = Modifier.onGloballyPositioned {
                                fullLabelCenterX = it.boundsInRoot().center.x
                            }
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TactileButton(
                            onClick = { leftClicks++ },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("left_btn")
                                .onGloballyPositioned { leftOuterCenterX = it.boundsInRoot().center.x }
                        ) {
                            Text(
                                "Go",
                                modifier = Modifier.onGloballyPositioned {
                                    leftLabelCenterX = it.boundsInRoot().center.x
                                }
                            )
                        }
                        TactileButton(
                            onClick = {},
                            modifier = Modifier
                                .weight(1f)
                                .testTag("right_btn")
                        ) {
                            Text("Slow")
                        }
                    }
                }
            }
        }
        compose.waitForIdle()

        // Tapping near the right edge of the full-width and weighted buttons must hit the clickable Surface.
        compose.onNodeWithTag("full_btn").performTouchInput {
            click(Offset(width * 0.85f, height * 0.5f))
        }
        compose.onNodeWithTag("left_btn").performTouchInput {
            click(Offset(width * 0.85f, height * 0.5f))
        }
        compose.waitForIdle()

        assertEquals("Right side of full-width button must be clickable", 1, fullClicks)
        assertEquals("Right side of weighted button must be clickable", 1, leftClicks)
        assertTrue(
            "Full-width button label ($fullLabelCenterX) must be centered in button ($fullOuterCenterX)",
            abs(fullOuterCenterX - fullLabelCenterX) <= 8f
        )
        assertTrue(
            "Weighted button label ($leftLabelCenterX) must be centered in button ($leftOuterCenterX)",
            abs(leftOuterCenterX - leftLabelCenterX) <= 8f
        )
    }

    @Test
    fun wrap_content_tactile_button_stays_compact() {
        var rowW = 0
        var compactOuterW = 0
        var compactClicks = 0

        compose.setContent {
            PolyglotStudioTheme(activeLanguage = TargetLanguage.JAPANESE, darkTheme = false) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .onGloballyPositioned { rowW = it.size.width }
                ) {
                    TactileButton(
                        onClick = { compactClicks++ },
                        modifier = Modifier
                            .testTag("compact_btn")
                            .onGloballyPositioned { compactOuterW = it.size.width }
                    ) {
                        Text("Guide")
                    }
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag("compact_btn").performTouchInput { click(center) }
        compose.waitForIdle()

        assertEquals(1, compactClicks)
        assertTrue("Row width should be full width ($rowW)", rowW > 400)
        assertTrue(
            "Compact button ($compactOuterW) should stay narrower than half row ($rowW)",
            compactOuterW < rowW / 2
        )
    }

    @Test
    fun compact_padding_tactile_button_has_no_vertical_shadow_bleed() {
        var outerHeightPx = 0
        var innerRowContentHeightPx = 0
        var shadowPx = 0
        var verticalPaddingPx = 0

        compose.setContent {
            val density = LocalDensity.current
            shadowPx = with(density) { 3.dp.roundToPx() }
            verticalPaddingPx = with(density) { 16.dp.roundToPx() } // 8.dp top + 8.dp bottom

            PolyglotStudioTheme(activeLanguage = TargetLanguage.JAPANESE, darkTheme = false) {
                TactileButton(
                    onClick = {},
                    shadowOffset = 3.dp,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .testTag("sign_out_btn")
                        .onGloballyPositioned { outerHeightPx = it.size.height }
                ) {
                    Text(
                        "Sign Out",
                        modifier = Modifier.onGloballyPositioned { coords ->
                            innerRowContentHeightPx = coords.parentLayoutCoordinates?.size?.height ?: 0
                        }
                    )
                }
            }
        }
        compose.waitForIdle()

        val expectedInnerHeight = outerHeightPx - shadowPx - verticalPaddingPx
        assertTrue(
            "Surface height must fill the 48dp touch target without vertical shadow bleed " +
                "(expected inner=$expectedInnerHeight, actual=$innerRowContentHeightPx, outer=$outerHeightPx)",
            abs(expectedInnerHeight - innerRowContentHeightPx) <= 2
        )
    }
}
