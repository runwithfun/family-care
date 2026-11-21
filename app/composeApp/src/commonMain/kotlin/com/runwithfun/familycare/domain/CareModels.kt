package com.runwithfun.familycare.domain

enum class FamilyRole { MOTHER, CHILD }

data class FamilyMember(
    val id: String,
    val displayName: String,
    val role: FamilyRole,
    val birthDate: CareDate? = null,
)

sealed interface CareEvent {
    val id: String
    val personId: String
    val title: String
    val date: CareDate
    val time: CareTime?
    val sourceDocumentId: String?
}

enum class VisitStatus { PLANNED, BOOKED, COMPLETED, CANCELED }
enum class TaskStatus { OPEN, DONE }

data class Visit(
    override val id: String,
    override val personId: String,
    override val title: String,
    override val date: CareDate,
    val provider: String,
    val status: VisitStatus,
    override val time: CareTime? = null,
    override val sourceDocumentId: String? = null,
) : CareEvent

data class Task(
    override val id: String,
    override val personId: String,
    override val title: String,
    override val date: CareDate,
    val status: TaskStatus = TaskStatus.OPEN,
    override val time: CareTime? = null,
    override val sourceDocumentId: String? = null,
    val visitId: String? = null,
) : CareEvent

// A proposal uses the ID of the task it will create. Keep that ID unchanged on retry.
data class AssistantProposal(val task: Task)

enum class DocumentKind { VISIT_SUMMARY, IMMUNIZATION_RECORD, OTHER }

data class CareDocument(
    val id: String,
    val personId: String,
    val kind: DocumentKind,
    val original: DocumentOriginal,
    val fields: List<DocumentField>,
)

data class DocumentOriginal(
    val fileName: String,
    val mediaType: String,
    val content: OriginalContent,
)

sealed interface OriginalContent {
    data class StoredFile(val storageKey: String) : OriginalContent
    data class Text(val value: String) : OriginalContent
}

data class DocumentField(
    val label: String,
    val proposal: ExtractedValue? = null,
    val review: FieldReview? = null,
)

data class ExtractedValue(
    val value: String,
    val quotedText: String,
    val page: Int? = null,
)

// A review with a null value records an explicit decision to leave the field unresolved.
data class FieldReview(val value: String?)

data class RecordedImmunization(
    val personId: String,
    val name: String,
    val dateAdministered: CareDate,
    val sourceDocumentId: String,
)

data class ImmunizationReference(
    val jurisdiction: String,
    val authority: String,
    val sourceUrl: String,
)
