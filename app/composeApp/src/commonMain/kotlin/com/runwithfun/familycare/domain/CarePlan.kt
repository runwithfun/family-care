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
    val documents: List<CareDocument> = documents.map { it.copy(fields = it.fields.toList()) }
    val recordedImmunizations: List<RecordedImmunization> = recordedImmunizations.toList()
    val immunizationReferences: List<ImmunizationReference> = immunizationReferences.toList()

    private val peopleById = this.people.associateBy { it.id }
    private val documentsById = this.documents.associateBy { it.id }
    private val eventsById = AtomicReference(events.associateBy { it.id })

    init {
        require(peopleById.size == people.size) { "Person IDs must be unique" }
        require(documentsById.size == documents.size) { "Document IDs must be unique" }
        require(eventsById.load().size == events.size) { "Event IDs must be unique" }
        require(people.all { it.id.isNotBlank() }) { "Person IDs must not be blank" }
        require(documents.all { it.id.isNotBlank() && it.personId in peopleById }) {
            "Each document must have an ID and belong to a known person"
        }
        for (event in events) validateEvent(event, eventsById.load())
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
}
