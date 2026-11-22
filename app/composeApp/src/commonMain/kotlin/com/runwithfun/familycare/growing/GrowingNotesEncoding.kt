package com.runwithfun.familycare.growing

import com.runwithfun.familycare.domain.AgeStage
import com.runwithfun.familycare.domain.CareDate
import com.runwithfun.familycare.domain.CareTime
import com.runwithfun.familycare.domain.GrowingObservation
import com.runwithfun.familycare.domain.GrowingObservationEntry
import com.runwithfun.familycare.domain.Task
import com.runwithfun.familycare.domain.TaskStatus
import kotlin.io.encoding.Base64

private const val FormatVersion = "growing-notes-1"

internal fun encodeGrowingNotes(entries: List<GrowingObservationEntry>): String = buildString {
    append(FormatVersion)
    for ((note, task) in entries) {
        val fields = listOf(
            encode(note.id),
            encode(note.personId),
            note.stage.name,
            encode(note.note),
            note.recordedOn.toString(),
            encode(note.forVisitId),
        )
        val taskFields = if (task == null) emptyList() else listOf(
            encode(task.id),
            encode(task.title),
            task.date.toString(),
            task.status.name,
            task.time?.toString().orEmpty(),
            encode(task.sourceDocumentId),
        )
        append('\n')
        append((fields + taskFields).joinToString("\t"))
    }
}

internal fun decodeGrowingNotes(payload: String): List<GrowingObservationEntry> {
    val lines = payload.split('\n')
    require(lines.first() == FormatVersion) { "Unsupported growing-up observation storage format" }
    return lines.drop(1).map { line ->
        val fields = line.split('\t')
        require(fields.size == 6 || fields.size == 12) { "Incomplete growing-up observation record" }
        val note = GrowingObservation(
            id = decode(fields[0]),
            personId = decode(fields[1]),
            stage = AgeStage.valueOf(fields[2]),
            note = decode(fields[3]),
            recordedOn = decodeDate(fields[4]),
            forVisitId = fields[5].takeIf(String::isNotEmpty)?.let(::decode),
        )
        val task = if (fields.size == 6) null else Task(
            id = decode(fields[6]),
            personId = note.personId,
            title = decode(fields[7]),
            date = decodeDate(fields[8]),
            status = TaskStatus.valueOf(fields[9]),
            time = fields[10].takeIf(String::isNotEmpty)?.let(::decodeTime),
            sourceDocumentId = fields[11].takeIf(String::isNotEmpty)?.let(::decode),
            visitId = note.forVisitId,
        )
        GrowingObservationEntry(note, task)
    }
}

private fun encode(value: String?): String = value?.let { Base64.encode(it.encodeToByteArray()) }.orEmpty()

private fun decode(value: String): String = Base64.decode(value).decodeToString(throwOnInvalidSequence = true)

private fun decodeDate(value: String): CareDate {
    val parts = value.split('-')
    require(parts.size == 3) { "Invalid stored growing-up date: $value" }
    return CareDate(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
}

private fun decodeTime(value: String): CareTime {
    val parts = value.split(':')
    require(parts.size == 2) { "Invalid stored growing-up time: $value" }
    return CareTime(parts[0].toInt(), parts[1].toInt())
}
