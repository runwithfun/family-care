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
    observations: List<GrowingObservation> = emptyList(),
) {
    val people: List<FamilyMember> = people.toList()
    val recordedImmunizations: List<RecordedImmunization> = recordedImmunizations.toList()
    val immunizationReferences: List<ImmunizationReference> = immunizationReferences.toList()

    private val peopleById = this.people.associateBy { it.id }
    private val documentsById = AtomicReference(
        documents.associate { it.id to it.copy(fields = it.fields.toList()) },
    )
    private val eventsById = AtomicReference(events.associateBy { it.id })
    private val observationsById = AtomicReference(observations.associateBy { it.id })

    val documents: List<CareDocument>
        get() = documentsById.load().values
            .sortedBy { it.id }
            .map { it.copy(fields = it.fields.toList()) }

    init {
        val initialDocuments = documentsById.load()
        require(peopleById.size == people.size) { "Person IDs must be unique" }
        require(initialDocuments.size == documents.size) { "Document IDs must be unique" }
        require(eventsById.load().size == events.size) { "Event IDs must be unique" }
        require(observationsById.load().size == observations.size) { "Observation IDs must be unique" }
        require(people.all { it.id.isNotBlank() }) { "Person IDs must not be blank" }
        require(documents.all { it.id.isNotBlank() && it.personId in peopleById }) {
            "Each document must have an ID and belong to a known person"
        }
        for (event in events) validateEvent(event, eventsById.load())
        for (observation in observations) validateObservation(observation, eventsById.load())
        for (record in recordedImmunizations) {
            val source = initialDocuments[record.sourceDocumentId]
            require(source?.personId == record.personId && source.kind == DocumentKind.IMMUNIZATION_RECORD) {
                "An immunization must link to its person's immunization record"
            }
        }
    }

    fun event(id: String): CareEvent? = eventsById.load()[id]

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

    fun eventsInMonth(year: Int, month: Int, personId: String? = null): List<CareEvent> =
        eventsById.load().values
            .filter {
                it.date.year == year &&
                    it.date.month == month &&
                    (personId == null || it.personId == personId)
            }
            .sortedWith(
                compareBy<CareEvent> { it.date.day }
                    .thenBy { it.time == null }
                    .thenBy { it.time }
                    .thenBy { it.id },
            )

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

    // Replaces an existing event under the same ID. Same details is a no-op so retries stay safe.
    fun updateEvent(event: CareEvent) {
        while (true) {
            val current = eventsById.load()
            val previous = current[event.id]
                ?: throw IllegalArgumentException("Unknown event: ${event.id}")
            if (previous == event) return
            require(previous::class == event::class) {
                "Event kind cannot change for ${event.id}"
            }
            require(previous.personId == event.personId) {
                "Event owner cannot change for ${event.id}"
            }
            val next = current + (event.id to event)
            validateEvent(event, next)
            if (eventsById.compareAndSet(current, next)) return
        }
    }

    fun confirm(proposal: AssistantProposal): Task {
        addEvent(proposal.task)
        return proposal.task
    }

    fun observations(personId: String, stage: AgeStage? = null): List<GrowingObservation> =
        observationsById.load().values
            .filter { it.personId == personId && (stage == null || it.stage == stage) }
            .sortedWith(compareBy<GrowingObservation> { it.recordedOn.toString() }.thenBy { it.id })

    // Same-ID retry is a no-op. Conflicting details are rejected. Child history is kept across stage switches.
    fun saveObservation(observation: GrowingObservation) {
        while (true) {
            val current = observationsById.load()
            val previous = current[observation.id]
            if (previous != null) {
                require(previous == observation) {
                    "Observation ID already exists with different details: ${observation.id}"
                }
                return
            }
            validateObservation(observation, eventsById.load())
            if (observationsById.compareAndSet(current, current + (observation.id to observation))) return
        }
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

    // Earliest planned or booked visit for this person on or after [onOrAfter], or null when none.
    fun nextEligibleVisit(personId: String, onOrAfter: CareDate): Visit? =
        eventsById.load().values
            .filterIsInstance<Visit>()
            .filter { it.personId == personId }
            .filter { it.status == VisitStatus.PLANNED || it.status == VisitStatus.BOOKED }
            .filter { it.date.toString() >= onOrAfter.toString() }
            .minWithOrNull(
                compareBy<Visit> { it.date.toString() }
                    .thenBy { it.time == null }
                    .thenBy { it.time }
                    .thenBy { it.id },
            )

    /**
     * Saves the observation and, when a next eligible visit exists, its discussion task
     * as one logical write. Retries with the same IDs are no-ops. If no visit is eligible,
     * only the observation is stored.
     */
    fun recordGrowingObservation(observation: GrowingObservation): GrowingObservation {
        val visit = observation.forVisitId?.let { event(it) as? Visit }
            ?: nextEligibleVisit(observation.personId, observation.recordedOn)
        val linked = observation.copy(forVisitId = visit?.id)
        val discussion = visit?.let { discussionTaskFor(linked, it) }

        while (true) {
            val currentObservations = observationsById.load()
            val currentEvents = eventsById.load()
            val previousObservation = currentObservations[linked.id]
            if (previousObservation != null) {
                require(previousObservation == linked) {
                    "Observation ID already exists with different details: ${linked.id}"
                }
                if (discussion != null) {
                    val previousDiscussion = currentEvents[discussion.id]
                    require(previousDiscussion == null || previousDiscussion == discussion) {
                        "Discussion task ID already exists with different details: ${discussion.id}"
                    }
                }
                return linked
            }

            var nextEvents = currentEvents
            if (discussion != null) {
                val previousDiscussion = currentEvents[discussion.id]
                if (previousDiscussion != null) {
                    require(previousDiscussion == discussion) {
                        "Discussion task ID already exists with different details: ${discussion.id}"
                    }
                } else {
                    nextEvents = currentEvents + (discussion.id to discussion)
                    validateEvent(discussion, nextEvents)
                }
            }
            validateObservation(linked, nextEvents)

            val nextObservations = currentObservations + (linked.id to linked)
            if (!observationsById.compareAndSet(currentObservations, nextObservations)) continue
            if (nextEvents === currentEvents || eventsById.compareAndSet(currentEvents, nextEvents)) {
                return linked
            }
            // Roll the observation back so a failed task write does not leave a partial save.
            observationsById.compareAndSet(nextObservations, currentObservations)
        }
    }

    private fun discussionTaskFor(observation: GrowingObservation, visit: Visit): Task {
        val trimmed = observation.note.trim().replace('\n', ' ')
        val body = if (trimmed.length <= 48) trimmed else trimmed.take(45).trimEnd() + "…"
        return Task(
            id = "discuss-${observation.id}",
            personId = observation.personId,
            title = "Discuss: $body",
            date = visit.date,
            status = TaskStatus.OPEN,
            visitId = visit.id,
        )
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

    private fun validateObservation(observation: GrowingObservation, events: Map<String, CareEvent>) {
        require(observation.id.isNotBlank()) { "Observation ID must not be blank" }
        require(observation.note.isNotBlank()) { "Observation note must not be blank" }
        require(observation.personId in peopleById) { "Unknown observation owner: ${observation.personId}" }
        require(peopleById.getValue(observation.personId).role == FamilyRole.CHILD) {
            "Growing-up observations belong to the child"
        }
        observation.forVisitId?.let { visitId ->
            val visit = events[visitId]
            require(visit is Visit && visit.personId == observation.personId) {
                "The discussion visit must belong to the same child"
            }
        }
    }
}
