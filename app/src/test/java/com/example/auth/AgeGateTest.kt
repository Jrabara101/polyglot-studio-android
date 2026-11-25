package com.example.auth

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AgeGateTest {

    private fun utcMidnight(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear(); set(year, month - 1, day)
        }.timeInMillis

    private fun today(year: Int, month: Int, day: Int): Calendar =
        Calendar.getInstance().apply { clear(); set(year, month - 1, day) }

    @Test fun turnsThirteenToday_isThirteen() =
        assertEquals(13, ageFromBirthdayMillis(utcMidnight(2013, 11, 21), today(2026, 11, 21)))

    @Test fun dayBeforeThirteenthBirthday_isTwelve() =
        assertEquals(12, ageFromBirthdayMillis(utcMidnight(2013, 11, 22), today(2026, 11, 21)))

    @Test fun laterMonthNotYetReached_isTwelve() =
        assertEquals(12, ageFromBirthdayMillis(utcMidnight(2013, 12, 1), today(2026, 11, 21)))

    @Test fun usernameRules() {
        assertNull(validateUsername("polyglot_fan.01"))
        assertNotNull(validateUsername("ab"))
        assertNotNull(validateUsername("has space"))
        assertNotNull(validateUsername("a".repeat(21)))
    }
}
