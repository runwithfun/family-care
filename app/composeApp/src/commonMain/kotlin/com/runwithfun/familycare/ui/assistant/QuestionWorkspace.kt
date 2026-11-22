package com.runwithfun.familycare.ui.assistant

import com.runwithfun.familycare.domain.Visit
import com.runwithfun.familycare.session.CarePlanEventSnapshot
import com.runwithfun.familycare.session.decodeCarePlanEvents
import com.runwithfun.familycare.session.encodeCarePlanEvents
import kotlin.io.encoding.Base64

internal data class QuestionWorkspace(
    val proposal: QuestionProposal? = null,
    val receiptId: String? = null,
)

internal fun questionWorkspaceKey(personId: String): String = "familycare.assistant.question.$personId"

internal fun encodeQuestionWorkspace(workspace: QuestionWorkspace): String? {
    if (workspace.proposal == null && workspace.receiptId == null) return null
    return buildString {
        appendLine("v1")
        append(workspace.receiptId.orEmpty())
        val draft = workspace.proposal ?: return@buildString
        append('\n')
        val citation = draft.citation
        append(
            listOf(
                draft.id,
                draft.revision.toString(),
                draft.title,
                draft.dateText,
                draft.visit?.let { encodeCarePlanEvents(CarePlanEventSnapshot(1, listOf(it))) }.orEmpty(),
                citation.documentId,
                citation.fileName,
                citation.quote,
                citation.page?.toString().orEmpty(),
                citation.documentDate?.toString().orEmpty(),
            ).joinToString("\t") { Base64.encode(it.encodeToByteArray()) },
        )
        append('\t')
        append(citation.originalVersion)
    }
}

internal fun decodeQuestionWorkspace(raw: String, personId: String): QuestionWorkspace {
    val parts = raw.split('\n', limit = 3)
    require(parts.size >= 2 && parts[0] == "v1") { "Invalid Assistant question workspace version." }
    val receiptId = parts[1].ifBlank { null }
    val fields = parts.getOrNull(2)?.split('\t') ?: return QuestionWorkspace(receiptId = receiptId)
    require(fields.size == 11) { "Invalid Assistant question proposal field count: ${fields.size}." }
    val values = fields.dropLast(1).map { Base64.decode(it).decodeToString(throwOnInvalidSequence = true) }
    val visit = values[4].ifBlank { null }?.let { decodeCarePlanEvents(it).events.single() as Visit }
    require(visit == null || visit.personId == personId) { "The Assistant proposal visit must belong to the person." }
    val citation = AssistantCitation(
        personId = personId,
        documentId = values[5],
        fileName = values[6],
        quote = values[7],
        page = values[8].ifBlank { null }?.toInt(),
        documentDate = values[9].ifBlank { null }?.let { requireNotNull(parseQuestionDate(it)) },
        originalVersion = fields.last(),
    )
    val draft = QuestionProposal(
        id = values[0],
        revision = values[1].toInt(),
        personId = personId,
        title = values[2],
        dateText = values[3],
        visit = visit,
        citation = citation,
    )
    return QuestionWorkspace(proposal = draft, receiptId = receiptId)
}
