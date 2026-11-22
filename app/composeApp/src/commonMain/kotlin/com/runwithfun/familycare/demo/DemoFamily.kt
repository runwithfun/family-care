package com.runwithfun.familycare.demo

import com.runwithfun.familycare.domain.AgeStage
import com.runwithfun.familycare.domain.AssistantProposal
import com.runwithfun.familycare.domain.CareDate
import com.runwithfun.familycare.domain.CareDocument
import com.runwithfun.familycare.domain.CarePlan
import com.runwithfun.familycare.domain.CareTime
import com.runwithfun.familycare.domain.DocumentField
import com.runwithfun.familycare.domain.DocumentKind
import com.runwithfun.familycare.domain.DocumentOriginal
import com.runwithfun.familycare.domain.ExtractedValue
import com.runwithfun.familycare.domain.FamilyMember
import com.runwithfun.familycare.domain.FamilyRole
import com.runwithfun.familycare.domain.FieldReview
import com.runwithfun.familycare.domain.GrowingObservation
import com.runwithfun.familycare.domain.ImmunizationReference
import com.runwithfun.familycare.domain.OriginalContent
import com.runwithfun.familycare.domain.ReviewedActivity
import com.runwithfun.familycare.domain.Task
import com.runwithfun.familycare.domain.Visit
import com.runwithfun.familycare.domain.VisitStatus

object DemoFamily {
    const val motherId = "demo-mother"
    const val childId = "demo-child"
    const val childVisitId = "demo-child-visit"
    const val childName = "Maya"
    const val motherName = "Aoife"
    const val clinicName = "Alderfen Family Clinic"
    val childBirthDate = CareDate(2026, 9, 28)
    val today = CareDate(2026, 11, 23)

    private const val recoveryVisitId = "demo-recovery-visit"
    val recoveryVisitDate = today
    val recoveryNoteDate = CareDate(2026, 11, 20)
    const val recoveryDocumentId = "demo-recovery-note"
    const val immunizationDocumentId = "demo-immunizations"
    // Earlier demo builds stored a different fictional PDF under the same display name.
    const val immunizationPdfStorageKey = "documents/demo-immunizations-extract-v2.pdf"
    const val immunizationDocumentDateField = "Document date (copy issued)"
    const val administrationDateField = "Administration date"
    val immunizationDocumentDate = CareDate(2026, 11, 19)
    val immunizationDocumentDateExcerpt = "Copy issued: $immunizationDocumentDate"

    const val recoveryObservation = "Aoife reports some soreness after longer walks."
    const val recoveryRescheduleExcerpt = "Follow-up moved from November 20 to November 23, 2026 at 14:30."

    /** Fictional original shared by Records and Assistant. */
    val recoveryNoteText =
        "$clinicName · Postpartum follow-up\n" +
            "Patient: $motherName\n" +
            "Note date: $recoveryNoteDate · Fictional demo record\n\n" +
            "$recoveryRescheduleExcerpt\n" +
            "$recoveryObservation\n" +
            "Bring any questions about your recovery."

    const val missingImmunizationExcerpt = "Administration date: not recorded on this copy."
    const val noVaccinationEntriesExcerpt = "No vaccination entries are included in this copy."

    fun immunizationPdfBody(
        childName: String = DemoFamily.childName,
        birthDate: CareDate? = childBirthDate,
    ): String =
        """
        $clinicName — Immunization record extract
        Fictional demo record
        Patient: $childName
        Date of birth: ${birthDate ?: "Not provided"}
        $immunizationDocumentDateExcerpt

        Vaccine name: not recorded on this copy.
        $missingImmunizationExcerpt
        $noVaccinationEntriesExcerpt
        """.trimIndent()

    val newbornObservation = GrowingObservation(
        id = "demo-obs-newborn-lift",
        personId = childId,
        stage = AgeStage.NEWBORN_0_2_MONTHS,
        note = "Holds head up briefly during supervised tummy time.",
        recordedOn = CareDate(2026, 10, 20),
    )

    val rattleObservation = GrowingObservation(
        id = "demo-obs-infant-reach",
        personId = childId,
        stage = AgeStage.NEWBORN_0_2_MONTHS,
        note = "Watched a soft rattle move from side to side.",
        recordedOn = CareDate(2026, 11, 18),
        forVisitId = childVisitId,
    )

    val tummyTimeActivity = ReviewedActivity(
        id = "demo-activity-tummy-time",
        stage = AgeStage.NEWBORN_0_2_MONTHS,
        title = "Supervised tummy time",
        summary = "Short practice on a firm surface. Stay with your baby the whole time.",
        steps = listOf(
            "Place your awake baby on their tummy on a firm floor mat and stay close.",
            "Get down to eye level and talk or sing to your baby.",
            "Keep the session short and stop when your baby needs a break.",
        ),
        sourceLabel = "American Academy of Pediatrics · Back to Sleep, Tummy to Play",
        sourceUrl = "https://www.healthychildren.org/English/ages-stages/baby/sleep/Pages/back-to-sleep-tummy-to-play.aspx",
    )

    fun reviewedActivity(stage: AgeStage): ReviewedActivity = when (stage) {
        AgeStage.NEWBORN_0_2_MONTHS -> tummyTimeActivity
        AgeStage.INFANT_2_4_MONTHS -> ReviewedActivity(
            id = "demo-activity-reach-and-grasp",
            stage = AgeStage.INFANT_2_4_MONTHS,
            title = "Reach and grasp play",
            summary = "Offer one baby-safe toy or clean cloth at a time.",
            steps = listOf(
                "Hold the toy or cloth near one hand and give your baby time to reach.",
                "Offer the same object near the other hand.",
                "Pause when your baby is ready for a break.",
            ),
            sourceLabel = "UNICEF Parenting · Learning activities for 2-month-olds",
            sourceUrl = "https://www.unicef.org/parenting/child-care/21-learning-activities-babies-and-toddlers",
        )
    }

    val recoveryQuestion = AssistantProposal(
        Task(
            id = "demo-recovery-question",
            personId = motherId,
            title = "Ask about recovery at today's visit",
            date = today,
            sourceDocumentId = recoveryDocumentId,
            visitId = recoveryVisitId,
        ),
    )

    fun createPlan(
        childName: String = DemoFamily.childName,
        birthDate: CareDate? = childBirthDate,
    ): CarePlan = CarePlan(
        people = listOf(
            FamilyMember(motherId, motherName, FamilyRole.MOTHER),
            FamilyMember(childId, childName, FamilyRole.CHILD, birthDate = birthDate),
        ),
        events = listOf(
            Visit(
                id = childVisitId,
                personId = childId,
                title = "Well-baby visit",
                date = today,
                time = CareTime(9, 30),
                provider = clinicName,
                status = VisitStatus.BOOKED,
            ),
            Visit(
                id = recoveryVisitId,
                personId = motherId,
                title = "Recovery follow-up",
                date = recoveryVisitDate,
                time = CareTime(14, 30),
                provider = clinicName,
                status = VisitStatus.BOOKED,
                sourceDocumentId = recoveryDocumentId,
            ),
            Task(
                id = "demo-request-record",
                personId = childId,
                title = "Request a copy of the immunization record",
                date = today,
                sourceDocumentId = immunizationDocumentId,
            ),
        ),
        documents = listOf(
            CareDocument(
                id = recoveryDocumentId,
                personId = motherId,
                kind = DocumentKind.VISIT_SUMMARY,
                original = DocumentOriginal(
                    fileName = "demo-recovery-note.txt",
                    mediaType = "text/plain",
                    content = OriginalContent.Text(recoveryNoteText),
                ),
                fields = listOf(
                    DocumentField(
                        label = "Follow-up time",
                        proposal = ExtractedValue("14:30", recoveryRescheduleExcerpt),
                        review = FieldReview("14:30"),
                    ),
                    DocumentField(label = "Clinician name"),
                ),
            ),
            CareDocument(
                id = immunizationDocumentId,
                personId = childId,
                kind = DocumentKind.IMMUNIZATION_RECORD,
                original = DocumentOriginal(
                    fileName = "demo-immunizations.pdf",
                    mediaType = "application/pdf",
                    content = OriginalContent.StoredFile(immunizationPdfStorageKey),
                ),
                fields = listOf(
                    DocumentField(
                        label = immunizationDocumentDateField,
                        proposal = ExtractedValue(
                            immunizationDocumentDate.toString(),
                            immunizationDocumentDateExcerpt,
                            page = 1,
                        ),
                    ),
                    DocumentField(label = administrationDateField),
                    DocumentField(label = "Vaccine name"),
                ),
            ),
        ),
        recordedImmunizations = emptyList(),
        immunizationReferences = listOf(
            ImmunizationReference(
                "US",
                "CDC",
                "https://www.cdc.gov/vaccines/hcp/imz-schedules/index.html",
            ),
            ImmunizationReference(
                "IE",
                "HSE",
                "https://www2.hse.ie/babies-children/vaccines-your-child/",
            ),
        ),
        observations = listOf(newbornObservation, rattleObservation),
    )
}
