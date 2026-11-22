package com.runwithfun.familycare.ui.onboarding

import com.runwithfun.familycare.domain.CareDate
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FamilyProfileStateTest {
    @Test
    fun savedNameBirthDateNotesAndPhotoReloadTogether() {
        val store = TestStore()
        val photo = byteArrayOf(12, 34, 56)
        val state = FamilyProfileState(store)
        assertTrue(
            state.save(
                "  Zoë  ",
                birthDate = CareDate(2026, 9, 28),
                notes = "Loves tummy time",
                photoBytes = photo,
            ),
        )
        photo[0] = 0

        val restored = assertNotNull(FamilyProfileState(store).profile)
        assertEquals("Zoë", restored.childName)
        assertEquals(CareDate(2026, 9, 28), restored.birthDate)
        assertEquals("Loves tummy time", restored.notes)
        assertContentEquals(byteArrayOf(12, 34, 56), restored.photoBytes)
        assertContentEquals(byteArrayOf(12, 34, 56), state.profile?.photoBytes)
    }

    @Test
    fun profileCanBeRestoredWithoutPhoto() {
        val store = TestStore()
        val state = FamilyProfileState(store)
        assertTrue(state.save("Maya", birthDate = CareDate(2026, 9, 28), photoBytes = byteArrayOf(1)))
        assertTrue(state.save("Maya", birthDate = CareDate(2026, 9, 28), photoBytes = null))
        assertEquals("v2|TWF5YQ==|2026-09-28||", store.load())

        val restored = assertNotNull(FamilyProfileState(store).profile)
        assertEquals("Maya", restored.childName)
        assertEquals(CareDate(2026, 9, 28), restored.birthDate)
        assertNull(restored.photoBytes)
    }

    @Test
    fun legacyProfileRestoresWithAndroidPreferenceIndentation() {
        val withoutPhoto = assertNotNull(FamilyProfileState(TestStore("TWF5YQ==\n    ")).profile)
        val withPhoto = assertNotNull(FamilyProfileState(TestStore("TWF5YQ==\nAQID    ")).profile)

        assertEquals("Maya", withoutPhoto.childName)
        assertNull(withoutPhoto.photoBytes)
        assertNull(withoutPhoto.birthDate)
        assertEquals("Maya", withPhoto.childName)
        assertContentEquals(byteArrayOf(1, 2, 3), withPhoto.photoBytes)
    }

    @Test
    fun previousColonFormatRestoresAnUnknownBirthDate() {
        val restored = assertNotNull(FamilyProfileState(TestStore("TWF5YQ==:AQID")).profile)

        assertEquals("Maya", restored.childName)
        assertNull(restored.birthDate)
        assertEquals("", restored.notes)
        assertContentEquals(byteArrayOf(1, 2, 3), restored.photoBytes)
    }

    @Test
    fun failedWriteDoesNotPublishUnsavedProfile() {
        val store = TestStore()
        val state = FamilyProfileState(store)
        assertTrue(state.save("Maya", photoBytes = null))
        store.rejectWrite = true

        assertFalse(state.save("Ava", photoBytes = byteArrayOf(1)))
        assertTrue(state.saveFailed)
        assertEquals("Maya", state.profile?.childName)
        assertEquals("Maya", FamilyProfileState(store).profile?.childName)
        store.rejectWrite = false
        assertTrue(state.save("Ava", photoBytes = null))
        assertFalse(state.saveFailed)
        assertEquals("Ava", FamilyProfileState(store).profile?.childName)
    }

    private class TestStore(initialRecord: String? = null) : FamilyProfileStore {
        var rejectWrite = false
        private var record = initialRecord

        override fun load(): String? = record

        override fun save(value: String) {
            check(!rejectWrite) { "The test store rejected the write." }
            record = value
        }
    }
}
