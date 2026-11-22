package com.runwithfun.familycare.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.runwithfun.familycare.domain.CareDate
import com.runwithfun.familycare.domain.CareEvent
import com.runwithfun.familycare.domain.CareTime
import com.runwithfun.familycare.domain.Task
import com.runwithfun.familycare.domain.TaskStatus
import com.runwithfun.familycare.domain.Visit
import com.runwithfun.familycare.domain.VisitStatus
import kotlin.io.encoding.Base64

internal const val CarePlanEventsKey = "family-care.demo.care-plan-events"

data class CarePlanEventSnapshot(
    val nextLocalId: Int,
    val events: List<CareEvent>,
)

internal interface CarePlanEventStore {
    fun load(): String?
    fun save(value: String)
}

@Composable
internal expect fun rememberCarePlanEventStore(): CarePlanEventStore

@Composable
fun rememberCareSession(plan: com.runwithfun.familycare.domain.CarePlan): CareSession {
    val store = rememberCarePlanEventStore()
    return remember(plan, store) { CareSession(plan, store) }
}

internal fun encodeCarePlanEvents(snapshot: CarePlanEventSnapshot): String = buildString {
    appendLine("v1")
    append("nextLocalId=${snapshot.nextLocalId}")
    for (event in snapshot.events) {
        append('\n')
        append(encodeEvent(event))
    }
}

internal fun decodeCarePlanEvents(encoded: String): CarePlanEventSnapshot {
    // Do not trim event rows: trailing empty fields are meaningful tabs.
    val lines = encoded.lineSequence().map { it.trimEnd('\r') }.filter { it.isNotBlank() }.toList()
    require(lines.isNotEmpty() && lines.first() == "v1") { "Unsupported care-plan event store version" }
    var nextLocalId = 1
    val events = mutableListOf<CareEvent>()
    for (line in lines.drop(1)) {
        when {
            line.startsWith("nextLocalId=") -> {
                nextLocalId = line.removePrefix("nextLocalId=").toInt()
                require(nextLocalId >= 1) { "nextLocalId must be at least 1" }
            }
            else -> events += decodeEvent(line)
        }
    }
    return CarePlanEventSnapshot(nextLocalId, events)
}

private fun encodeEvent(event: CareEvent): String = when (event) {
    is Visit -> listOf(
        "visit",
        event.id,
        event.personId,
        encodeField(event.title),
        event.date.toString(),
        event.time?.toString().orEmpty(),
        encodeField(event.provider),
        event.status.name,
        event.sourceDocumentId.orEmpty(),
    ).joinToString("\t")
    is Task -> listOf(
        "task",
        event.id,
        event.personId,
        encodeField(event.title),
        event.date.toString(),
        event.time?.toString().orEmpty(),
        event.status.name,
        event.sourceDocumentId.orEmpty(),
        event.visitId.orEmpty(),
    ).joinToString("\t")
}

private fun decodeEvent(line: String): CareEvent {
    val parts = line.split('\t')
    return when (parts.firstOrNull()) {
        "visit" -> {
            require(parts.size == 9) { "Visit record has ${parts.size} fields" }
            Visit(
                id = parts[1],
                personId = parts[2],
                title = decodeField(parts[3]),
                date = parseDate(parts[4]),
                time = parseTime(parts[5]),
                provider = decodeField(parts[6]),
                status = VisitStatus.valueOf(parts[7]),
                sourceDocumentId = parts[8].ifEmpty { null },
            )
        }
        "task" -> {
            require(parts.size == 9) { "Task record has ${parts.size} fields" }
            Task(
                id = parts[1],
                personId = parts[2],
                title = decodeField(parts[3]),
                date = parseDate(parts[4]),
                time = parseTime(parts[5]),
                status = TaskStatus.valueOf(parts[6]),
                sourceDocumentId = parts[7].ifEmpty { null },
                visitId = parts[8].ifEmpty { null },
            )
        }
        else -> error("Unknown care-plan event kind: ${parts.firstOrNull()}")
    }
}

private fun encodeField(value: String): String = Base64.encode(value.encodeToByteArray())

private fun decodeField(value: String): String =
    Base64.decode(value).decodeToString(throwOnInvalidSequence = true)

private fun parseDate(raw: String): CareDate {
    val parts = raw.split('-')
    require(parts.size == 3) { "Invalid date: $raw" }
    return CareDate(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
}

private fun parseTime(raw: String): CareTime? {
    if (raw.isEmpty()) return null
    val parts = raw.split(':')
    require(parts.size == 2) { "Invalid time: $raw" }
    return CareTime(parts[0].toInt(), parts[1].toInt())
}
