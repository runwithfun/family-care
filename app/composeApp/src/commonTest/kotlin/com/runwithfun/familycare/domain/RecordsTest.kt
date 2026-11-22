package com.runwithfun.familycare.domain

import com.runwithfun.familycare.demo.DemoFamily
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecordsTest {
    @Test
    fun originalClinicPdfRemainsRetrievableBesideExtractedFields() {
        val plan = DemoFamily.createPlan()
        val document = plan.document(DemoFamily.immunizationDocumentId)

        assertNotNull(document)
        assertEquals("demo-immunizations.pdf", document.original.fileName)
        assertEquals("application/pdf", document.original.mediaType)
        assertEquals(DemoFamily.immunizationPdfText, document.originalText())
        assertEquals("demo-immunizations.pdf", document.originalRetrievalLabel())
        assertTrue(document.fields.any { it.label.contains("Example immunization A") })
    }

    @Test
    fun completeAndIncompleteDocumentsAreDistinguishedWithoutGuessingVaccines() {
        val plan = DemoFamily.createPlan()
        val incompleteImmunization = plan.document(DemoFamily.immunizationDocumentId)!!
        val incompleteVisit = plan.document(DemoFamily.recoveryDocumentId)!!

        assertTrue(incompleteImmunization.hasGaps())
        assertFalse(incompleteImmunization.isComplete())
        assertTrue(incompleteVisit.hasGaps())

        val confirmedA = incompleteImmunization.fields.single { it.label.contains("Example immunization A") }
        assertTrue(confirmedA.isConfirmed())
        assertEquals("2026-11-02", confirmedA.shownValue())

        val missingB = incompleteImmunization.fields.single { it.label.contains("Example immunization B") }
        assertFalse(missingB.isConfirmed())
        assertNull(missingB.proposal)
        assertNull(missingB.shownValue())

        assertEquals(
            listOf("Example immunization A"),
            plan.recordedImmunizations.map { it.name },
        )
        assertTrue(plan.recordedImmunizations.none { it.name.contains("B") })

        val followUp = incompleteImmunization.providerFollowUpMessage()
        assertNotNull(followUp)
        assertTrue(followUp.contains("Do not assume", ignoreCase = true))
    }

    @Test
    fun parentCorrectionsPersistAndUnresolvedFieldsStayOpen() {
        val plan = DemoFamily.createPlan()
        val original = plan.document(DemoFamily.recoveryDocumentId)!!.originalText()

        plan.reviewField(DemoFamily.recoveryDocumentId, "Clinician name", FieldReview("Dr. Example"))
        val afterConfirm = plan.document(DemoFamily.recoveryDocumentId)!!
        assertEquals("Dr. Example", afterConfirm.fields.single { it.label == "Clinician name" }.shownValue())
        assertEquals(original, afterConfirm.originalText())
        assertTrue(afterConfirm.isComplete())

        plan.reviewField(DemoFamily.recoveryDocumentId, "Clinician name", FieldReview(null))
        val afterUnresolved = plan.document(DemoFamily.recoveryDocumentId)!!
        assertFalse(afterUnresolved.isComplete())
        assertNull(afterUnresolved.fields.single { it.label == "Clinician name" }.review?.value)
        assertEquals(original, afterUnresolved.originalText())
    }

    @Test
    fun immunizationsReferencesAndAppointmentsStaySeparate() {
        val plan = DemoFamily.createPlan()

        assertEquals(1, plan.recordedImmunizations.size)
        assertEquals(2, plan.immunizationReferences.size)
        assertEquals(
            listOf("Well-baby visit", "Recovery follow-up"),
            plan.appointments().map { it.title },
        )
        assertTrue(plan.documents.any { it.kind == DocumentKind.IMMUNIZATION_RECORD })
        assertTrue(
            plan.eventsOn(DemoFamily.today, DemoFamily.childId)
                .any { it.title.contains("Request a copy of the immunization record") },
        )
    }
}
