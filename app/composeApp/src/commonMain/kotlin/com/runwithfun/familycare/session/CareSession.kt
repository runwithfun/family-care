package com.runwithfun.familycare.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.runwithfun.familycare.domain.CareDate
import com.runwithfun.familycare.domain.CareEvent
import com.runwithfun.familycare.domain.CarePlan
import com.runwithfun.familycare.domain.FamilyMember
import com.runwithfun.familycare.domain.Task
import com.runwithfun.familycare.domain.Visit

class CareSession internal constructor(
    val plan: CarePlan,
    private val store: CarePlanEventStore,
) {
    private var generation by mutableIntStateOf(0)
    private var nextLocalId = 1
    private val overlay = linkedMapOf<String, CareEvent>()

    init {
        restore()
    }

    val people: List<FamilyMember>
        get() = plan.people

    fun person(id: String): FamilyMember = plan.people.first { it.id == id }

    fun event(id: String): CareEvent? {
        observe()
        return plan.event(id)
    }

    fun eventsOn(date: CareDate, personId: String? = null): List<CareEvent> {
        observe()
        return plan.eventsOn(date, personId)
    }

    fun eventsInMonth(year: Int, month: Int, personId: String? = null): List<CareEvent> {
        observe()
        return plan.eventsInMonth(year, month, personId)
    }

    fun daysWithEvents(year: Int, month: Int): Set<Int> =
        eventsInMonth(year, month).mapTo(mutableSetOf()) { it.date.day }

    fun notifyChanged() {
        generation++
    }

    fun allocateLocalId(prefix: String = "local-plan-item"): String {
        check(nextLocalId < Int.MAX_VALUE) { "The care-plan local ID range is exhausted." }
        val id = "$prefix-$nextLocalId"
        val followingId = nextLocalId + 1
        // Persist the counter even if the following save fails, so IDs are never reused.
        persist(CarePlanEventSnapshot(followingId, overlay.values.toList()))
        nextLocalId = followingId
        return id
    }

    fun saveVisit(visit: Visit) {
        applyAndPersist(visit)
    }

    fun saveTask(task: Task) {
        applyAndPersist(task)
    }

    private fun applyAndPersist(event: CareEvent) {
        val events = overlay + (event.id to event)
        persist(CarePlanEventSnapshot(nextLocalId, events.values.toList())) {
            if (plan.event(event.id) == null) {
                plan.addEvent(event)
            } else {
                plan.updateEvent(event)
            }
        }
        overlay[event.id] = event
        generation++
    }

    private fun restore() {
        val encoded = try {
            store.load()
        } catch (error: Exception) {
            logCareSessionError("Could not read local care-plan events.", error)
            return
        } ?: return
        val snapshot = try {
            decodeCarePlanEvents(encoded)
        } catch (error: Exception) {
            logCareSessionError("Could not decode local care-plan events.", error)
            return
        }
        nextLocalId = maxOf(1, snapshot.nextLocalId)
        for (event in snapshot.events) {
            try {
                if (plan.event(event.id) == null) {
                    plan.addEvent(event)
                } else {
                    plan.updateEvent(event)
                }
                overlay[event.id] = event
            } catch (error: Exception) {
                logCareSessionError("Could not restore local care event ${event.id}.", error)
            }
        }
    }

    private fun persist(snapshot: CarePlanEventSnapshot, publish: () -> Unit = {}) {
        val previous = store.load()
            ?: encodeCarePlanEvents(CarePlanEventSnapshot(nextLocalId, overlay.values.toList()))
        val encoded = encodeCarePlanEvents(snapshot)
        try {
            store.save(encoded)
            publish()
        } catch (failure: Exception) {
            try {
                store.save(previous)
            } catch (rollbackError: Exception) {
                if (rollbackError !== failure) failure.addSuppressed(rollbackError)
            }
            throw failure
        }
    }

    private fun observe() {
        generation
    }
}

internal fun logCareSessionError(message: String, error: Throwable) {
    println("$message\n${error.stackTraceToString()}")
}
