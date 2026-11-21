package com.runwithfun.familycare.demo

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
import com.runwithfun.familycare.domain.ImmunizationReference
import com.runwithfun.familycare.domain.OriginalContent
import com.runwithfun.familycare.domain.RecordedImmunization
import com.runwithfun.familycare.domain.Task
import com.runwithfun.familycare.domain.Visit
import com.runwithfun.familycare.domain.VisitStatus

object DemoFamily {
    const val motherId = "demo-mother"
    const val childId = "demo-child"
    val today = CareDate(2026, 11, 23)

    private const val recoveryVisitId = "demo-recovery-visit"
    private const val recoveryDocumentId = "demo-recovery-note"
    private const val immunizationDocumentId = "demo-immunizations"
    private val immunizationDate = CareDate(2026, 11, 2)

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

    fun createPlan(): CarePlan = CarePlan(
        people = listOf(
            FamilyMember(motherId, "Aoife", FamilyRole.MOTHER),
            FamilyMember(childId, "Maya", FamilyRole.CHILD, birthDate = CareDate(2026, 9, 28)),
        ),
        events = listOf(
            Visit(
                id = "demo-child-visit",
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
                    content = OriginalContent.Text(
                        "Fictional clinic note for demonstration. Follow-up booked at 14:30. " +
                            "Bring any questions about your recovery.",
                    ),
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
                    fileName = "demo-immunizations.txt",
                    mediaType = "text/plain",
                    content = OriginalContent.Text(
                        "Fictional record for demonstration. Example immunization A: $immunizationDate.",
                    ),
                ),
                fields = listOf(
                    DocumentField(
                        label = "Administration date",
                        proposal = ExtractedValue(
                            immunizationDate.toString(),
                            "Example immunization A: $immunizationDate.",
                        ),
                    ),
                ),
            ),
        ),
        recordedImmunizations = listOf(
            RecordedImmunization(
                personId = childId,
                name = "Example immunization A",
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
    )
}
