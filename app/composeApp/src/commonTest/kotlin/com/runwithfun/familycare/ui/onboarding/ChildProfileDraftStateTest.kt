package com.runwithfun.familycare.ui.onboarding

import com.runwithfun.familycare.demo.DemoFamily
import com.runwithfun.familycare.domain.CareDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ChildProfileDraftStateTest {
    @Test
    fun birthDateRequiresAnIsoCalendarDateOnOrBeforeTheDemoDay() {
        val draft = ChildProfileDraftState("Maya", "2024-02-29", "", null, null)

        assertEquals(CareDate(2024, 2, 29), draft.validatedBirthDate())

        for (input in listOf("", "2024-2-29", "2025-02-29", "2026-11-24")) {
            draft.birthDateText = input
            assertNull(draft.validatedBirthDate(), input)
            assertNotNull(draft.birthDateError, input)
        }

        draft.birthDateText = DemoFamily.today.toString()
        assertEquals(DemoFamily.today, draft.validatedBirthDate())
        assertNull(draft.birthDateError)
    }

    @Test
    fun editingAnOlderProfileMayKeepAnUnknownBirthDate() {
        val draft = ChildProfileDraftState("Maya", "", "", null, null)

        assertNull(draft.validatedBirthDate(required = false))
        assertNull(draft.birthDateError)

        draft.birthDateText = "2026-02-30"
        assertNull(draft.validatedBirthDate(required = false))
        assertNotNull(draft.birthDateError)

        draft.birthDateText = ""
        assertNull(draft.validatedBirthDate(required = false))
        assertNull(draft.birthDateError)
    }
}
