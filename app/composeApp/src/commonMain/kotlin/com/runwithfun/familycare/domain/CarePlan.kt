package com.runwithfun.familycare.domain

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
class CarePlan(
    people: List<FamilyMember>,
    events: List<CareEvent>,
    documents: List<CareDocument>,
    recordedImmunizations: List<RecordedImmunization>,
    immunizationReferences: List<ImmunizationReference>,
) {
    val people: List<FamilyMember> = people.toList()
    val recordedImmunizations: List<RecordedImmunization> = recordedImmunizations.toList()
    val immunizationReferences: List<ImmunizationReference> = immunizationReferences.toList()

    private val peopleById = this.people.associateBy { it.id }
    private val documentsById = AtomicReference(
        documents.associate { it.id to it.copy(fields = it.fields.toList()) },
    )
    private val eventsById = AtomicReference(events.associateBy { it.id })

    val documents: List<CareDocument>
        get() = documentsById.load().values
            .sortedBy { it.id }
            .map { it.copy(fields = it.fields.toList()) }

    init {
        val initialDocuments = documentsById.load()
        require(peopleById.size == people.size) { "Person IDs must be unique" }
        require(initialDocuments.size == documents.size) { "Document IDs must be unique" }
        require(eventsById.load().size == events.size) { "Event IDs must be unique" }
        require(people.all { it.id.isNotBlank() }) { "Person IDs must not be blank" }
        require(documents.all { it.id.isNotBlank() && it.personId in peopleById }) {
            "Each document must have an ID and belong to a known person"
        }
        for (event in events) validateEvent(event, eventsById.load())
        for (record in recordedImmunizations) {
            val source = initialDocuments[record.sourceDocumentId]
            require(source?.personId == record.personId && source.kind == DocumentKind.IMMUNIZATION_RECORD) {
                "An immunization must link to its person's immunization record"
            }
        }
    }

    fun document(id: String): CareDocument? =
        documentsById.load()[id]?.let { it.copy(fields = it.fields.toList()) }

    /**
     * Records a parent decision for one extracted field.
     * Pass [FieldReview] with a null value to leave the field explicitly unresolved.
     * Does not invent immunizations or rewrite [recordedImmunizations].
     */
    fun reviewField(documentId: String, fieldLabel: String, review: FieldReview) {
        updateDocument(documentId) { document ->
            val index = document.fields.indexOfFirst { it.label == fieldLabel }
            require(index >= 0) { "Unknown field \"$fieldLabel\" on document $documentId" }
            val updatedFields = document.fields.toMutableList()
            updatedFields[index] = updatedFields[index].copy(review = review)
            document.copy(fields = updatedFields)
        }
    }

    fun setDocumentOriginal(documentId: String, original: DocumentOriginal) {
        updateDocument(documentId) { it.copy(original = original) }
    }

    fun eventsOn(date: CareDate, personId: String? = null): List<CareEvent> =
        eventsById.load().values
            .filter { it.date == date && (personId == null || it.personId == personId) }
            .sortedWith(compareBy<CareEvent> { it.time == null }.thenBy { it.time }.thenBy { it.id })

    fun appointments(personId: String? = null): List<Visit> =
        eventsById.load().values
            .filterIsInstance<Visit>()
            .filter { personId == null || it.personId == personId }
            .sortedWith(compareBy<Visit> { it.date.toString() }.thenBy { it.time == null }.thenBy { it.time }.thenBy { it.id })

    /** Open request tasks for a source document, independent of appointment dates. */
    fun openTasksForDocument(documentId: String): List<Task> =
        eventsById.load().values
            .filterIsInstance<Task>()
            .filter { it.sourceDocumentId == documentId && it.status == TaskStatus.OPEN }
            .sortedBy { it.id }

    /**
     * Links an existing open request for the same source document, or adds [task] once.
     * Never invents clinical status; only manages the follow-up task identity.
     */
    fun linkOpenRequestTask(task: Task): Task {
        val sourceId = task.sourceDocumentId
            ?: throw IllegalArgumentException("A request task must name its source document")
        openTasksForDocument(sourceId).firstOrNull()?.let { return it }
        addEvent(task)
        return task
    }

    fun addEvent(event: CareEvent) {
        while (true) {
            val current = eventsById.load()
            val previous = current[event.id]
            if (previous != null) {
                require(previous == event) { "Event ID already exists with different details: ${event.id}" }
                return
            }
            validateEvent(event, current)
            if (eventsById.compareAndSet(current, current + (event.id to event))) return
        }
    }

    fun confirm(proposal: AssistantProposal): Task {
        addEvent(proposal.task)
        return proposal.task
    }

    private fun updateDocument(documentId: String, transform: (CareDocument) -> CareDocument) {
        while (true) {
            val current = documentsById.load()
            val document = current[documentId]
                ?: throw IllegalArgumentException("Unknown document: $documentId")
            val updated = transform(document)
            if (documentsById.compareAndSet(current, current + (documentId to updated))) return
        }
    }

    private fun validateEvent(event: CareEvent, events: Map<String, CareEvent>) {
        require(event.id.isNotBlank()) { "Event ID must not be blank" }
        require(event.personId in peopleById) { "Unknown event owner: ${event.personId}" }
        event.sourceDocumentId?.let { sourceId ->
            require(documentsById.load()[sourceId]?.personId == event.personId) {
                "The source document must belong to the event owner"
            }
        }
        if (event is Task && event.visitId != null) {
            val visit = events[event.visitId]
            require(visit is Visit && visit.personId == event.personId) {
                "The related visit must belong to the task owner"
            }
        }
    }
}
