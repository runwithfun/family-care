package com.runwithfun.familycare.ui.assistant

import com.runwithfun.familycare.demo.DemoFamily
import com.runwithfun.familycare.domain.CareDate
import com.runwithfun.familycare.domain.OriginalContent
import com.runwithfun.familycare.domain.Task
import com.runwithfun.familycare.domain.TaskStatus
import com.runwithfun.familycare.records.DocumentFileStore
import com.runwithfun.familycare.records.DocumentReviewStore
import com.runwithfun.familycare.records.InMemoryRecordsLocalStore
import com.runwithfun.familycare.records.RecordsController
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

class AssistantConversationTest {
    @Test
    fun sourceAndVisitStayWithTheSelectedPerson() {
        val (chat, _, _) = Stores().launch()
        val recovery = chat.visits.single()

        assertEquals(DemoFamily.motherId, chat.personId)
        chat.selectVisit(recovery.id)
        chat.send("Find the postpartum note")
        val citation = chat.latestAnswer().citations.single()
        val original = assertNotNull(chat.openOriginal(citation))
        assertEquals(DemoFamily.motherId, citation.personId)
        assertEquals(DemoFamily.recoveryDocumentId, citation.documentId)
        assertEquals(DemoFamily.recoveryNoteDate, citation.documentDate)
        assertEquals(original.fileName, citation.fileName)
        assertContains(assertNotNull(original.textPreview), citation.quote)

        chat.selectPerson(DemoFamily.childId)
        chat.selectVisit(recovery.id)
        assertEquals(DemoFamily.childId, chat.personId)
        assertNull(chat.visitId)
        assertTrue(chat.visits.all { it.personId == DemoFamily.childId })
        assertNull(chat.openOriginal(citation))
        chat.send("Find Aoife's postpartum note")
        assertTrue(chat.latestAnswer().citations.isEmpty())
        chat.selectPerson(DemoFamily.motherId)
        assertEquals(citation, chat.latestAnswer().citations.single())
    }

    @Test
    fun missingImmunizationDateRemainsUnknownAfterReviewAndQuestionSave() {
        val (chat, session, records) = Stores().launch()
        chat.selectPerson(DemoFamily.childId)
        chat.send("Find the missing immunization date")
        assertIs<AssistantReply.ImportRecord>(chat.turns.single().reply)
        chat.importDemoRecord()
        assertContains(chat.latestAnswer().text.lowercase(), "unknown")
        assertTrue(records.leaveUnresolved(DemoFamily.immunizationDocumentId, DemoFamily.administrationDateField))
        val recordedBefore = session.plan.recordedImmunizations.toList()

        chat.send("Find the missing immunization date")
        val answer = chat.latestAnswer()
        val citation = answer.citations.single()
        assertContains(answer.text, "issued this copy on 2026-11-19")
        assertContains(answer.text, "administration date is unknown")
        assertContains(answer.text, "This copy includes no vaccination entries")
        assertEquals(DemoFamily.immunizationDocumentId, citation.documentId)
        assertEquals(DemoFamily.childId, citation.personId)
        assertEquals(DemoFamily.immunizationDocumentDate, citation.documentDate)
        assertNotNull(chat.openOriginal(citation))

        chat.selectVisit(chat.visits.single().id)
        chat.send("Draft a question for my clinician")
        val proposal = assertNotNull(chat.proposal)
        assertEquals(ConfirmQuestionResult.SAVED, chat.confirm(proposal.id, proposal.revision))
        val field = assertNotNull(records.document(citation.documentId))
            .fields.single { it.label == DemoFamily.administrationDateField }
        assertNull(field.proposal)
        assertNull(assertNotNull(field.review).value)
        assertEquals(recordedBefore, session.plan.recordedImmunizations)
        assertTrue(session.plan.recordedImmunizations.isEmpty())
    }

    @Test
    fun originalInfantRecordStaysCitableAfterAProfileEdit() {
        val stores = Stores()
        val (chat, _, _) = stores.launch()
        chat.selectPerson(DemoFamily.childId)
        chat.importDemoRecord()
        chat.send("Find the original record")
        val citation = chat.latestAnswer().citations.single()

        val (reopened, _, _) = stores.launch(childName = "Maya Rose")
        reopened.selectPerson(DemoFamily.childId)
        reopened.send("Find the original record")
        assertEquals(citation, reopened.latestAnswer().citations.single())
        assertNotNull(reopened.openOriginal(citation))
    }

    @Test
    fun confirmingAnOldRevisionOrMovedVisitLeavesThePlanUntouched() {
        val (chat, session, _) = Stores().launch()
        val visit = chat.visits.single()
        chat.send("When is my moved postpartum visit?")
        assertEquals(listOf(visit), assertIs<AssistantReply.ChooseVisit>(chat.turns.single().reply).visits)
        chat.selectVisit(visit.id)
        assertContains(chat.latestAnswer().text, DemoFamily.recoveryVisitDate.toString())
        chat.send("Draft a question for my clinician")
        val preview = assertNotNull(chat.proposal)
        chat.editProposal("Which recovery changes should I bring up?", preview.dateText)
        val edited = assertNotNull(chat.proposal)

        assertEquals(preview.id, edited.id)
        assertTrue(edited.revision > preview.revision)
        assertEquals(ConfirmQuestionResult.STALE_PROPOSAL, chat.confirm(preview.id, preview.revision))
        assertNull(session.event(preview.id))

        session.saveVisit(visit.copy(date = CareDate(2026, 12, 2)))
        assertEquals(ConfirmQuestionResult.STALE_PROPOSAL, chat.confirm(edited.id, edited.revision))
        assertNull(session.event(edited.id))
        chat.refreshProposal()
        val refreshed = assertNotNull(chat.proposal)
        assertEquals("2026-12-02", refreshed.dateText)
        assertEquals(edited.title, refreshed.title)
        assertNull(session.event(refreshed.id))
        assertEquals(ConfirmQuestionResult.SAVED, chat.confirm(refreshed.id, refreshed.revision))
    }

    @Test
    fun changedOriginalRequiresReviewAndDiscardSurvivesRelaunch() {
        val stores = Stores()
        val (chat, session, _) = stores.launch()
        chat.selectVisit(chat.visits.single().id)
        chat.send("Draft a question for my clinician")
        val draft = assertNotNull(chat.proposal)
        val original = assertNotNull(session.plan.document(draft.citation.documentId)).original
        val text = assertIs<OriginalContent.Text>(original.content).value
        session.plan.setDocumentOriginal(
            draft.citation.documentId,
            original.copy(content = OriginalContent.Text("$text\nThe clinic updated this copy.")),
        )

        assertNull(chat.openOriginal(draft.citation))
        assertEquals(ConfirmQuestionResult.STALE_PROPOSAL, chat.confirm(draft.id, draft.revision))
        assertNull(session.event(draft.id))
        chat.refreshProposal()
        val updated = assertNotNull(chat.proposal)
        assertNotNull(chat.openOriginal(updated.citation))
        chat.discardProposal()
        val (reopened, reopenedSession, _) = stores.launch()
        assertNull(reopened.proposal)
        assertNull(reopenedSession.event(draft.id))
    }

    @Test
    fun relaunchAndRepeatedConfirmationKeepLaterManualEdits() {
        val stores = Stores()
        val (firstChat, firstSession, _) = stores.launch()
        firstChat.selectVisit(firstChat.visits.single().id)
        firstChat.send("Draft a question for my clinician")
        val draft = assertNotNull(firstChat.proposal)
        firstChat.editProposal("Can we review my notes from this week?", DemoFamily.today.toString())
        val editedDraft = assertNotNull(firstChat.proposal)
        assertEquals(draft.id, editedDraft.id)
        assertNull(firstSession.event(draft.id))

        val (reopenedChat, reopenedSession, _) = stores.launch()
        val reopenedDraft = assertNotNull(reopenedChat.proposal)
        assertEquals(editedDraft, reopenedDraft)
        assertEquals(ConfirmQuestionResult.SAVED, reopenedChat.confirm(reopenedDraft.id, reopenedDraft.revision))
        val saved = assertIs<Task>(reopenedSession.event(reopenedDraft.id))
        assertEquals(editedDraft.title, saved.title)
        assertEquals(ConfirmQuestionResult.ALREADY_SAVED, reopenedChat.confirm(reopenedDraft.id, reopenedDraft.revision))

        val manualEdit = saved.copy(
            title = "Discuss my notes and ask for the appointment summary",
            date = CareDate(2026, 11, 30),
            status = TaskStatus.DONE,
        )
        reopenedSession.saveTask(manualEdit)
        val (afterEditChat, afterEditSession, _) = stores.launch()

        assertEquals(ConfirmQuestionResult.ALREADY_SAVED, afterEditChat.confirm(reopenedDraft.id, reopenedDraft.revision))
        assertEquals(manualEdit, afterEditChat.savedTask)
        assertEquals(manualEdit, afterEditSession.event(manualEdit.id))
        assertEquals(listOf(manualEdit), afterEditSession.eventsOn(manualEdit.date).filter { it.id == manualEdit.id })
        assertTrue(afterEditSession.eventsOn(saved.date).none { it.id == manualEdit.id })

        afterEditChat.selectVisit(afterEditChat.visits.single().id)
        afterEditChat.send("Draft a question for my clinician")
        val newer = assertNotNull(afterEditChat.proposal)
        assertEquals(ConfirmQuestionResult.SAVED, afterEditChat.confirm(newer.id, newer.revision))
        assertEquals(ConfirmQuestionResult.ALREADY_SAVED, afterEditChat.confirm(reopenedDraft.id, reopenedDraft.revision))
        assertEquals(newer.id, afterEditChat.savedTask?.id)
    }

    @Test
    fun relaunchRestoresBothTypedThreadsAndSelectionWithoutRepeatingActions() {
        val stores = Stores()
        val (chat, session, _) = stores.launch()
        chat.send("Find the postpartum note\nThanks\t🌿")
        val citation = chat.latestAnswer().citations.single()
        chat.send("Draft a question for my clinician")
        assertIs<AssistantReply.ChooseVisit>(chat.turns.last().reply)
        val motherTurns = chat.turns

        chat.selectPerson(DemoFamily.childId)
        val childVisit = chat.visits.single()
        chat.selectVisit(childVisit.id)
        chat.send("Find the original record")
        assertIs<AssistantReply.ImportRecord>(chat.turns.single().reply)
        val childTurns = chat.turns
        val originalAgenda = session.eventsOn(DemoFamily.today)

        val (restored, reopenedSession, records) = stores.launch()
        assertEquals(DemoFamily.childId, restored.personId)
        assertEquals(childVisit.id, restored.visitId)
        assertEquals(childTurns, restored.turns)
        assertFalse(records.isPdfImported())

        restored.selectPerson(DemoFamily.motherId)
        assertNull(restored.visitId)
        assertEquals(motherTurns, restored.turns)
        assertNotNull(restored.openOriginal(citation))
        assertNull(restored.proposal)
        assertEquals(originalAgenda, reopenedSession.eventsOn(DemoFamily.today))
    }

    @Test
    fun aLegacyProposalRestoresItsVisitAndAStoredClearKeepsItStale() {
        val stores = Stores()
        val (chat, _, _) = stores.launch()
        val visit = chat.visits.single()
        chat.selectVisit(visit.id)
        chat.send("Draft a question for my clinician")
        val draft = assertNotNull(chat.proposal)
        stores.removeHistory(DemoFamily.motherId)

        val (migrated, _, _) = stores.launch()
        assertEquals(visit.id, migrated.visitId)
        assertEquals(draft, migrated.proposal)
        assertTrue(migrated.turns.isEmpty())
        migrated.selectVisit(null)

        val (restored, session, _) = stores.launch()
        assertNull(restored.visitId)
        assertTrue(restored.proposalNeedsReview)
        assertEquals(ConfirmQuestionResult.STALE_PROPOSAL, restored.confirm(draft.id, draft.revision))
        assertNull(session.event(draft.id))
    }

    private fun AssistantConversation.latestAnswer(): AssistantReply.Answer =
        assertIs<AssistantReply.Answer>(turns.last().reply)

    private class Stores {
        private val recordsStore = InMemoryRecordsLocalStore()
        private val eventsStore = object : CarePlanEventStore {
            private var value: String? = null

            override fun load() = value

            override fun save(value: String) {
                this.value = value
            }
        }

        fun removeHistory(personId: String) = recordsStore.write(assistantHistoryKey(personId), null)

        fun launch(childName: String = "Maya"): Triple<AssistantConversation, CareSession, RecordsController> {
            val plan = DemoFamily.createPlan(childName)
            val session = CareSession(plan, eventsStore)
            val records = RecordsController(
                plan,
                DocumentFileStore(recordsStore),
                DocumentReviewStore(recordsStore),
                pdfBodyText = DemoFamily.immunizationPdfBody(childName),
                saveTask = session::saveTask,
            )
            return Triple(AssistantConversation(session, records, recordsStore), session, records)
        }
    }
}
