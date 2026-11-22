package com.runwithfun.familycare.session

import com.runwithfun.familycare.demo.DemoFamily
import com.runwithfun.familycare.domain.CareDate
import com.runwithfun.familycare.domain.Task
import com.runwithfun.familycare.domain.TaskStatus
import com.runwithfun.familycare.domain.Visit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CareSessionTest {
    @Test
    fun savesAndRestoresEditsWithoutResettingLocalIds() {
        val store = MemoryCarePlanEventStore()
        val first = CareSession(DemoFamily.createPlan(), store)
        val moved = CareDate(2026, 11, 24)
        val visit = first.eventsOn(DemoFamily.today, DemoFamily.childId)
            .filterIsInstance<Visit>()
            .single()
            .copy(date = moved, title = "Rescheduled well-baby visit")
        first.saveVisit(visit)

        val newId = first.allocateLocalId()
        val task = Task(
            id = newId,
            personId = DemoFamily.motherId,
            title = "Pack questions for the clinic",
            date = moved,
            status = TaskStatus.OPEN,
        )
        first.saveTask(task)
        first.allocateLocalId() // burns local-plan-item-(n+1) so restore must not reuse newId

        val restored = CareSession(DemoFamily.createPlan(), store)
        assertEquals(visit, restored.event(visit.id))
        assertEquals(listOf(visit, task), restored.eventsOn(moved))
        assertTrue(restored.eventsOn(DemoFamily.today, DemoFamily.childId).none { it.id == visit.id })
        val resumedId = restored.allocateLocalId()
        assertTrue(resumedId != newId)
        assertTrue(resumedId != task.id)
    }

    @Test
    fun draftIsNotInPlanUntilSaveAndAssistantTaskCanCoexist() {
        val store = MemoryCarePlanEventStore()
        val session = CareSession(DemoFamily.createPlan(), store)
        assertNull(session.event("local-plan-item-1"))

        val proposal = DemoFamily.recoveryQuestion
        session.plan.confirm(proposal)
        session.notifyChanged()

        val planItem = Task(
            id = session.allocateLocalId(),
            personId = DemoFamily.childId,
            title = "Bring the growth chart",
            date = DemoFamily.today,
        )
        session.saveTask(planItem)

        val today = session.eventsOn(DemoFamily.today).map { it.id }.toSet()
        assertTrue(proposal.task.id in today)
        assertTrue(planItem.id in today)
    }

    @Test
    fun codecRoundTripsVisitAndTask() {
        val visit = DemoFamily.createPlan().eventsOn(DemoFamily.today).filterIsInstance<Visit>().first()
        val task = Task(
            id = "local-plan-item-2",
            personId = DemoFamily.motherId,
            title = "Ask about swelling",
            date = CareDate(2026, 11, 25),
            status = TaskStatus.DONE,
        )
        val encoded = encodeCarePlanEvents(CarePlanEventSnapshot(3, listOf(visit, task)))
        val decoded = decodeCarePlanEvents(encoded)
        assertEquals(3, decoded.nextLocalId)
        assertEquals(listOf(visit, task), decoded.events)
    }

    private class MemoryCarePlanEventStore(
        private var value: String? = null,
    ) : CarePlanEventStore {
        override fun load(): String? = value
        override fun save(value: String) {
            this.value = value
        }
    }
}
