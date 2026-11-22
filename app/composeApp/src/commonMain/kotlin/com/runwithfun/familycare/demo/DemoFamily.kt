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

    private const val childVisitId = "demo-child-visit"
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
        summary = "A short, reviewed practice sheet for the newborn stage. " +
            "Use a firm surface and stay with Maya the whole time.",
        steps = listOf(
            "Place Maya on her tummy for 2–3 minutes while she is awake and calm.",
            "Get down to eye level and talk or sing so she lifts her head toward your voice.",
            "Stop when she looks tired, then try again later the same day.",
            "Write one observation to discuss at the next well-baby visit.",
        ),
    )

    fun reviewedActivity(stage: AgeStage): ReviewedActivity = when (stage) {
        AgeStage.NEWBORN_0_2_MONTHS -> tummyTimeActivity
        AgeStage.INFANT_2_4_MONTHS -> ReviewedActivity(
            id = "demo-activity-reach-and-grasp",
            stage = AgeStage.INFANT_2_4_MONTHS,
            title = "Reach and grasp play",
            summary = "A short, reviewed practice sheet for the early-infant stage. " +
                "Offer one soft toy at a time within easy reach.",
            steps = listOf(
                "Sit Maya supported on your lap facing a soft rattle.",
                "Hold the rattle just within reach and wait for her to move toward it.",
                "Celebrate any reach or grasp, then rest before another short round.",
                "Save an observation if you want to bring it up at the next visit.",
            ),
        )
    }

    private const val MilestoneSourceLabel = "CDC Learn the Signs. Act Early."
    private const val MilestoneSourceUrl =
        "https://www.cdc.gov/ncbddd/actearly/milestones/index.html"

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
            "Place your baby on their tummy for 2–3 minutes while awake and calm.",
            "Get to eye level and talk so they lift their head toward your voice.",
            "Stop when they look tired; try again later the same day.",
        ),
        sourceLabel = MilestoneSourceLabel,
        sourceUrl = MilestoneSourceUrl,
    )

    fun reviewedActivity(stage: AgeStage): ReviewedActivity = when (stage) {
        AgeStage.NEWBORN_0_2_MONTHS -> tummyTimeActivity
        AgeStage.INFANT_2_4_MONTHS -> ReviewedActivity(
            id = "demo-activity-reach-and-grasp",
            stage = AgeStage.INFANT_2_4_MONTHS,
            title = "Reach and grasp play",
            summary = "Offer one soft toy at a time within easy reach.",
            steps = listOf(
                "Support your baby on your lap facing a soft rattle.",
                "Hold it just within reach and wait for a reach or grasp.",
                "Celebrate the try, then rest before another short round.",
            ),
            sourceLabel = MilestoneSourceLabel,
            sourceUrl = MilestoneSourceUrl,
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
