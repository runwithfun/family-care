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
import com.runwithfun.familycare.domain.RecordedImmunization
import com.runwithfun.familycare.domain.ReviewedActivity
import com.runwithfun.familycare.domain.Task
import com.runwithfun.familycare.domain.Visit
import com.runwithfun.familycare.domain.VisitStatus

object DemoFamily {
    const val motherId = "demo-mother"
    const val childId = "demo-child"
    const val childVisitId = "demo-child-visit"
    val today = CareDate(2026, 11, 23)

    private const val recoveryVisitId = "demo-recovery-visit"
    const val recoveryDocumentId = "demo-recovery-note"
    const val immunizationDocumentId = "demo-immunizations"
    const val immunizationPdfStorageKey = "documents/demo-immunizations.pdf"
    const val immunizationAField = "Example immunization A date"
    const val immunizationBField = "Example immunization B date"
    const val immunizationAName = "Example immunization A"
    val immunizationDate = CareDate(2026, 11, 2)

    /** Readable recovery note for Assistant (#7). Kept as on-device text. */
    const val recoveryNoteText =
        "Fictional clinic note for demonstration. Follow-up booked at 14:30. " +
            "Bring any questions about your recovery."

    fun immunizationPdfBody(childName: String = "Maya"): String =
        """
        Example Family Clinic — Immunization record (synthetic demo PDF)
        Patient: $childName
        Date of birth: 2026-09-28

        Documented doses on this copy:
        Example immunization A: $immunizationDate

        Example immunization B: not listed on this copy.
        Contact the clinic for a complete record before assuming any missing dose status.
        """.trimIndent()

    val newbornObservation = GrowingObservation(
        id = "demo-obs-newborn-lift",
        personId = childId,
        stage = AgeStage.NEWBORN_0_2_MONTHS,
        note = "Holds head up briefly during supervised tummy time.",
        recordedOn = CareDate(2026, 10, 20),
    )

    val infantObservation = GrowingObservation(
        id = "demo-obs-infant-reach",
        personId = childId,
        stage = AgeStage.INFANT_2_4_MONTHS,
        note = "Reaches toward a soft rattle held nearby.",
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

    fun createPlan(childName: String = "Maya"): CarePlan = CarePlan(
        people = listOf(
            FamilyMember(motherId, "Aoife", FamilyRole.MOTHER),
            FamilyMember(childId, childName, FamilyRole.CHILD, birthDate = CareDate(2026, 9, 28)),
        ),
        events = listOf(
            Visit(
                id = childVisitId,
                personId = childId,
                title = "Well-baby visit",
                date = today,
                time = CareTime(9, 30),
                provider = "Example Family Clinic",
                status = VisitStatus.BOOKED,
            ),
            Visit(
                id = recoveryVisitId,
                personId = motherId,
                title = "Recovery follow-up",
                date = today,
                time = CareTime(14, 30),
                provider = "Example Family Clinic",
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
                        proposal = ExtractedValue("14:30", "Follow-up booked at 14:30."),
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
                        label = immunizationAField,
                        proposal = ExtractedValue(
                            immunizationDate.toString(),
                            "Example immunization A: $immunizationDate",
                        ),
                    ),
                    DocumentField(label = immunizationBField),
                ),
            ),
        ),
        recordedImmunizations = listOf(
            RecordedImmunization(
                personId = childId,
                name = immunizationAName,
                dateAdministered = immunizationDate,
                sourceDocumentId = immunizationDocumentId,
            ),
        ),
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
        observations = listOf(newbornObservation, infantObservation),
    )
}
