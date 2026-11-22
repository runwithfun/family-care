package com.runwithfun.familycare.domain

import com.runwithfun.familycare.demo.DemoFamily
import com.runwithfun.familycare.growing.ActivityPdf
import com.runwithfun.familycare.growing.GrowingUpActions
import com.runwithfun.familycare.growing.saveLocalPdf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GrowingUpTest {
    @Test
    fun switchingStagesKeepsChildHistory() {
        val plan = DemoFamily.createPlan()

        assertEquals(
            listOf(DemoFamily.newbornObservation),
            plan.observations(DemoFamily.childId, AgeStage.NEWBORN_0_2_MONTHS),
        )
        assertEquals(
            listOf(DemoFamily.infantObservation),
            plan.observations(DemoFamily.childId, AgeStage.INFANT_2_4_MONTHS),
        )
        assertEquals(
            listOf(DemoFamily.newbornObservation, DemoFamily.infantObservation),
            plan.observations(DemoFamily.childId),
        )
    }

    @Test
    fun savingObservationCreatesVisitDiscussionTask() {
        val plan = DemoFamily.createPlan()
        val observation = GrowingObservation(
            id = "local-obs-head-control",
            personId = DemoFamily.childId,
            stage = AgeStage.INFANT_2_4_MONTHS,
            note = "Steady head control when held upright.",
            recordedOn = DemoFamily.today,
            forVisitId = DemoFamily.childVisitId,
        )

        GrowingUpActions.saveObservation(plan, observation)
        GrowingUpActions.saveObservation(plan, observation)

        assertEquals(
            listOf(DemoFamily.infantObservation, observation),
            plan.observations(DemoFamily.childId, AgeStage.INFANT_2_4_MONTHS),
        )
        val discussion = plan.eventsOn(DemoFamily.today, DemoFamily.childId)
            .filterIsInstance<Task>()
            .single { it.id == "discuss-${observation.id}" }
        assertEquals(DemoFamily.childVisitId, discussion.visitId)
        assertTrue(discussion.title.startsWith("Discuss:"))
        assertEquals(
            listOf(DemoFamily.newbornObservation),
            plan.observations(DemoFamily.childId, AgeStage.NEWBORN_0_2_MONTHS),
        )
    }

    @Test
    fun conflictingObservationDetailsAreRejected() {
        val plan = DemoFamily.createPlan()

        assertFailsWith<IllegalArgumentException> {
            plan.saveObservation(DemoFamily.newbornObservation.copy(note = "Different note"))
        }
    }

    @Test
    fun reviewedActivityExportsReadablePdf() {
        val activity = DemoFamily.tummyTimeActivity
        val bytes = ActivityPdf.render(activity, "Maya")
        val text = bytes.decodeToString()

        assertTrue(text.startsWith("%PDF-1.4"))
        assertTrue(text.contains("Supervised tummy time"))
        assertTrue(text.contains("For Maya"))
        assertTrue(text.contains("%%EOF"))

        val path = saveLocalPdf(ActivityPdf.fileName(activity), bytes)
        assertTrue(path.endsWith(".pdf"))
        assertTrue(path.contains(activity.id))
    }
}
