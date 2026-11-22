package com.runwithfun.familycare.growing

import com.runwithfun.familycare.domain.AgeStage
import com.runwithfun.familycare.domain.CareDate
import com.runwithfun.familycare.domain.CarePlan
import com.runwithfun.familycare.domain.GrowingObservation

object GrowingUpActions {
    fun saveObservation(
        plan: CarePlan,
        personId: String,
        stage: AgeStage,
        note: String,
        recordedOn: CareDate,
        id: String,
    ): GrowingObservation {
        val observation = GrowingObservation(
            id = id,
            personId = personId,
            stage = stage,
            note = note.trim(),
            recordedOn = recordedOn,
        )
        return plan.recordGrowingObservation(observation)
    }
}
