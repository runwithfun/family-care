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

private const val RecoveryExcerpt = "Bring any questions about your recovery."

internal const val ConfirmedActionKey = "family-care.demo.confirmed-assistant-action"

internal interface AssistantActionStorage {
    fun read(): String?
    fun write(actionId: String?)
}

@Composable
internal expect fun rememberAssistantActionStorage(): AssistantActionStorage

@Composable
fun rememberAssistantActions(plan: CarePlan): AssistantActions {
    val storage = rememberAssistantActionStorage()
    return remember(plan, storage) { AssistantActions(plan, storage) }
}

class AssistantActions internal constructor(
    private val plan: CarePlan,
    private val storage: AssistantActionStorage,
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
    val canConfirm = personName != null && excerpt != null

    var confirmed by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    init {
        try {
            if (canConfirm && storage.read() == task.id) {
                plan.confirm(proposal)
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
                plan.confirm(proposal)
            } catch (error: Exception) {
                try {
                    storage.write(previousActionId)
                } catch (rollbackError: Exception) {
                    println("Could not roll back local assistant action ${task.id}\n${rollbackError.stackTraceToString()}")
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

    private fun reportFailure(operation: String, error: Exception) {
        println("Could not $operation local assistant action ${task.id}\n${error.stackTraceToString()}")
        errorMessage = "Could not $operation the follow-up on this device."
    }
}
