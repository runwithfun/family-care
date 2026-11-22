package com.runwithfun.familycare.domain

import com.runwithfun.familycare.demo.DemoFamily
import com.runwithfun.familycare.records.DocumentFileStore
import com.runwithfun.familycare.records.DocumentReviewStore
import com.runwithfun.familycare.records.InMemoryRecordsLocalStore
import com.runwithfun.familycare.records.RecordsController
import com.runwithfun.familycare.records.RecordsLocalStore
import com.runwithfun.familycare.records.buildSyntheticClinicPdf
import com.runwithfun.familycare.records.containsLatin1
import com.runwithfun.familycare.records.isPdfHeader
import com.runwithfun.familycare.records.validateReviewInput
import com.runwithfun.familycare.records.FieldValidationResult
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecordsTest {
    @Test
    fun syntheticPdfImportStoresAndReopensOriginalOnDevice() {
        val store = InMemoryRecordsLocalStore()
        val files = DocumentFileStore(store)
        val reviews = DocumentReviewStore(store)
        val birthDate = CareDate(2026, 8, 23)
        val plan = DemoFamily.createPlan("Nora", birthDate)
        val records = RecordsController(plan, files, reviews, DemoFamily.immunizationPdfBody("Nora", birthDate))

        assertFalse(records.isPdfImported())
        assertTrue(records.importSyntheticImmunizationPdf())

        val bytes = files.readBytes(DemoFamily.immunizationPdfStorageKey)
        assertNotNull(bytes)
        assertTrue(bytes.isPdfHeader())
        assertTrue(bytes.containsLatin1(DemoFamily.immunizationDocumentDateExcerpt))
        assertTrue(bytes.containsLatin1(DemoFamily.missingImmunizationExcerpt))
        assertTrue(bytes.containsLatin1(DemoFamily.noVaccinationEntriesExcerpt))
        assertTrue(bytes.containsLatin1("Date of birth: $birthDate"))

        val opened = records.reopenOriginal(DemoFamily.immunizationDocumentId)
        assertNotNull(opened)
        assertTrue(opened.isPdf)
        assertContentEquals(bytes, opened.pdfBytes)
        assertNull(opened.textPreview)
        assertEquals("demo-immunizations.pdf", opened.fileName)
        assertEquals(DemoFamily.immunizationPdfStorageKey, opened.storageKey)
        assertEquals("application/pdf", plan.document(DemoFamily.immunizationDocumentId)!!.original.mediaType)

        val updatedBody = DemoFamily.immunizationPdfBody("Nora", birthDate = null)
        assertTrue(updatedBody.contains("Date of birth: Not provided"))
        val updatedProfile = RecordsController(
            DemoFamily.createPlan("Nora", birthDate = null), files, reviews, updatedBody,
        )
        assertContentEquals(bytes, updatedProfile.reopenOriginal(DemoFamily.immunizationDocumentId)?.pdfBytes)

        val recovery = plan.document(DemoFamily.recoveryDocumentId)!!
        assertTrue(recovery.original.content is OriginalContent.Text)
        assertEquals(DemoFamily.recoveryNoteText, recovery.originalText())
    }

    @Test
    fun pdfCrossReferenceUsesByteOffsets() {
        val bytes = buildSyntheticClinicPdf("Nóra")
        val xrefOffset = bytes.decodeToString().substringAfterLast("startxref\n").lineSequence().first().toInt()
        assertEquals("xref\n", bytes.decodeToString(xrefOffset, xrefOffset + 5))
    }

    @Test
    fun earlierSyntheticPdfAndReviewDoNotAppearAsTheCurrentExtract() {
        val documentId = DemoFamily.immunizationDocumentId
        val store = InMemoryRecordsLocalStore(
            mapOf(DocumentReviewStore.ReviewsKey to "$documentId\tExample immunization A date\t2026-11-02"),
        )
        val files = DocumentFileStore(store)
        val oldKey = "documents/demo-immunizations.pdf"
        val oldPdf = buildSyntheticClinicPdf("Example immunization A: 2026-11-02")
        files.writeBytes(oldKey, oldPdf)
        val plan = DemoFamily.createPlan()
        val records = RecordsController(plan, files, DocumentReviewStore(store))

        assertFalse(records.isPdfImported())
        assertTrue(records.importSyntheticImmunizationPdf())
        val current = assertNotNull(records.reopenOriginal(documentId)?.pdfBytes)
        assertTrue(current.containsLatin1(DemoFamily.noVaccinationEntriesExcerpt))
        assertFalse(current.containsLatin1("Example immunization A"))
        assertContentEquals(oldPdf, files.readBytes(oldKey))
        assertTrue(assertNotNull(records.document(documentId)).fields.all { it.review == null })
        assertTrue(plan.recordedImmunizations.isEmpty())
    }

    @Test
    fun reviewsPersistAcrossNewPlanAndStoreReloadAndDetailObservesSave() {
        val shared = InMemoryRecordsLocalStore()
        val firstPlan = DemoFamily.createPlan()
        val first = RecordsController(firstPlan, DocumentFileStore(shared), DocumentReviewStore(shared))

        assertTrue(first.leaveUnresolved(DemoFamily.immunizationDocumentId, DemoFamily.administrationDateField))
        assertTrue(first.saveCorrection(DemoFamily.immunizationDocumentId, DemoFamily.immunizationDocumentDateField, "2026-11-02"))
        val afterSave = first.document(DemoFamily.immunizationDocumentId)!!
        assertEquals("2026-11-02", afterSave.fields.single { it.label == DemoFamily.immunizationDocumentDateField }.review?.value)
        assertTrue(afterSave.fields.single { it.label == DemoFamily.administrationDateField }.isExplicitlyUnresolved())
        assertFalse(afterSave.fields.single { it.label == DemoFamily.administrationDateField }.isUntouched())
        assertEquals(2, first.revision)

        val secondPlan = DemoFamily.createPlan()
        val reloaded = RecordsController(
            secondPlan,
            DocumentFileStore(InMemoryRecordsLocalStore(shared.snapshot())),
            DocumentReviewStore(InMemoryRecordsLocalStore(shared.snapshot())),
        )
        val restored = reloaded.document(DemoFamily.immunizationDocumentId)!!
        val fieldA = restored.fields.single { it.label == DemoFamily.immunizationDocumentDateField }
        val fieldB = restored.fields.single { it.label == DemoFamily.administrationDateField }
        assertEquals("2026-11-02", fieldA.review?.value)
        assertTrue(fieldB.isExplicitlyUnresolved())
        assertFalse(fieldB.isUntouched())
        assertTrue(
            reloaded.fieldStatusLabel(DemoFamily.immunizationDocumentId, DemoFamily.administrationDateField)
                .contains("Explicitly unresolved"),
        )
        assertTrue(
            first.fieldStatusLabel(DemoFamily.immunizationDocumentId, DemoFamily.immunizationDocumentDateField)
                .contains("Confirmed"),
        )
    }

    @Test
    fun untouchedProposalDiffersFromExplicitUnresolved() {
        val plan = DemoFamily.createPlan()
        val fieldA = plan.document(DemoFamily.immunizationDocumentId)!!
            .fields.single { it.label == DemoFamily.immunizationDocumentDateField }
        assertTrue(fieldA.isUntouched())
        assertNotNull(fieldA.proposal)
        assertFalse(fieldA.isExplicitlyUnresolved())

        plan.reviewField(DemoFamily.immunizationDocumentId, DemoFamily.immunizationDocumentDateField, FieldReview(null))
        val unresolved = plan.document(DemoFamily.immunizationDocumentId)!!
            .fields.single { it.label == DemoFamily.immunizationDocumentDateField }
        assertTrue(unresolved.isExplicitlyUnresolved())
        assertFalse(unresolved.isUntouched())
    }

    @Test
    fun reviewFailurePreservesSavedAndVisibleReview() {
        val backing = InMemoryRecordsLocalStore()
        var failNextWrite = false
        val store = object : RecordsLocalStore {
            override fun read(key: String): String? = backing.read(key)

            override fun write(key: String, value: String?) {
                backing.write(key, value)
                if (failNextWrite) {
                    failNextWrite = false
                    error("Test persistence failed after changing the value")
                }
            }
        }
        val plan = DemoFamily.createPlan()
        val records = RecordsController(plan, DocumentFileStore(store), DocumentReviewStore(store))
        val documentId = DemoFamily.immunizationDocumentId
        val fieldLabel = DemoFamily.immunizationDocumentDateField
        assertTrue(records.saveCorrection(documentId, fieldLabel, "2026-11-02"))
        val goodSnapshot = backing.snapshot()

        failNextWrite = true
        assertFalse(records.leaveUnresolved(documentId, fieldLabel))
        assertEquals("2026-11-02", records.document(documentId)!!.fields.first().review?.value)
        assertEquals(goodSnapshot, backing.snapshot())
        assertEquals(1, records.revision)

        assertFalse(records.leaveUnresolved(documentId, "Unknown field"))
        assertEquals(goodSnapshot, backing.snapshot())
        assertEquals(1, records.revision)
    }

    @Test
    fun typedDateAndTimeAreValidatedBeforeReview() {
        assertTrue(validateReviewInput("Follow-up time", "14:30") is FieldValidationResult.Ok)
        assertTrue(validateReviewInput("Follow-up time", "25:90") is FieldValidationResult.Invalid)
        assertTrue(validateReviewInput(DemoFamily.immunizationDocumentDateField, "2026-11-02") is FieldValidationResult.Ok)
        assertTrue(validateReviewInput(DemoFamily.immunizationDocumentDateField, "11/02/2026") is FieldValidationResult.Invalid)

        val store = InMemoryRecordsLocalStore()
        val records = RecordsController(DemoFamily.createPlan(), DocumentFileStore(store), DocumentReviewStore(store))
        assertFalse(records.saveCorrection(DemoFamily.immunizationDocumentId, DemoFamily.immunizationDocumentDateField, "not-a-date"))
        assertNotNull(records.lastErrorDetail)
        assertNull(
            records.document(DemoFamily.immunizationDocumentId)!!
                .fields.single { it.label == DemoFamily.immunizationDocumentDateField }.review,
        )
    }

    @Test
    fun editingAdministrationFieldCannotEstablishADose() {
        val store = InMemoryRecordsLocalStore()
        val plan = DemoFamily.createPlan()
        val records = RecordsController(plan, DocumentFileStore(store), DocumentReviewStore(store))
        assertTrue(records.importSyntheticImmunizationPdf())
        val documentId = DemoFamily.immunizationDocumentId
        val original = assertNotNull(records.reopenOriginal(documentId)?.pdfBytes)
        assertTrue(records.saveCorrection(documentId, DemoFamily.administrationDateField, "2026-11-05"))
        assertEquals(
            "2026-11-05",
            records.document(documentId)!!.fields.single { it.label == DemoFamily.administrationDateField }.review?.value,
        )
        assertContentEquals(original, records.reopenOriginal(documentId)?.pdfBytes)
        assertTrue(original.containsLatin1(DemoFamily.missingImmunizationExcerpt))
        assertTrue(plan.recordedImmunizations.isEmpty())
    }

    @Test
    fun openRequestTasksUseSourceDocumentAndStateWithoutDuplicating() {
        val plan = DemoFamily.createPlan()
        val existing = plan.openTasksForDocument(DemoFamily.immunizationDocumentId).single()
        assertEquals(TaskStatus.OPEN, existing.status)

        val linked = plan.linkOpenRequestTask(
            Task(
                id = "another-request",
                personId = DemoFamily.childId,
                title = "Request a copy of the immunization record",
                date = CareDate(2026, 12, 1),
                sourceDocumentId = DemoFamily.immunizationDocumentId,
            ),
        )
        assertEquals(existing.id, linked.id)
        assertEquals(1, plan.openTasksForDocument(DemoFamily.immunizationDocumentId).size)

        val store = InMemoryRecordsLocalStore()
        val records = RecordsController(plan, DocumentFileStore(store), DocumentReviewStore(store))
        assertEquals(existing.id, records.providerRequest(DemoFamily.immunizationDocumentId)?.id)
        assertEquals(existing.id, records.ensureProviderRequest(DemoFamily.immunizationDocumentId)?.id)
    }

    @Test
    fun recoveryNoteFollowUpsCannotBecomeImmunizationRequests() {
        val plan = DemoFamily.createPlan()
        val store = InMemoryRecordsLocalStore()
        val records = RecordsController(plan, DocumentFileStore(store), DocumentReviewStore(store))
        val documentId = DemoFamily.recoveryDocumentId
        val existing = plan.openTasksForDocument(documentId)

        assertNotNull(plan.document(documentId)?.providerFollowUpMessage())
        assertNull(records.providerRequest(documentId))
        assertNull(records.ensureProviderRequest(documentId))
        assertEquals(existing, records.openRequestTasks(documentId))
        assertNull(plan.event("request-$documentId"))
    }

    @Test
    fun textReviewsSurviveDelimitersAndLegacyMissingRows() {
        val documentId = DemoFamily.recoveryDocumentId
        val store = InMemoryRecordsLocalStore(
            mapOf(DocumentReviewStore.ReviewsKey to "$documentId\tFollow-up time\t__MISSING__"),
        )
        val first = RecordsController(DemoFamily.createPlan(), DocumentFileStore(store), DocumentReviewStore(store))
        val corrected = "__MISSING__\tCafé"
        assertTrue(first.saveCorrection(documentId, "Clinician name", corrected))

        val reopened = RecordsController(DemoFamily.createPlan(), DocumentFileStore(store), DocumentReviewStore(store))
        val fields = assertNotNull(reopened.document(documentId)).fields
        assertEquals(corrected, fields.single { it.label == "Clinician name" }.review?.value)
        assertTrue(fields.single { it.label == "Follow-up time" }.isExplicitlyUnresolved())
    }

    @Test
    fun incompleteRecordDoesNotGuessVaccineStatus() {
        val plan = DemoFamily.createPlan()
        val document = plan.document(DemoFamily.immunizationDocumentId)!!
        assertTrue(document.hasGaps())
        assertFalse(document.isComplete())
        val followUp = document.providerFollowUpMessage()
        assertNotNull(followUp)
        assertTrue(followUp.contains("Do not assume", ignoreCase = true))
        assertTrue(buildSyntheticClinicPdf(DemoFamily.immunizationPdfBody()).isPdfHeader())
    }
}
