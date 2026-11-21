package com.runwithfun.familycare.ui.assistant

import com.runwithfun.familycare.demo.DemoFamily
import com.runwithfun.familycare.domain.CarePlan
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    private class MemoryStorage(var value: String? = null) : AssistantActionStorage {
        var writes = 0
            private set

        override fun read(): String? = value

        override fun write(actionId: String) {
            value = actionId
            writes++
        }
    }
}
