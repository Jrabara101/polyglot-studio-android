package com.example.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioIconsTest {

    @Test
    fun all_31_studio_icons_build_non_empty_vectors_with_expected_mirroring() {
        val autoMirroredIcons = listOf(
            "HelpOutline" to StudioIcons.HelpOutline,
            "Logout" to StudioIcons.Logout,
            "MenuBook" to StudioIcons.MenuBook,
            "Undo" to StudioIcons.Undo,
            "VolumeUp" to StudioIcons.VolumeUp
        )
        val nonMirroredIcons = listOf(
            "AutoAwesome" to StudioIcons.AutoAwesome,
            "BarChart" to StudioIcons.BarChart,
            "Bolt" to StudioIcons.Bolt,
            "Bookmark" to StudioIcons.Bookmark,
            "BookmarkAdd" to StudioIcons.BookmarkAdd,
            "Brush" to StudioIcons.Brush,
            "CalendarMonth" to StudioIcons.CalendarMonth,
            "CallEnd" to StudioIcons.CallEnd,
            "Cancel" to StudioIcons.Cancel,
            "ChevronLeft" to StudioIcons.ChevronLeft,
            "ChevronRight" to StudioIcons.ChevronRight,
            "DeleteOutline" to StudioIcons.DeleteOutline,
            "DeleteSweep" to StudioIcons.DeleteSweep,
            "EmojiEvents" to StudioIcons.EmojiEvents,
            "Forum" to StudioIcons.Forum,
            "GraphicEq" to StudioIcons.GraphicEq,
            "History" to StudioIcons.History,
            "LocalFireDepartment" to StudioIcons.LocalFireDepartment,
            "Mic" to StudioIcons.Mic,
            "Quiz" to StudioIcons.Quiz,
            "RecordVoiceOver" to StudioIcons.RecordVoiceOver,
            "SlowMotionVideo" to StudioIcons.SlowMotionVideo,
            "Stop" to StudioIcons.Stop,
            "Style" to StudioIcons.Style,
            "Visibility" to StudioIcons.Visibility,
            "VisibilityOff" to StudioIcons.VisibilityOff
        )

        assertEquals(31, autoMirroredIcons.size + nonMirroredIcons.size)

        for ((name, vector) in autoMirroredIcons) {
            assertTrue("$name should have vector nodes", vector.root.size > 0)
            assertTrue("$name should have autoMirror=true", vector.autoMirror)
        }
        for ((name, vector) in nonMirroredIcons) {
            assertTrue("$name should have vector nodes", vector.root.size > 0)
            assertFalse("$name should have autoMirror=false", vector.autoMirror)
        }
    }
}
