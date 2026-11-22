package com.runwithfun.familycare.ui.assistant

import com.runwithfun.familycare.demo.DemoFamily
import com.runwithfun.familycare.domain.CareDate
import com.runwithfun.familycare.domain.CarePlan
import com.runwithfun.familycare.domain.Visit
import com.runwithfun.familycare.domain.VisitStatus
import com.runwithfun.familycare.session.CarePlanEventStore
import com.runwithfun.familycare.session.CareSession
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AssistantActionsTest {
    @Test
    fun confirmsOnlyOnceAndRestoresOnTheCorrectDateAndPerson() {
        val storage = MemoryStorage()
        val plan = DemoFamily.createPlan()
        val actions = AssistantActions(plan, storage)
        val task = DemoFamily.recoveryQuestion.task

        assertFalse(actions.confirmed)
        assertNull(storage.value)
        assertFalse(plan.eventsOn(task.date).any { it.id == task.id })
        assertContains(assertNotNull(actions.originalText), assertNotNull(actions.excerpt))

        assertTrue(actions.confirm())
        assertTrue(actions.confirm())
        assertEquals(task.id, storage.value)
        assertEquals(1, storage.writes)
        assertEquals(listOf(task), plan.eventsOn(task.date, task.personId).filter { it.id == task.id })

        val reopenedPlan = DemoFamily.createPlan()
        assertTrue(AssistantActions(reopenedPlan, storage).confirmed)
        assertEquals(listOf(task), reopenedPlan.eventsOn(task.date, task.personId).filter { it.id == task.id })
        assertFalse(reopenedPlan.eventsOn(task.date, DemoFamily.childId).any { it.id == task.id })
        assertEquals(1, storage.writes)
    }

    @Test
    fun missingSourcePreventsRestorationAndConfirmation() {
        val demo = DemoFamily.createPlan()
        val task = DemoFamily.recoveryQuestion.task
        val plan = CarePlan(
            people = demo.people,
            events = emptyList(),
            documents = emptyList(),
            recordedImmunizations = emptyList(),
            immunizationReferences = emptyList(),
        )
        val storage = MemoryStorage(value = task.id)
        val actions = AssistantActions(plan, storage)

        assertFalse(actions.canConfirm)
        assertFalse(actions.confirmed)
        assertFalse(actions.confirm())
        assertTrue(plan.eventsOn(task.date).isEmpty())
        assertEquals(0, storage.writes)
    }

    @Test
    fun declinesConfirmationAfterTheLinkedVisitIsMovedOrFinished() {
        val plan = DemoFamily.createPlan()
        val storage = MemoryStorage()
        val actions = AssistantActions(plan, storage)
        val task = DemoFamily.recoveryQuestion.task
        val visit = assertIs<Visit>(plan.event(assertNotNull(task.visitId)))

        plan.updateEvent(visit.copy(date = CareDate(2026, 11, 24)))
        assertTrue(actions.visitHasChanged)
        assertFalse(actions.confirm())

        plan.updateEvent(visit.copy(status = VisitStatus.COMPLETED))
        assertTrue(actions.visitHasChanged)
        assertFalse(actions.confirm())
        assertNull(plan.event(task.id))
        assertEquals(0, storage.writes)
    }

    @Test
    fun failedStorageWriteLeavesThePlanAndPreviousActionUnchanged() {
        val task = DemoFamily.recoveryQuestion.task
        val plan = DemoFamily.createPlan()
        val previousActionId = "another-confirmed-action"
        val storage = MemoryStorage(value = previousActionId, failWritingId = task.id)
        val actions = AssistantActions(plan, storage)

        assertFalse(actions.confirm())
        assertFalse(actions.confirmed)
        assertNotNull(actions.errorMessage)
        assertFalse(plan.eventsOn(task.date).any { it.id == task.id })
        assertEquals(previousActionId, storage.value)
        assertFalse(AssistantActions(DemoFamily.createPlan(), storage).confirmed)
    }

    @Test
    fun conflictingPlanTaskClearsAnUncommittedAction() {
        val task = DemoFamily.recoveryQuestion.task
        val existingTask = task.copy(title = "Keep this existing follow-up")
        val plan = DemoFamily.createPlan()
        plan.addEvent(existingTask)
        val storage = MemoryStorage()
        val actions = AssistantActions(plan, storage)

        assertFalse(actions.confirm())
        assertFalse(actions.confirmed)
        assertEquals(listOf(existingTask), plan.eventsOn(task.date).filter { it.id == task.id })
        assertNull(storage.value)
    }

    @Test
    fun legacyAddToTodayMigratesAndKeepsLaterCalendarEdits() {
        val original = DemoFamily.recoveryQuestion.task
        val legacy = MemoryStorage(original.id)
        val events = object : CarePlanEventStore {
            private var value: String? = null

            override fun load() = value

            override fun save(value: String) {
                this.value = value
            }
        }
        val firstPlan = DemoFamily.createPlan()
        val firstSession = CareSession(firstPlan, events)
        assertTrue(AssistantActions(firstPlan, legacy, firstSession).confirmed)
        assertEquals(original, firstSession.event(original.id))

        val edited = original.copy(title = "Bring my updated recovery notes", date = CareDate(2026, 11, 24))
        firstSession.saveTask(edited)
        val reopenedPlan = DemoFamily.createPlan()
        val reopenedSession = CareSession(reopenedPlan, events)
        val reopened = AssistantActions(reopenedPlan, legacy, reopenedSession)
        assertTrue(reopened.confirm())
        assertEquals(edited, reopened.task)
        assertEquals(edited, reopenedSession.event(original.id))
        assertEquals(0, legacy.writes)
    }

    private class MemoryStorage(
        var value: String? = null,
        private val failWritingId: String? = null,
    ) : AssistantActionStorage {
        var writes = 0
            private set

        override fun read(): String? = value

        override fun write(actionId: String?) {
            value = actionId
            writes++
            check(failWritingId == null || actionId != failWritingId) { "Storage write failed" }
        }
    }
}
