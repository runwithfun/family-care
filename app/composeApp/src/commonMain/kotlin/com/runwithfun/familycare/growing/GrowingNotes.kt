package com.runwithfun.familycare.growing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.runwithfun.familycare.domain.AgeStage
import com.runwithfun.familycare.domain.CareDate
import com.runwithfun.familycare.domain.CarePlan
import com.runwithfun.familycare.domain.GrowingObservation
import com.runwithfun.familycare.domain.GrowingObservationEntry
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

internal const val GrowingNotesKey = "family-care.growing-observations"

internal interface GrowingNotesStorage {
    fun read(): String?
    fun write(value: String?)
}

@Composable
internal expect fun rememberGrowingNotesStorage(): GrowingNotesStorage

@Composable
internal fun rememberGrowingNotes(plan: CarePlan): GrowingNotes {
    val storage = rememberGrowingNotesStorage()
    return remember(plan, storage) { GrowingNotes(plan, storage) }
}

internal class GrowingNotes(
    private val plan: CarePlan,
    private val storage: GrowingNotesStorage,
) {
    private var saved = restore()
    private var history by mutableStateOf(planHistory())

    fun observations(personId: String, stage: AgeStage? = null): List<GrowingObservation> =
        history.filter { it.personId == personId && (stage == null || it.stage == stage) }

    @OptIn(ExperimentalUuidApi::class)
    fun save(personId: String, stage: AgeStage, note: String, recordedOn: CareDate): GrowingObservation {
        try {
            val previous = saved.getOrThrow()
            val observation = GrowingObservation(
                id = "growing-${Uuid.random()}",
                personId = personId,
                stage = stage,
                note = note.trim(),
                recordedOn = recordedOn,
            )
            val entry = plan.prepareGrowingObservation(observation)
            val entries = previous.entries + entry
            val payload = encodeGrowingNotes(entries)
            try {
                storage.write(payload)
                plan.recordGrowingObservations(listOf(entry))
            } catch (error: Exception) {
                try {
                    storage.write(previous.payload)
                } catch (rollback: Exception) {
                    if (rollback !== error) error.addSuppressed(rollback)
                }
                throw error
            }
            saved = Result.success(StoredGrowingNotes(payload, entries))
            history = planHistory()
            return entry.observation
        } catch (error: Exception) {
            println("Could not save local observation for $personId ($stage, $recordedOn)\n${error.stackTraceToString()}")
            throw error
        }
    }

    private fun restore(): Result<StoredGrowingNotes> = runCatching {
        val payload = storage.read()
        val entries = payload?.let(::decodeGrowingNotes).orEmpty()
        plan.recordGrowingObservations(entries)
        StoredGrowingNotes(payload, entries)
    }.onFailure { error ->
        println("Could not restore local growing-up observations\n${error.stackTraceToString()}")
    }

    private fun planHistory(): List<GrowingObservation> = plan.people.flatMap { plan.observations(it.id) }

    private data class StoredGrowingNotes(
        val payload: String?,
        val entries: List<GrowingObservationEntry>,
    )
}
