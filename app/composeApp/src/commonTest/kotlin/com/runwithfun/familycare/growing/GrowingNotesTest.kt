package com.runwithfun.familycare.growing

import com.runwithfun.familycare.domain.AgeStage
import com.runwithfun.familycare.domain.CareDate
import com.runwithfun.familycare.domain.CarePlan
import com.runwithfun.familycare.domain.FamilyMember
import com.runwithfun.familycare.domain.FamilyRole
import com.runwithfun.familycare.domain.Task
import com.runwithfun.familycare.domain.Visit
import com.runwithfun.familycare.domain.VisitStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class GrowingNotesTest {
    private val initialVisitDay = CareDate(2026, 11, 23)
    private val stage = AgeStage.INFANT_2_4_MONTHS
    private val child = "child"

    @Test
    fun restoresOriginalTaskAndKeepsUnlinkedNoteUnlinkedAfterVisitMoves() {
        val storage = MemoryStorage()
        val initialPlan = plan(initialVisitDay)
        val notes = GrowingNotes(initialPlan, storage)
        val linked = notes.save(child, stage, "  Café\nA note with a tab:\tfinished.  ", initialVisitDay)
        val unlinked = notes.save(child, stage, "Discuss later", CareDate(2026, 11, 24))
        val originalTask = assertIs<Task>(initialPlan.event("discuss-${linked.id}"))
        assertNull(unlinked.forVisitId)

        val reopenedPlan = plan(CareDate(2026, 11, 25))
        val reopenedNotes = GrowingNotes(reopenedPlan, storage)
        assertEquals(listOf(linked, unlinked), reopenedNotes.observations(child, stage))
        assertEquals(originalTask, reopenedPlan.event(originalTask.id))
        assertEquals(listOf(originalTask), reopenedPlan.eventsOn(initialVisitDay, child))
        assertNull(reopenedPlan.event("discuss-${unlinked.id}"))

        val another = reopenedNotes.save(child, stage, linked.note, initialVisitDay)
        assertNotEquals(linked.id, another.id)
        assertEquals(listOf(linked, unlinked, another).toSet(), reopenedNotes.observations(child).toSet())
    }

    @Test
    fun failedStorageWriteRestoresPreviousPayloadAndPublishesNeitherNoteNorTask() {
        val storage = MemoryStorage()
        val currentPlan = plan(initialVisitDay)
        val notes = GrowingNotes(currentPlan, storage)
        val previous = notes.save(child, stage, "Already saved", initialVisitDay)
        val previousPayload = storage.value
        val previousEvents = currentPlan.eventsOn(initialVisitDay)
        val failure = IllegalStateException("Simulated failure after changing the stored value")
        storage.failNextWrite = failure

        assertSame(failure, assertFailsWith<IllegalStateException> {
            notes.save(child, stage, "Do not publish", initialVisitDay)
        })
        assertEquals(previousPayload, storage.value)
        assertEquals(listOf(previous), notes.observations(child))
        assertEquals(listOf(previous), currentPlan.observations(child))
        assertEquals(previousEvents, currentPlan.eventsOn(initialVisitDay))
        assertEquals(listOf(previous), GrowingNotes(plan(initialVisitDay), storage).observations(child))
    }

    private fun plan(visitDay: CareDate) = CarePlan(
        people = listOf(FamilyMember(child, "Sam", FamilyRole.CHILD)),
        events = listOf(Visit("visit", child, "Check-up", visitDay, "Example Clinic", VisitStatus.BOOKED)),
        documents = emptyList(),
        recordedImmunizations = emptyList(),
        immunizationReferences = emptyList(),
    )

    private class MemoryStorage : GrowingNotesStorage {
        var value: String? = null
        var failNextWrite: Exception? = null
        override fun read() = value

        override fun write(value: String?) {
            this.value = value
            failNextWrite?.let { error ->
                failNextWrite = null
                throw error
            }
        }
    }
}
