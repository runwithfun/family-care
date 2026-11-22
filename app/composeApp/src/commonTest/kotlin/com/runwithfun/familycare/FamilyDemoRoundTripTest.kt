package com.runwithfun.familycare

import com.runwithfun.familycare.demo.DemoFamily
import com.runwithfun.familycare.domain.AgeStage
import com.runwithfun.familycare.domain.CareDate
import com.runwithfun.familycare.domain.Task
import com.runwithfun.familycare.domain.Visit
import com.runwithfun.familycare.domain.isExplicitlyUnresolved
import com.runwithfun.familycare.growing.GrowingNotes
import com.runwithfun.familycare.growing.GrowingNotesStorage
import com.runwithfun.familycare.records.DocumentFileStore
import com.runwithfun.familycare.records.DocumentReviewStore
import com.runwithfun.familycare.records.InMemoryRecordsLocalStore
import com.runwithfun.familycare.records.RecordsController
import com.runwithfun.familycare.session.CarePlanEventStore
import com.runwithfun.familycare.session.CareSession
import com.runwithfun.familycare.ui.assistant.AssistantActionStorage
import com.runwithfun.familycare.ui.assistant.AssistantActions
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FamilyDemoRoundTripTest {
    @Test
    fun relaunchRestoresTheSharedPlanWithTheOriginalRecordAndAnEditedAssistantTask() {
        val assistantStore = object : AssistantActionStorage {
            private var confirmedId: String? = null
            override fun read(): String? = confirmedId

            override fun write(actionId: String?) {
                confirmedId = actionId
            }
        }
        val noteStore = MemoryStringStore()
        val calendarStore = MemoryStringStore()
        val recordsStore = InMemoryRecordsLocalStore()
        val plan = DemoFamily.createPlan()

        val assistant = AssistantActions(plan, assistantStore)
        val notes = GrowingNotes(plan, noteStore)
        val calendar = CareSession(plan, calendarStore)
        val records = RecordsController(plan, DocumentFileStore(recordsStore), DocumentReviewStore(recordsStore))
        assertTrue(assistant.confirm())

        val assistantTask = assistant.task.copy(title = "Bring my recovery questions", date = CareDate(2026, 11, 24))
        calendar.saveTask(assistantTask)
        val visit = assertIs<Visit>(calendar.event(DemoFamily.childVisitId))
        val movedVisit = visit.copy(date = CareDate(2026, 11, 25))
        calendar.saveVisit(movedVisit)
        val note = notes.save(
            DemoFamily.childId,
            AgeStage.INFANT_2_4_MONTHS,
            "Turns toward a familiar voice.",
            DemoFamily.today,
        )
        assertEquals(movedVisit.id, note.forVisitId)

        val documentId = DemoFamily.immunizationDocumentId
        val missingField = DemoFamily.immunizationBField
        assertTrue(records.importSyntheticImmunizationPdf())
        assertTrue(records.leaveUnresolved(documentId, missingField))
        val original = assertNotNull(records.reopenOriginal(documentId)?.pdfBytes)

        val reopened = DemoFamily.createPlan()
        val reopenedAssistant = AssistantActions(reopened, assistantStore)
        val reopenedNotes = GrowingNotes(reopened, noteStore)
        val reopenedCalendar = CareSession(reopened, calendarStore)
        val reopenedRecords = RecordsController(
            reopened, DocumentFileStore(recordsStore), DocumentReviewStore(recordsStore),
        )

        assertTrue(reopenedAssistant.confirmed)
        assertTrue(reopenedAssistant.confirm())
        assertEquals(assistantTask, reopenedAssistant.task)
        assertEquals(listOf(assistantTask), reopenedCalendar.eventsOn(assistantTask.date, assistantTask.personId))
        assertEquals(movedVisit, reopenedCalendar.event(movedVisit.id))
        assertTrue(note in reopenedNotes.observations(DemoFamily.childId, note.stage))
        val linkedTask = assertIs<Task>(reopenedCalendar.event("discuss-${note.id}"))
        assertEquals(movedVisit.date, linkedTask.date)
        assertEquals(movedVisit.id, linkedTask.visitId)
        assertContentEquals(original, reopenedRecords.reopenOriginal(documentId)?.pdfBytes)
        val field = assertNotNull(reopenedRecords.document(documentId)?.fields?.single { it.label == missingField })
        assertTrue(field.isExplicitlyUnresolved())
    }

    private class MemoryStringStore : GrowingNotesStorage, CarePlanEventStore {
        private var value: String? = null
        override fun read(): String? = value
        override fun load(): String? = value
        override fun save(value: String) = write(value)

        override fun write(value: String?) {
            this.value = value
        }
    }
}
