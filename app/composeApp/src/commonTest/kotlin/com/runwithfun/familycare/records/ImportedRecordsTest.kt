package com.runwithfun.familycare.records

import com.runwithfun.familycare.demo.DemoFamily
import com.runwithfun.familycare.domain.OriginalContent
import com.runwithfun.familycare.session.CarePlanEventStore
import com.runwithfun.familycare.session.CareSession
import com.runwithfun.familycare.ui.assistant.AssistantIntent
import com.runwithfun.familycare.ui.assistant.AssistantReply
import com.runwithfun.familycare.ui.assistant.AssistantScope
import com.runwithfun.familycare.ui.assistant.AssistantToolResult
import com.runwithfun.familycare.ui.assistant.AssistantTools
import com.runwithfun.familycare.ui.assistant.RecordRequest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImportedRecordsTest {
    @Test
    fun keepsPersonAndVersionWithOriginalBytesAfterRestart() {
        val store = InMemoryRecordsLocalStore()
        val files = TestPdfStorage()
        val index = ImportedRecords(store, files)
        val plan = DemoFamily.createPlan()
        val mother = DemoFamily.motherId
        val child = DemoFamily.childId
        val firstPdf = LocalRecordPdf("Clinic copy.pdf", buildSyntheticClinicPdf("First version"))
        val secondPdf = firstPdf.copy(bytes = buildSyntheticClinicPdf("Second version"))
        val first = index.importPdf(plan, child, firstPdf)
        assertEquals(first, index.importPdf(plan, child, firstPdf.copy(fileName = "Same content.pdf")))
        val otherPerson = index.importPdf(plan, mother, firstPdf)
        val second = index.importPdf(plan, child, secondPdf)
        assertNotEquals(first, otherPerson)
        assertNotEquals(first, second)
        assertEquals(2, files.blobs.size)

        val restoredIndex = ImportedRecords(store, files)
        val restored = DemoFamily.createPlan().also(restoredIndex::restoreInto)
        val original = assertNotNull(restored.document(first))
        val content = assertIs<OriginalContent.ImportedPdf>(original.original.content)
        assertEquals("Clinic copy.pdf", original.original.fileName)
        assertContentEquals(firstPdf.bytes, restoredIndex.read(content.sha256))
        assertTrue(original.fields.isEmpty())

        val records = RecordsController(restored, DocumentFileStore(store), DocumentReviewStore(store), importedRecords = restoredIndex)
        val events = object : CarePlanEventStore {
            override fun load(): String? = null
            override fun save(value: String) = Unit
        }
        val tools = AssistantTools(CareSession(restored, events), records)
        val scope = AssistantScope(child, null)
        val reply = assertIs<AssistantToolResult.Reply>(tools.run(scope, AssistantIntent.FindRecord(RecordRequest.DEFAULT)))
        val citation = assertIs<AssistantReply.Answer>(reply.value).citations.single()
        assertEquals(second, citation.documentId)
        assertEquals(1, citation.page)
        assertContentEquals(secondPdf.bytes, assertNotNull(tools.open(scope, citation)).pdfBytes)
        assertNull(tools.open(AssistantScope(mother, null), citation))
        val older = assertNotNull(tools.currentCitation(scope, first))
        assertContentEquals(firstPdf.bytes, assertNotNull(tools.open(scope, older)).pdfBytes)
    }

    @Test
    fun rejectsNonPdfsAndRollsBackANewBlobWhenItsIndexCannotBeSaved() {
        val files = TestPdfStorage()
        val index = ImportedRecords(object : RecordsLocalStore {
            override fun read(key: String): String? = null
            override fun write(key: String, value: String?) = error("Index write failed")
        }, files)
        val plan = DemoFamily.createPlan()
        val originalCount = plan.documents.size
        assertFailsWith<IllegalArgumentException> {
            index.importPdf(plan, DemoFamily.childId, LocalRecordPdf("wrong.pdf", "text".encodeToByteArray()))
        }
        assertFailsWith<IllegalStateException> {
            index.importPdf(plan, DemoFamily.childId, LocalRecordPdf("clinic.pdf", buildSyntheticClinicPdf("Example")))
        }
        assertTrue(files.blobs.isEmpty())
        assertEquals(originalCount, plan.documents.size)
    }

    private class TestPdfStorage : ImportedPdfStorage {
        val blobs = mutableMapOf<String, ByteArray>()

        override fun write(hash: String, bytes: ByteArray) {
            blobs[hash] = bytes.copyOf()
        }

        override fun read(hash: String): ByteArray? = blobs[hash]?.copyOf()

        override fun delete(hash: String) {
            blobs.remove(hash)
        }
    }
}
