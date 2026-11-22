package com.runwithfun.familycare.records

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.runwithfun.familycare.domain.CareDocument
import com.runwithfun.familycare.domain.CarePlan
import com.runwithfun.familycare.domain.DocumentKind
import com.runwithfun.familycare.domain.DocumentOriginal
import com.runwithfun.familycare.domain.OriginalContent
import kotlin.io.encoding.Base64

@Composable
internal fun rememberImportedRecords(): ImportedRecords {
    val index = rememberRecordsLocalStore()
    val files = rememberImportedPdfStorage()
    return remember(index, files) { ImportedRecords(index, files) }
}

internal class ImportedRecords(
    private val index: RecordsLocalStore,
    private val files: ImportedPdfStorage,
) {
    fun restoreInto(plan: CarePlan) {
        try {
            for (record in load()) {
                if (plan.people.any { it.id == record.personId }) plan.registerImportedDocument(record.document())
            }
        } catch (error: Exception) {
            println("Could not restore imported clinic records.\n${error.stackTraceToString()}")
        }
    }

    fun importPdf(plan: CarePlan, personId: String, pdf: LocalRecordPdf): String {
        require(plan.people.any { it.id == personId }) { "Unknown clinic record owner: $personId" }
        require(pdf.bytes.size <= MaxLocalRecordPdfBytes && pdf.bytes.isPdfHeader()) {
            "Could not import ${pdf.fileName} (${pdf.bytes.size} bytes) as a local PDF."
        }

        val sha256 = recordPdfSha256(pdf.bytes)
        val records = load()
        val existing = records.firstOrNull { it.personId == personId && it.sha256 == sha256 }
        val record = existing ?: Entry(personId, sha256, pdf.fileName.ifBlank { "Clinic record.pdf" })
        val document = record.document()
        val alreadyStored = files.read(sha256) != null
        if (!alreadyStored) files.write(sha256, pdf.bytes)

        if (existing == null) {
            try {
                index.write(IndexKey, (records + record).joinToString("\n", transform = Entry::encode))
            } catch (error: Exception) {
                if (!alreadyStored && records.none { it.sha256 == sha256 }) {
                    runCatching { files.delete(sha256) }.exceptionOrNull()?.let { if (it !== error) error.addSuppressed(it) }
                }
                throw error
            }
        }
        plan.registerImportedDocument(document)
        return document.id
    }

    fun read(sha256: String): ByteArray = files.read(sha256)
        ?: error("Stored clinic original is missing for SHA-256 $sha256")

    fun latestDocumentId(personId: String): String? = load().lastOrNull { it.personId == personId }?.id

    private fun load(): List<Entry> = index.read(IndexKey).orEmpty().lineSequence()
        .filter { it.isNotEmpty() }
        .map { line ->
            val parts = line.split('\t')
            require(parts.size == 3 && parts[1].length == 64 && parts[1].all { it in '0'..'9' || it in 'a'..'f' }) {
                "Invalid local clinic record index entry: $line"
            }
            Entry(
                personId = Base64.decode(parts[0]).decodeToString(throwOnInvalidSequence = true),
                sha256 = parts[1],
                fileName = Base64.decode(parts[2]).decodeToString(throwOnInvalidSequence = true),
            )
        }
        .toList()

    private data class Entry(val personId: String, val sha256: String, val fileName: String) {
        val id get() = "$personId-local-pdf-$sha256"

        fun encode(): String = "${Base64.encode(personId.encodeToByteArray())}\t$sha256\t${Base64.encode(fileName.encodeToByteArray())}"

        fun document() = CareDocument(
            id = id,
            personId = personId,
            kind = DocumentKind.OTHER,
            original = DocumentOriginal(fileName, "application/pdf", OriginalContent.ImportedPdf(sha256)),
            fields = emptyList(),
        )
    }

    private companion object {
        const val IndexKey = "familycare.records.imports.v1"
    }
}
