package com.runwithfun.familycare.ui.home

import com.runwithfun.familycare.domain.CareDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DayLabelsTest {
    @Test
    fun sundayWeekCrossesTheYearBoundary() {
        val week = weekContaining(CareDate(2026, 12, 31))

        assertEquals(CareDate(2026, 12, 27), week.first())
        assertEquals(CareDate(2027, 1, 2), week.last())
        assertEquals("Thursday", CareDate(2026, 12, 31).weekdayName())
    }

    @Test
    fun demoAgeAndFutureBirthDateAreHandled() {
        assertEquals("8 weeks old", childAge(CareDate(2026, 9, 28), CareDate(2026, 11, 23)))
        assertNull(childAge(CareDate(2026, 11, 24), CareDate(2026, 11, 23)))
    }
}
