package com.runwithfun.familycare.growing

import com.runwithfun.familycare.domain.CarePlan
import com.runwithfun.familycare.domain.GrowingObservation
import com.runwithfun.familycare.domain.Task
import com.runwithfun.familycare.domain.TaskStatus
import com.runwithfun.familycare.domain.Visit

object GrowingUpActions {
    fun saveObservation(plan: CarePlan, observation: GrowingObservation): GrowingObservation {
        plan.saveObservation(observation)
        val visitId = observation.forVisitId ?: return observation
        val visit = plan.event(visitId) as? Visit
            ?: error("Unknown visit for observation discussion: $visitId")
        val discussion = Task(
            id = "discuss-${observation.id}",
            personId = observation.personId,
            title = discussionTitle(observation.note),
            date = visit.date,
            status = TaskStatus.OPEN,
            visitId = visitId,
        )
        plan.addEvent(discussion)
        return observation
    }

    private fun discussionTitle(note: String): String {
        val trimmed = note.trim().replace('\n', ' ')
        val body = if (trimmed.length <= 48) trimmed else trimmed.take(45).trimEnd() + "…"
        return "Discuss: $body"
    }
}
