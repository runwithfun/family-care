package com.runwithfun.familycare.ui.assistant

import com.runwithfun.familycare.demo.DemoFamily
import com.runwithfun.familycare.domain.CareDate
import com.runwithfun.familycare.domain.CareDocument
import com.runwithfun.familycare.domain.DocumentKind
import com.runwithfun.familycare.domain.FamilyMember
import com.runwithfun.familycare.domain.Visit
import com.runwithfun.familycare.domain.VisitStatus
import com.runwithfun.familycare.records.RecordsController
import com.runwithfun.familycare.records.RetrievedOriginal
import com.runwithfun.familycare.session.CareSession
import kotlin.io.encoding.Base64

internal data class AssistantCitation(
    val personId: String,
    val documentId: String,
    val fileName: String,
    val quote: String,
    val page: Int?,
    val documentDate: CareDate?,
    internal val originalVersion: String,
)

internal sealed interface AssistantReply {
    val text: String

    data class Answer(
        override val text: String,
        val citations: List<AssistantCitation> = emptyList(),
    ) : AssistantReply

    data class ChooseVisit(override val text: String, val visits: List<Visit>) : AssistantReply
    data class ImportRecord(override val text: String) : AssistantReply
}

internal data class AssistantTurn(val prompt: String, val reply: AssistantReply)

internal data class AssistantScope(val personId: String, val visitId: String?)

internal enum class RecordRequest { DEFAULT, POSTPARTUM, IMMUNIZATION }

internal sealed interface AssistantIntent {
    data class FindRecord(val record: RecordRequest) : AssistantIntent
    data object PrepareVisit : AssistantIntent
    data class DraftQuestion(val record: RecordRequest) : AssistantIntent
    data object Unknown : AssistantIntent
}

internal fun parseAssistantIntent(prompt: String): AssistantIntent {
    val words = Regex("[a-z0-9]+").findAll(prompt.lowercase()).map { it.value }.toSet()
    val record = when {
        words.any { it in immunizationWords } -> RecordRequest.IMMUNIZATION
        words.any { it in postpartumWords } -> RecordRequest.POSTPARTUM
        else -> RecordRequest.DEFAULT
    }
    val asksAboutQuestions = "question" in words || "questions" in words || "ask" in words
    return when {
        asksAboutQuestions && words.any { it in draftWords } -> AssistantIntent.DraftQuestion(record)
        asksAboutQuestions || words.any { it in preparationWords } -> AssistantIntent.PrepareVisit
        words.any { it in visitWords } && words.any { it in scheduleWords } -> AssistantIntent.PrepareVisit
        record != RecordRequest.DEFAULT || words.any { it in recordWords } -> AssistantIntent.FindRecord(record)
        else -> AssistantIntent.Unknown
    }
}

private val immunizationWords = setOf("immunization", "immunizations", "immunisation", "immunisations", "vaccine", "vaccines", "dose")
private val postpartumWords = setOf("postpartum", "recovery")
private val draftWords = setOf("draft", "save", "add", "write", "remind")
private val preparationWords = setOf("prepare", "preparation", "ready")
private val visitWords = setOf("visit", "appointment", "followup", "follow")
private val scheduleWords = setOf("when", "moved", "rescheduled", "next")
private val recordWords = setOf("record", "records", "original", "note", "document", "pdf")

internal sealed interface AssistantToolResult {
    data class Reply(val value: AssistantReply) : AssistantToolResult
    data class Question(
        val title: String,
        val visit: Visit?,
        val citation: AssistantCitation,
    ) : AssistantToolResult
}

/** Reads only the selected person's agenda and indexed demo sources. */
internal class AssistantTools(
    private val session: CareSession,
    private val records: RecordsController,
) {
    internal fun visits(personId: String): List<Visit> =
        session.plan.appointments(personId).mapNotNull { (session.event(it.id) as? Visit)?.takeIf(Visit::isOpen) }

    internal fun run(scope: AssistantScope, intent: AssistantIntent): AssistantToolResult = when (intent) {
        is AssistantIntent.FindRecord -> withRecord(scope, intent.record) { record ->
            reply(record.summary, listOf(record.citation))
        }
        AssistantIntent.PrepareVisit -> {
            val visit = openVisit(scope)
            if (visit == null) chooseVisit(scope.personId)
            else withRecord(scope, RecordRequest.DEFAULT) { record ->
                val dateAndTime = listOfNotNull(visit.date.toString(), visit.time?.toString()).joinToString(" at ")
                val preparation = "${visit.title} is ${visit.status.name.lowercase()} for $dateAndTime with ${visit.provider}."
                reply("$preparation\n\n${record.summary}\n\nQuestion to bring: ${record.question}", listOf(record.citation))
            }
        }
        is AssistantIntent.DraftQuestion -> {
            val visit = openVisit(scope)
            if (visit == null && (scope.visitId != null ||
                    intent.record != RecordRequest.IMMUNIZATION && visits(scope.personId).isNotEmpty())
            ) {
                chooseVisit(scope.personId)
            } else {
                withRecord(scope, intent.record) { record ->
                    AssistantToolResult.Question(record.question, visit, record.citation)
                }
            }
        }
        AssistantIntent.Unknown -> reply(
            "I can find an original record, help you prepare for a visit, or draft a question for your clinician.",
        )
    }

    internal fun open(scope: AssistantScope, citation: AssistantCitation): RetrievedOriginal? {
        if (scope.personId != citation.personId) return null
        val record = scopedRecord(scope, citation.documentId) ?: return null
        return record.original.takeIf { record.citation == citation }
    }

    internal fun currentCitation(scope: AssistantScope, documentId: String): AssistantCitation? =
        scopedRecord(scope, documentId)?.citation

    private fun scopedRecord(scope: AssistantScope, documentId: String): AssistantRecord? {
        val document = records.document(documentId)?.takeIf { it.personId == scope.personId } ?: return null
        if (document.kind == DocumentKind.IMMUNIZATION_RECORD && !records.isPdfImported(document.id)) return null
        return readRecord(document)
    }

    internal fun openVisit(scope: AssistantScope): Visit? =
        (scope.visitId?.let(session::event) as? Visit)?.takeIf { it.personId == scope.personId && it.isOpen() }

    private fun withRecord(
        scope: AssistantScope,
        request: RecordRequest,
        onRead: (AssistantRecord) -> AssistantToolResult,
    ): AssistantToolResult {
        val document = findRecord(scope, request) ?: return reply("I couldn’t find a matching record for this person.")
        if (document.id == DemoFamily.immunizationDocumentId && !records.isPdfImported(document.id)) {
            return AssistantToolResult.Reply(
                AssistantReply.ImportRecord("Import the sample immunization PDF to read and cite the original."),
            )
        }
        val record = readRecord(document) ?: return reply("I couldn’t verify an excerpt in this original.")
        return onRead(record)
    }

    private fun findRecord(scope: AssistantScope, request: RecordRequest): CareDocument? {
        val linkedId = openVisit(scope)?.sourceDocumentId
        val person = session.people.firstOrNull { it.id == scope.personId } ?: return null
        val requestedId = when (request) {
            RecordRequest.POSTPARTUM -> DemoFamily.recoveryDocumentId
            RecordRequest.IMMUNIZATION -> DemoFamily.immunizationDocumentId
            RecordRequest.DEFAULT -> linkedId ?: demoDocumentId(person)
        }
        return records.document(requestedId)?.takeIf { it.personId == scope.personId }
    }

    private fun readRecord(document: CareDocument): AssistantRecord? {
        val original = records.readOriginal(document.id)
        return when (document.id) {
            DemoFamily.recoveryDocumentId -> postpartumRecord(document, original)
            DemoFamily.immunizationDocumentId -> immunizationRecord(document, original)
            else -> null
        }
    }

    private fun postpartumRecord(document: CareDocument, original: RetrievedOriginal): AssistantRecord? {
        val text = original.textPreview ?: return null
        val quote = DemoFamily.recoveryObservation
        if (quote !in text) return null
        return AssistantRecord(
            original = original,
            citation = citation(
                document, original, quote, page = null,
                date = DemoFamily.recoveryNoteDate.takeIf { text.lineSequence().any { line ->
                    line == "Note date: $it · Fictional demo record"
                } },
            ),
            summary = "The postpartum note records soreness after longer walks.",
            question = "What should I keep track of when I feel sore after a walk?",
        )
    }

    private fun immunizationRecord(document: CareDocument, original: RetrievedOriginal): AssistantRecord? {
        val bytes = original.pdfBytes ?: return null
        val quote = DemoFamily.missingImmunizationExcerpt
        // The sample importer writes one page with an uncompressed text stream.
        val pdf = bytes.decodeToString(throwOnInvalidSequence = false)
        val pageText = pdf.substringAfter(">>stream\n", "").substringBefore("endstream\n")
        val sourceLines = listOf(quote, DemoFamily.immunizationDocumentDateExcerpt, DemoFamily.noVaccinationEntriesExcerpt)
        if ("/Type /Pages /Kids [3 0 R] /Count 1" !in pdf || sourceLines.any { "($it) Tj" !in pageText }) return null
        val field = document.fields.firstOrNull { it.label == DemoFamily.administrationDateField } ?: return null
        val reviewedDate = field.review?.value
        val summary = buildString {
            append("The clinic issued this copy on ${DemoFamily.immunizationDocumentDate}. ")
            append("The original administration date is unknown. This copy includes no vaccination entries.")
            if (reviewedDate != null) append(" Records has a manually entered date of $reviewedDate.")
        }
        return AssistantRecord(
            original = original,
            citation = citation(document, original, quote, page = 1, date = DemoFamily.immunizationDocumentDate),
            summary = summary,
            question = "Could you send a complete immunization record with the names and dates of any vaccinations on file?",
        )
    }

    private fun citation(
        document: CareDocument,
        original: RetrievedOriginal,
        quote: String,
        page: Int?,
        date: CareDate?,
    ): AssistantCitation = AssistantCitation(
        personId = document.personId,
        documentId = document.id,
        fileName = original.fileName,
        quote = quote,
        page = page,
        documentDate = date,
        originalVersion = Base64.encode(original.pdfBytes ?: original.textPreview.orEmpty().encodeToByteArray()),
    )

    private fun demoDocumentId(person: FamilyMember): String =
        if (person.id == DemoFamily.motherId) DemoFamily.recoveryDocumentId else DemoFamily.immunizationDocumentId

    private fun chooseVisit(personId: String): AssistantToolResult {
        val visits = visits(personId)
        return if (visits.isEmpty()) reply("There are no open visits for this person.")
        else AssistantToolResult.Reply(AssistantReply.ChooseVisit("Choose the visit you want to prepare for.", visits))
    }

    private fun reply(text: String, citations: List<AssistantCitation> = emptyList()): AssistantToolResult =
        AssistantToolResult.Reply(AssistantReply.Answer(text, citations))

    private data class AssistantRecord(
        val original: RetrievedOriginal,
        val citation: AssistantCitation,
        val summary: String,
        val question: String,
    )
}

private fun Visit.isOpen(): Boolean = status == VisitStatus.BOOKED || status == VisitStatus.PLANNED
