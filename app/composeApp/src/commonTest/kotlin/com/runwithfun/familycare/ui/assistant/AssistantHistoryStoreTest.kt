package com.runwithfun.familycare.ui.assistant

import com.runwithfun.familycare.records.InMemoryRecordsLocalStore
import com.runwithfun.familycare.records.RecordsLocalStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AssistantHistoryStoreTest {
    @Test
    fun longHistoryFitsDesktopPreferencesAndFailedWriteKeepsTheOldHistory() {
        val historyKey = assistantHistoryKey("demo-mother")
        val backing = InMemoryRecordsLocalStore()
        var rejectNextIndex = false
        val restrictedStore = object : RecordsLocalStore {
            override fun read(key: String): String? = backing.read(key)

            override fun write(key: String, value: String?) {
                require(value == null || value.length <= 8_192)
                if (key == historyKey && rejectNextIndex) {
                    rejectNextIndex = false
                    error("The next index could not be written.")
                }
                backing.write(key, value)
            }
        }
        val historyStore = AssistantHistoryStore(restrictedStore)
        val original = "first transcript ".repeat(1_500)
        historyStore.write(historyKey, original)
        assertEquals(original, historyStore.read(historyKey))
        val saved = backing.snapshot()

        rejectNextIndex = true
        val newHistory = original + "another reply ".repeat(1_500)
        assertFailsWith<IllegalStateException> { historyStore.write(historyKey, newHistory) }
        assertEquals(original, historyStore.read(historyKey))
        assertEquals(saved, backing.snapshot())

        historyStore.write(historyKey, newHistory)
        assertEquals(newHistory, historyStore.read(historyKey))
        historyStore.write(historyKey, "short transcript")
        assertEquals(mapOf(historyKey to "short transcript"), backing.snapshot())
    }
}
