package com.runwithfun.familycare.ui.assistant

import com.runwithfun.familycare.domain.Visit
import com.runwithfun.familycare.session.CarePlanEventSnapshot
import com.runwithfun.familycare.session.decodeCarePlanEvents
import com.runwithfun.familycare.session.encodeCarePlanEvents
import kotlin.io.encoding.Base64

internal data class AssistantHistory(
    val visitId: String?,
    val turns: List<AssistantTurn>,
)

internal const val AssistantSelectedPersonKey = "familycare.assistant.selected-person"

internal fun assistantHistoryKey(personId: String): String = "familycare.assistant.history.$personId"

internal fun encodeAssistantHistory(history: AssistantHistory): String = buildString {
    appendLine("v1")
    append(encodeText(history.visitId.orEmpty()))
    for (turn in history.turns) {
        append('\n')
        append(encodeTurn(turn))
    }
}

internal fun decodeAssistantHistory(raw: String, personId: String): AssistantHistory {
    val lines = raw.split('\n')
    require(lines.size >= 2 && lines.first() == "v1") { "Unsupported Assistant history version." }
    return AssistantHistory(
        visitId = decodeText(lines[1]).ifEmpty { null },
        turns = lines.drop(2).map { decodeTurn(it, personId) },
    )
}

private fun encodeTurn(turn: AssistantTurn): String {
    val reply = turn.reply
    val (kind, details) = when (reply) {
        is AssistantReply.Answer -> "answer" to reply.citations.flatMap { citation ->
            listOf(
                citation.personId,
                citation.documentId,
                citation.fileName,
                citation.quote,
                citation.page?.toString().orEmpty(),
                citation.documentDate?.toString().orEmpty(),
                citation.originalVersion,
            )
        }
        is AssistantReply.ChooseVisit -> "choose-visit" to listOf(
            encodeCarePlanEvents(CarePlanEventSnapshot(1, reply.visits)),
        )
        is AssistantReply.ImportRecord -> "import-record" to emptyList()
    }
    return kind + '\t' + (listOf(turn.prompt, reply.text) + details).joinToString("\t", transform = ::encodeText)
}

private fun decodeTurn(line: String, personId: String): AssistantTurn {
    val fields = line.split('\t')
    require(fields.size >= 3) { "Assistant turn is missing a prompt or reply." }
    val values = fields.drop(1).map(::decodeText)
    val text = values[1]
    val details = values.drop(2)
    val reply = when (val kind = fields[0]) {
        "answer" -> AssistantReply.Answer(text, details.chunked(CitationFieldCount).map { decodeCitation(it, personId) })
        "choose-visit" -> AssistantReply.ChooseVisit(
            text,
            decodeCarePlanEvents(details.single()).events.map { event ->
                require(event is Visit && event.personId == personId) { "Assistant history visit belongs to a different person." }
                event
            },
        )
        "import-record" -> {
            require(details.isEmpty()) { "An Assistant import reply has unexpected details." }
            AssistantReply.ImportRecord(text)
        }
        else -> error("Unknown Assistant reply kind: $kind")
    }
    return AssistantTurn(values[0], reply)
}

private const val CitationFieldCount = 7

private fun decodeCitation(fields: List<String>, personId: String): AssistantCitation {
    require(fields.size == CitationFieldCount && fields[0] == personId) { "Invalid citation in Assistant history for $personId." }
    return AssistantCitation(
        personId = fields[0],
        documentId = fields[1],
        fileName = fields[2],
        quote = fields[3],
        page = fields[4].takeIf { it.isNotEmpty() }?.toInt(),
        documentDate = fields[5].takeIf { it.isNotEmpty() }?.let { requireNotNull(parseQuestionDate(it)) },
        originalVersion = fields[6],
    )
}

private fun encodeText(value: String): String = Base64.encode(value.encodeToByteArray())

private fun decodeText(value: String): String = Base64.decode(value).decodeToString(throwOnInvalidSequence = true)
