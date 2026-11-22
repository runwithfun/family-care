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
    val documents: List<CareDocument> = documents.map { it.copy(fields = it.fields.toList()) }
    val recordedImmunizations: List<RecordedImmunization> = recordedImmunizations.toList()
    val immunizationReferences: List<ImmunizationReference> = immunizationReferences.toList()

    private val peopleById = this.people.associateBy { it.id }
    private val documentsById = this.documents.associateBy { it.id }
    private val eventsById = AtomicReference(events.associateBy { it.id })
    private val observationsById = AtomicReference(observations.associateBy { it.id })

    init {
        require(peopleById.size == people.size) { "Person IDs must be unique" }
        require(documentsById.size == documents.size) { "Document IDs must be unique" }
        require(eventsById.load().size == events.size) { "Event IDs must be unique" }
        require(observationsById.load().size == observations.size) { "Observation IDs must be unique" }
        require(people.all { it.id.isNotBlank() }) { "Person IDs must not be blank" }
        require(documents.all { it.id.isNotBlank() && it.personId in peopleById }) {
            "Each document must have an ID and belong to a known person"
        }
        for (event in events) validateEvent(event, eventsById.load())
        for (observation in observations) validateObservation(observation, eventsById.load())
        for (record in recordedImmunizations) {
            val source = documentsById[record.sourceDocumentId]
            require(source?.personId == record.personId && source.kind == DocumentKind.IMMUNIZATION_RECORD) {
                "An immunization must link to its person's immunization record"
            }
        }
    }

    fun event(id: String): CareEvent? = eventsById.load()[id]

    fun eventsOn(date: CareDate, personId: String? = null): List<CareEvent> =
        eventsById.load().values
            .filter { it.date == date && (personId == null || it.personId == personId) }
            .sortedWith(compareBy<CareEvent> { it.time == null }.thenBy { it.time }.thenBy { it.id })

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

    fun observations(personId: String, stage: AgeStage? = null): List<GrowingObservation> =
        observationsById.load().values
            .filter { it.personId == personId && (stage == null || it.stage == stage) }
            .sortedWith(compareBy<GrowingObservation> { it.recordedOn.toString() }.thenBy { it.id })

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
            require(documentsById[sourceId]?.personId == event.personId) {
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
