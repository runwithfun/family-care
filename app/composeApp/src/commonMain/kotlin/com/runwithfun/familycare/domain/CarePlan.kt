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
