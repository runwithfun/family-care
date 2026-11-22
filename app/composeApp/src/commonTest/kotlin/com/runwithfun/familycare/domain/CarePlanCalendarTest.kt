package com.runwithfun.familycare.domain

import com.runwithfun.familycare.demo.DemoFamily
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CarePlanCalendarTest {
    @Test
    fun savedVisitAppearsOnDateAndDailyPlan() {
        val plan = DemoFamily.createPlan()
        val moved = CareDate(2026, 11, 24)
        val visit = plan.eventsOn(DemoFamily.today, DemoFamily.childId)
            .filterIsInstance<Visit>()
            .single()
            .copy(date = moved)

        plan.updateEvent(visit)

        assertTrue(plan.eventsOn(DemoFamily.today, DemoFamily.childId).none { it.id == visit.id })
        assertEquals(listOf(visit), plan.eventsOn(moved))
        assertEquals(visit, plan.event(visit.id))
        assertEquals(listOf(visit), plan.eventsInMonth(2026, 11).filter { it.id == visit.id })
    }

    @Test
    fun editingTaskPreservesIdentityAndRetryDoesNotDuplicate() {
        val plan = DemoFamily.createPlan()
        val original = plan.eventsOn(DemoFamily.today, DemoFamily.childId)
            .filterIsInstance<Task>()
            .single()
        val edited = original.copy(
            title = "Request the full immunization packet",
            status = TaskStatus.DONE,
        )

        plan.updateEvent(edited)
        plan.updateEvent(edited)

        assertEquals(edited, plan.event(original.id))
        assertEquals(
            listOf(edited),
            plan.eventsOn(DemoFamily.today, DemoFamily.childId).filterIsInstance<Task>(),
        )
        assertEquals(original.id, plan.event(original.id)?.id)
    }

    @Test
    fun updateRejectsUnknownEventAndOwnerChange() {
        val plan = DemoFamily.createPlan()
        val visit = plan.eventsOn(DemoFamily.today, DemoFamily.childId)
            .filterIsInstance<Visit>()
            .single()

        assertFailsWith<IllegalArgumentException> {
            plan.updateEvent(visit.copy(id = "missing-visit"))
        }
        assertFailsWith<IllegalArgumentException> {
            plan.updateEvent(visit.copy(personId = DemoFamily.motherId))
        }
        assertNull(plan.event("missing-visit"))
    }

    @Test
    fun addEventRetryStillRejectsConflictingDetails() {
        val plan = DemoFamily.createPlan()
        val visit = plan.eventsOn(DemoFamily.today, DemoFamily.childId)
            .filterIsInstance<Visit>()
            .single()

        plan.addEvent(visit)
        assertFailsWith<IllegalArgumentException> {
            plan.addEvent(visit.copy(title = "Different title"))
        }
        assertEquals(visit.title, (plan.event(visit.id) as Visit).title)
    }

    @Test
    fun calendarMonthMathUsesMondayFirstGrid() {
        // 1 Nov 2026 is Sunday → six leading blanks in a Monday-first grid.
        assertEquals(6, CareCalendar.dayOfWeekMondayBased(2026, 11, 1))
        assertEquals(30, CareCalendar.lengthOfMonth(2026, 11))
        assertEquals(CareDate(2026, 11, 24), CareCalendar.shiftDay(CareDate(2026, 11, 23), 1))
        assertEquals(CareDate(2026, 12, 1), CareCalendar.shiftDay(CareDate(2026, 11, 30), 1))
        assertEquals(CareDate(2026, 11, 30), CareCalendar.shiftDay(CareDate(2026, 12, 1), -1))
        assertEquals(
            listOf(2026 to 12, 2027 to 1),
            listOf(
                CareCalendar.shiftMonth(2026, 11, 1),
                CareCalendar.shiftMonth(2026, 12, 1),
            ),
        )
        val cells = CareCalendar.monthCells(2026, 11)
        assertEquals(null, cells[0])
        assertEquals(CareDate(2026, 11, 1), cells[6])
        assertEquals(0, cells.size % 7)
    }

    @Test
    fun monthNavigationStopsAtCareDateYearEdges() {
        assertTrue(CareCalendar.canShiftMonth(1, 1, 1))
        assertTrue(!CareCalendar.canShiftMonth(1, 1, -1))
        assertNull(CareCalendar.shiftMonthBounded(1, 1, -1))
        assertTrue(CareCalendar.canShiftMonth(9999, 12, -1))
        assertTrue(!CareCalendar.canShiftMonth(9999, 12, 1))
        assertNull(CareCalendar.shiftMonthBounded(9999, 12, 1))
        assertEquals(1 to 2, CareCalendar.shiftMonthBounded(1, 1, 1))
        assertEquals(9999 to 11, CareCalendar.shiftMonthBounded(9999, 12, -1))
    }
}
