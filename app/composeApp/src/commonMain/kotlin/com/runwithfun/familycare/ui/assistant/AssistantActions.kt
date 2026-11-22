package com.runwithfun.familycare.ui.assistant

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.runwithfun.familycare.demo.DemoFamily
import com.runwithfun.familycare.domain.CarePlan
import com.runwithfun.familycare.domain.OriginalContent
import com.runwithfun.familycare.domain.Task
import com.runwithfun.familycare.domain.Visit
import com.runwithfun.familycare.domain.VisitStatus
import com.runwithfun.familycare.session.CareSession

private const val RecoveryExcerpt = "Bring any questions about your recovery."

internal const val ConfirmedActionKey = "family-care.demo.confirmed-assistant-action"

internal interface AssistantActionStorage {
    fun read(): String?
    fun write(actionId: String?)
}

@Composable
internal expect fun rememberAssistantActionStorage(): AssistantActionStorage

@Composable
fun rememberAssistantActions(session: CareSession): AssistantActions {
    val storage = rememberAssistantActionStorage()
    return remember(session, storage) { AssistantActions(session.plan, storage, session) }
}

class AssistantActions internal constructor(
    private val plan: CarePlan,
    private val storage: AssistantActionStorage,
    private val session: CareSession? = null,
) {
    private val proposal = DemoFamily.recoveryQuestion

    val task: Task
        get() = plan.event(proposal.task.id) as? Task ?: proposal.task
    val personName = plan.people.firstOrNull { it.id == task.personId }?.displayName
    private val source = plan.documents.firstOrNull {
        it.id == task.sourceDocumentId && it.personId == task.personId
    }
    val sourceName = source?.original?.fileName
    val originalText = (source?.original?.content as? OriginalContent.Text)?.value
    val excerpt = RecoveryExcerpt.takeIf { originalText?.contains(it) == true }
    private val hasSource = personName != null && excerpt != null
    private val currentVisit: Visit?
        get() = proposal.task.visitId?.let(plan::event) as? Visit
    val visitDate
        get() = currentVisit?.date
    val visitHasChanged: Boolean
        get() {
            if (!hasSource || confirmed) return false
            val visit = currentVisit ?: return true
            val scheduled = visit.status == VisitStatus.BOOKED || visit.status == VisitStatus.PLANNED
            return !scheduled || visit.personId != proposal.task.personId || visit.date != DemoFamily.recoveryVisitDate
        }
    val canConfirm: Boolean
        get() = hasSource && !visitHasChanged

    var confirmed by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    init {
        try {
            val persistedTask = session?.event(proposal.task.id) as? Task
            if (hasSource && (persistedTask != null || storage.read() == task.id)) {
                if (persistedTask == null) saveTask()
                confirmed = true
            }
        } catch (error: Exception) {
            reportFailure("restore", error)
        }
    }

    fun confirm(): Boolean {
        if (confirmed) return true
        if (!canConfirm) return false
        return try {
            val previousActionId = storage.read()
            try {
                storage.write(task.id)
                saveTask()
            } catch (error: Exception) {
                try {
                    storage.write(previousActionId)
                } catch (rollbackError: Exception) {
                    println("Could not roll back local assistant action ${task.id}\n${rollbackError.stackTraceToString()}\nRequest: ${proposal.task}")
                }
                throw error
            }
            confirmed = true
            errorMessage = null
            true
        } catch (error: Exception) {
            reportFailure("save", error)
            false
        }
    }

    private fun saveTask() {
        if (session == null) {
            plan.confirm(proposal)
        } else {
            val existing = session.event(proposal.task.id)
            require(existing == null || existing == proposal.task) { "Event ID already exists with different details: ${proposal.task.id}" }
            if (existing == null) session.saveTask(proposal.task)
        }
    }

    private fun reportFailure(operation: String, error: Exception) {
        println("Could not $operation local assistant action ${task.id}\n${error.stackTraceToString()}\nRequest: ${proposal.task}")
        errorMessage = "Could not $operation the follow-up on this device."
    }
}
