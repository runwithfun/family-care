package com.runwithfun.familycare.domain

import com.runwithfun.familycare.demo.DemoFamily
import com.runwithfun.familycare.growing.ActivityPaper
import com.runwithfun.familycare.growing.ActivityPdf
import com.runwithfun.familycare.growing.GrowingUpActions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GrowingUpTest {
    @Test
    fun birthDateSuggestsAStageOnlyWithinTheAvailableAgeRanges() {
        val today = DemoFamily.today

        assertEquals(AgeStage.NEWBORN_0_2_MONTHS, suggestedAgeStage(today, today))
        assertEquals(AgeStage.NEWBORN_0_2_MONTHS, suggestedAgeStage(CareDate(2026, 9, 24), today))
        assertEquals(AgeStage.INFANT_2_4_MONTHS, suggestedAgeStage(CareDate(2026, 9, 23), today))
        assertEquals(AgeStage.INFANT_2_4_MONTHS, suggestedAgeStage(CareDate(2026, 7, 24), today))
        assertNull(suggestedAgeStage(CareDate(2026, 7, 23), today))
        assertNull(suggestedAgeStage(CareDate(2026, 11, 24), today))
        assertNull(suggestedAgeStage(null, today))
    }

    @Test
    fun reviewedActivityExportsBothPaperSizes() {
        val activity = DemoFamily.tummyTimeActivity
        val letter = ActivityPdf.render(activity).decodeToString()
        val a4 = ActivityPdf.render(activity, ActivityPaper.A4).decodeToString()

        assertTrue(letter.startsWith("%PDF-1.4"))
        assertTrue(letter.contains("/MediaBox [0 0 612 792]"))
        assertTrue(a4.contains("/MediaBox [0 0 595 842]"))
        assertTrue(a4.contains("Supervised tummy time"))
        assertTrue(a4.contains("American Academy of Pediatrics"))
        assertTrue(a4.contains("%%EOF"))
        // Colorful kid-friendly styling (RGB fills + decorative shapes)
        assertTrue(a4.contains("0.35 0.72 0.78 rg"))
        assertTrue(a4.contains("0.98 0.62 0.45 rg"))
        assertTrue(a4.contains("Family Care"))
        assertTrue(a4.contains("Steps"))
    }

    @Test
    fun stepTextUsesDarkInkAfterDrawingItsPastelBadge() {
        for (paper in ActivityPaper.entries) {
            val lines = ActivityPdf.render(DemoFamily.tummyTimeActivity, paper).decodeToString().lines()
            val firstStep = lines.indexOfFirst { it.contains("(1. Place your awake baby") }
            assertTrue(firstStep >= 2)
            assertTrue(lines[firstStep - 2].startsWith("0.72 0.90 0.93 rg "))
            assertEquals("0.12 0.45 0.55 rg", lines[firstStep - 1])
        }
    }

    @Test
    fun switchingStagesKeepsChildHistory() {
        val plan = DemoFamily.createPlan("Sam")
        val childId = plan.people.first { it.role == FamilyRole.CHILD }.id

        assertEquals(
            listOf(DemoFamily.newbornObservation, DemoFamily.rattleObservation),
            plan.observations(childId, AgeStage.NEWBORN_0_2_MONTHS),
        )
        assertEquals(
            emptyList(),
            plan.observations(childId, AgeStage.INFANT_2_4_MONTHS),
        )
        assertEquals(
            listOf(DemoFamily.newbornObservation, DemoFamily.rattleObservation),
            plan.observations(childId),
        )
    }

    @Test
    fun savingObservationLinksNextEligibleVisitAsOneWrite() {
        val plan = DemoFamily.createPlan("Sam")
        val child = plan.people.first { it.role == FamilyRole.CHILD }
        val visit = assertNotNull(plan.nextEligibleVisit(child.id, DemoFamily.today))

        val saved = GrowingUpActions.saveObservation(
            plan = plan,
            personId = child.id,
            stage = AgeStage.INFANT_2_4_MONTHS,
            note = "Steady head control when held upright.",
            recordedOn = DemoFamily.today,
            id = "local-obs-head-control",
        )
        GrowingUpActions.saveObservation(
            plan = plan,
            personId = child.id,
            stage = AgeStage.INFANT_2_4_MONTHS,
            note = "Steady head control when held upright.",
            recordedOn = DemoFamily.today,
            id = "local-obs-head-control",
        )

        assertEquals(visit.id, saved.forVisitId)
        val discussion = plan.eventsOn(visit.date, child.id)
            .filterIsInstance<Task>()
            .single { it.id == "discuss-${saved.id}" }
        assertEquals(visit.id, discussion.visitId)
        assertTrue(discussion.title.startsWith("Discuss:"))
        assertEquals(
            listOf(DemoFamily.newbornObservation, DemoFamily.rattleObservation),
            plan.observations(child.id, AgeStage.NEWBORN_0_2_MONTHS),
        )
    }

    @Test
    fun savingObservationWithoutEligibleVisitKeepsNoteOnly() {
        val plan = CarePlan(
            people = listOf(
                FamilyMember("parent", "Parent", FamilyRole.MOTHER),
                FamilyMember("child", "Sam", FamilyRole.CHILD, birthDate = CareDate(2026, 9, 28)),
            ),
            events = emptyList(),
            documents = emptyList(),
            recordedImmunizations = emptyList(),
            immunizationReferences = emptyList(),
        )

        assertNull(plan.nextEligibleVisit("child", DemoFamily.today))
        val saved = GrowingUpActions.saveObservation(
            plan = plan,
            personId = "child",
            stage = AgeStage.NEWBORN_0_2_MONTHS,
            note = "Calm during short tummy time.",
            recordedOn = DemoFamily.today,
            id = "local-obs-no-visit",
        )

        assertNull(saved.forVisitId)
        assertEquals(listOf(saved), plan.observations("child"))
        assertTrue(plan.eventsOn(DemoFamily.today).isEmpty())
    }

    @Test
    fun conflictingObservationDetailsAreRejected() {
        val plan = DemoFamily.createPlan("Sam")

        assertFailsWith<IllegalArgumentException> {
            plan.recordGrowingObservation(DemoFamily.newbornObservation.copy(note = "Different note"))
        }
    }

    @Test
    fun reviewedActivitiesCiteSourceAndStayShort() {
        for (stage in AgeStage.entries) {
            val activity = DemoFamily.reviewedActivity(stage)
            assertTrue(activity.sourceLabel.isNotBlank())
            assertTrue(activity.sourceUrl.startsWith("https://"))
            assertTrue(activity.steps.size in 1..3)
            assertTrue(activity.steps.none { it.contains("Maya", ignoreCase = true) })
        }
    }
}
