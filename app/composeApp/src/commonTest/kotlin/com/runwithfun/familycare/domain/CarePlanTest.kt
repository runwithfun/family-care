package com.runwithfun.familycare.domain

import com.runwithfun.familycare.demo.DemoFamily
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CarePlanTest {
    @Test
    fun agendaFiltersByDayAndPersonWithUntimedTasksAfterVisits() {
        val plan = DemoFamily.createPlan()

        assertEquals(
            listOf(CareTime(9, 30), CareTime(14, 30), null),
            plan.eventsOn(DemoFamily.today).map { it.time },
        )
        assertEquals(
            listOf("Well-baby visit", "Request a copy of the immunization record"),
            plan.eventsOn(DemoFamily.today, DemoFamily.childId).map { it.title },
        )
        assertTrue(plan.eventsOn(CareDate(2026, 11, 24)).isEmpty())
        assertEquals(
            CareTime(14, 30),
            plan.eventsOn(DemoFamily.recoveryVisitDate, DemoFamily.motherId).single().time,
        )
    }

    @Test
    fun repeatedConfirmationSharesTheEventWithoutOverwritingIt() {
        val plan = DemoFamily.createPlan()
        val proposal = DemoFamily.recoveryQuestion

        assertTrue(plan.eventsOn(DemoFamily.today).none { it.id == proposal.task.id })
        assertEquals(proposal.task, plan.confirm(proposal))
        assertEquals(proposal.task, plan.confirm(proposal))
        plan.addEvent(proposal.task)

        assertFailsWith<IllegalArgumentException> {
            plan.confirm(AssistantProposal(proposal.task.copy(title = "A different action")))
        }
        assertEquals(
            listOf(proposal.task),
            plan.eventsOn(DemoFamily.today, DemoFamily.motherId).filter { it.id == proposal.task.id },
        )
        assertTrue(plan.eventsOn(DemoFamily.today, DemoFamily.childId).none { it.id == proposal.task.id })
    }

    @Test
    fun confirmationRejectsAnotherPersonsVisit() {
        val plan = DemoFamily.createPlan()
        val proposal = DemoFamily.recoveryQuestion
        val childVisit = plan.eventsOn(DemoFamily.today, DemoFamily.childId).filterIsInstance<Visit>().single()

        assertFailsWith<IllegalArgumentException> {
            plan.confirm(AssistantProposal(proposal.task.copy(visitId = childVisit.id)))
        }
        assertTrue(plan.eventsOn(DemoFamily.today).none { it.id == proposal.task.id })
    }
}
