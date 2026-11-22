package com.runwithfun.familycare.ui.assistant

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.runwithfun.familycare.demo.DemoFamily
import com.runwithfun.familycare.domain.CareDate
import com.runwithfun.familycare.domain.FamilyMember
import com.runwithfun.familycare.domain.FamilyRole
import com.runwithfun.familycare.domain.Task
import com.runwithfun.familycare.domain.Visit
import com.runwithfun.familycare.records.RecordsController
import com.runwithfun.familycare.records.RecordsLocalStore
import com.runwithfun.familycare.records.RetrievedOriginal
import com.runwithfun.familycare.records.rememberRecordsLocalStore
import com.runwithfun.familycare.session.CareSession

internal data class QuestionProposal(
    val id: String,
    val revision: Int,
    val personId: String,
    val title: String,
    val dateText: String,
    val visit: Visit?,
    val citation: AssistantCitation,
)

internal enum class ConfirmQuestionResult {
    SAVED, ALREADY_SAVED, STALE_PROPOSAL, SOURCE_UNAVAILABLE, INVALID_DRAFT, STORAGE_FAILURE,
}

@Composable
internal fun rememberAssistantConversation(session: CareSession, records: RecordsController): AssistantConversation {
    val store = rememberRecordsLocalStore()
    return remember(session, records, store) { AssistantConversation(session, records, store) }
}

internal class AssistantConversation(
    private val session: CareSession,
    private val records: RecordsController,
    private val store: RecordsLocalStore,
) {
    private val tools = AssistantTools(session, records)
    private val threads = mutableStateMapOf<String, List<AssistantTurn>>()
    private val workspaces = mutableStateMapOf<String, QuestionWorkspace>()
    private val visitSelections = mutableStateMapOf<String, String?>()
    private val needsReview = mutableStateMapOf<String, Boolean>()

    val people: List<FamilyMember> = session.people
    var personId by mutableStateOf((people.firstOrNull { it.role == FamilyRole.MOTHER } ?: people.first()).id)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    val visitId: String? get() = visitSelections[personId]
    val visits: List<Visit> get() = tools.visits(personId)
    val turns: List<AssistantTurn> get() = threads[personId].orEmpty()
    val proposal: QuestionProposal? get() = workspace.proposal?.takeUnless { savedQuestion(it.id) != null }
    val savedTask: Task? get() = savedQuestion(workspace.proposal?.id) ?: savedQuestion(workspace.receiptId)
    val proposalNeedsReview: Boolean
        get() = proposal?.let { draft ->
            needsReview[personId] == true || visitHasChanged(draft)
        } ?: false

    private val workspace get() = workspaces[personId] ?: QuestionWorkspace()
    private val scope get() = AssistantScope(personId, visitId)

    init {
        for (person in people) {
            val saved = restoreWorkspace(person.id)
            workspaces[person.id] = saved
            visitSelections[person.id] = saved.proposal?.visit?.id
        }
    }

    fun selectPerson(id: String) {
        if (id == personId || people.none { it.id == id }) return
        personId = id
        message = null
    }

    fun selectVisit(id: String?) {
        if (id != null && tools.openVisit(AssistantScope(personId, id)) == null) return
        visitSelections[personId] = id
        message = null
        val last = turns.lastOrNull()
        if (id != null && last?.reply is AssistantReply.ChooseVisit) respond(last.prompt, replaceLast = true)
    }

    fun send(text: String) {
        if (text.isNotBlank()) respond(text.trim())
    }

    fun importDemoRecord() {
        if (personId != DemoFamily.childId) return
        try {
            if (!records.isPdfImported() && !records.importSyntheticImmunizationPdf()) {
                message = records.errorMessage
                return
            }
            message = null
            val last = turns.lastOrNull()
            if (last?.reply is AssistantReply.ImportRecord) respond(last.prompt, replaceLast = true)
        } catch (error: Exception) {
            reportFailure("import the sample original", DemoFamily.immunizationDocumentId, error)
            message = "Could not import the sample original on this device."
        }
    }

    fun openOriginal(citation: AssistantCitation): RetrievedOriginal? =
        try {
            tools.open(scope, citation).also { original ->
                message = if (original == null) "This original is unavailable for the current selection." else null
            }
        } catch (error: Exception) {
            reportFailure("open a cited original", citation, error)
            message = "Could not open the original on this device."
            null
        }

    fun editProposal(title: String, dateText: String) {
        val previous = proposal ?: return
        if (title == previous.title && dateText == previous.dateText) return
        val edited = previous.copy(title = title, dateText = dateText, revision = previous.revision + 1)
        if (tryWorkspace(workspace.copy(proposal = edited))) message = null
    }

    fun discardProposal() {
        if (proposal == null) return
        if (tryWorkspace(workspace.copy(proposal = null))) {
            needsReview.remove(personId)
            message = "Draft discarded."
        }
    }

    fun refreshProposal() {
        val previous = proposal ?: return
        val visit = tools.openVisit(scope)
        if (visit == null && (previous.visit != null || visitId != null)) {
            message = "Choose an open visit to review this draft."
            return
        }
        try {
            val citation = tools.currentCitation(scope, previous.citation.documentId)
            if (citation == null) {
                message = "The cited original is unavailable. Open Records to check the source."
                return
            }
            val dateText = if (previous.dateText == defaultDate(previous.visit)) defaultDate(visit) else previous.dateText
            val refreshed = previous.copy(
                revision = previous.revision + 1,
                dateText = dateText,
                visit = visit,
                citation = citation,
            )
            if (tryWorkspace(workspace.copy(proposal = refreshed))) {
                needsReview.remove(personId)
                message = "Preview updated. Review the question and date before saving."
            }
        } catch (error: Exception) {
            reportFailure("refresh a question preview", previous, error)
            message = "Could not refresh this preview on the device."
        }
    }

    fun confirm(proposalId: String, revision: Int): ConfirmQuestionResult {
        val saved = savedQuestion(proposalId)
        if (saved != null) {
            if (workspace.proposal?.id == saved.id) rememberReceipt(saved)
            return outcome(ConfirmQuestionResult.ALREADY_SAVED, "This question is already in your plan.")
        }
        val draft = proposal
        if (draft == null || draft.id != proposalId || draft.revision != revision) {
            return outcome(ConfirmQuestionResult.STALE_PROPOSAL, "Review the current preview before saving.")
        }
        if (visitHasChanged(draft)) {
            needsReview[personId] = true
            return outcome(ConfirmQuestionResult.STALE_PROPOSAL, "The visit changed. Review the updated preview.")
        }
        val date = parseQuestionDate(draft.dateText)
        if (draft.title.isBlank() || date == null) {
            return outcome(ConfirmQuestionResult.INVALID_DRAFT, "Enter a question and a valid date in YYYY-MM-DD format.")
        }
        return try {
            val citation = tools.currentCitation(scope, draft.citation.documentId)
            if (citation == null) {
                needsReview[personId] = true
                return outcome(ConfirmQuestionResult.SOURCE_UNAVAILABLE, "The cited original is unavailable. Open Records to check it.")
            }
            if (citation != draft.citation) {
                needsReview[personId] = true
                return outcome(ConfirmQuestionResult.STALE_PROPOSAL, "The original changed. Review the updated preview.")
            }
            val task = Task(
                id = draft.id,
                personId = draft.personId,
                title = draft.title,
                date = date,
                sourceDocumentId = draft.citation.documentId,
                visitId = draft.visit?.id,
            )
            check(session.event(task.id) == null) { "Reserved assistant task ID already exists: ${task.id}" }
            session.saveTask(task)
            rememberReceipt(task)
            outcome(ConfirmQuestionResult.SAVED, "Question added to your plan.")
        } catch (error: Exception) {
            reportFailure("confirm a clinician question", draft, error)
            outcome(ConfirmQuestionResult.STORAGE_FAILURE, "Could not save this question on the device. Try again.")
        }
    }

    private fun respond(prompt: String, replaceLast: Boolean = false) {
        message = null
        val reply = try {
            val namedPerson = people.singleOrNull { person ->
                val name = Regex.escape(person.displayName)
                Regex("(?<![\\p{L}])$name(?![\\p{L}])", RegexOption.IGNORE_CASE).containsMatchIn(prompt)
            }
            if (namedPerson != null && namedPerson.id != personId) {
                AssistantReply.Answer("Switch to ${namedPerson.displayName} to ask about their records or visit.")
            } else {
                when (val result = tools.run(scope, parseAssistantIntent(prompt))) {
                    is AssistantToolResult.Reply -> result.value
                    is AssistantToolResult.Question -> preview(result)
                }
            }
        } catch (error: Exception) {
            reportFailure("answer an Assistant request", prompt, error)
            AssistantReply.Answer("Could not read the local record or save a preview. Try again.")
        }
        val history = if (replaceLast) turns.dropLast(1) else turns
        threads[personId] = history + AssistantTurn(prompt, reply)
    }

    private fun preview(question: AssistantToolResult.Question): AssistantReply.Answer {
        if (proposal != null) return AssistantReply.Answer("Review or discard your current draft before starting a new one.")
        val id = session.allocateLocalId(QuestionTaskPrefix)
        val draft = QuestionProposal(
            id = id,
            revision = 1,
            personId = personId,
            title = question.title,
            dateText = defaultDate(question.visit),
            visit = question.visit,
            citation = question.citation,
        )
        persistWorkspace(workspace.copy(proposal = draft))
        needsReview.remove(personId)
        return AssistantReply.Answer("Review your question below before adding it to your plan.")
    }

    private fun defaultDate(visit: Visit?): String = (visit?.date ?: DemoFamily.today).toString()

    private fun visitHasChanged(draft: QuestionProposal): Boolean =
        draft.visit?.id != visitId || draft.visit != tools.openVisit(scope)

    private fun savedQuestion(id: String?): Task? {
        if (id == null || !id.startsWith("$QuestionTaskPrefix-")) return null
        return (session.event(id) as? Task)?.takeIf { it.personId == personId }
    }

    private fun rememberReceipt(task: Task) {
        val pending = workspace.proposal
        val next = workspace.copy(proposal = pending?.takeUnless { it.id == task.id }, receiptId = task.id)
        if (next == workspace) return
        // A persisted task is the receipt. If this update fails, relaunch can still find it from the draft ID.
        tryWorkspace(next)
        if (pending?.id == task.id) needsReview.remove(personId)
    }

    private fun outcome(result: ConfirmQuestionResult, explanation: String): ConfirmQuestionResult {
        message = explanation
        return result
    }

    private fun tryWorkspace(next: QuestionWorkspace): Boolean = try {
        persistWorkspace(next)
        true
    } catch (error: Exception) {
        reportFailure("save a question preview or receipt", next, error)
        message = "Could not save the preview on this device. Try again."
        false
    }

    private fun persistWorkspace(next: QuestionWorkspace) {
        store.write(questionWorkspaceKey(personId), encodeQuestionWorkspace(next))
        workspaces[personId] = next
    }

    private fun restoreWorkspace(owner: String): QuestionWorkspace {
        var raw: String? = null
        return try {
            raw = store.read(questionWorkspaceKey(owner))
            raw?.let { decodeQuestionWorkspace(it, owner) } ?: QuestionWorkspace()
        } catch (error: Exception) {
            reportFailure("restore question previews for $owner", raw, error)
            QuestionWorkspace()
        }
    }
}

private const val QuestionTaskPrefix = "assistant-question"

internal fun parseQuestionDate(input: String): CareDate? {
    val raw = input.trim()
    if (!Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(raw)) return null
    val (year, month, day) = raw.split('-').map(String::toInt)
    return runCatching { CareDate(year, month, day) }.getOrNull()
}

private fun reportFailure(operation: String, request: Any?, error: Throwable) {
    println("Could not $operation. Request: $request\n${error.stackTraceToString()}")
}
