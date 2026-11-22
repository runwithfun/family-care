package com.runwithfun.familycare.ui.assistant

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.runwithfun.familycare.demo.DemoFamily
import com.runwithfun.familycare.domain.FamilyMember
import com.runwithfun.familycare.domain.FamilyRole
import com.runwithfun.familycare.domain.Task
import com.runwithfun.familycare.domain.Visit
import com.runwithfun.familycare.records.RecordPdfPreview
import com.runwithfun.familycare.records.RecordsController
import com.runwithfun.familycare.records.RetrievedOriginal
import com.runwithfun.familycare.records.rememberRecordImportAction
import com.runwithfun.familycare.ui.home.monthAndDay

@Composable
internal fun LocalAssistantScreen(
    actions: AssistantActions,
    conversation: AssistantConversation,
    records: RecordsController,
    onSaved: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenTask: (Task) -> Unit,
) {
    val personId = conversation.personId
    val person = conversation.people.firstOrNull { it.id == personId }
    val isChild = person?.role == FamilyRole.CHILD
    val draftPrompt = if (isChild) {
        "Draft a question about the missing immunization date"
    } else {
        "Draft a question for my clinician"
    }
    val turns = conversation.turns
    val proposal = conversation.proposal
    val savedTask = conversation.savedTask
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    var prompt by remember(conversation, personId) { mutableStateOf("") }
    var source by remember(conversation, personId) { mutableStateOf<OpenedCitation?>(null) }
    var shownProposalId by remember(personId) { mutableStateOf<String?>(null) }
    var shownSavedTaskId by remember(personId) { mutableStateOf<String?>(null) }

    fun sendPrompt(text: String) {
        if (text.isBlank()) return
        focusManager.clearFocus()
        conversation.send(text)
        prompt = ""
    }

    fun openCitation(citation: AssistantCitation) {
        conversation.openOriginal(citation)?.let { source = OpenedCitation(citation, it) }
    }

    val addPdf = rememberRecordImportAction(records) { owner, _ ->
        if (owner == conversation.personId) sendPrompt("Find the original record")
    }

    LaunchedEffect(personId, turns.size, proposal?.id, savedTask?.id) {
        val targetIndex = when {
            proposal != null && proposal.id != shownProposalId -> turns.size + 1
            savedTask != null && savedTask.id != shownSavedTaskId -> turns.size + 1
            turns.isNotEmpty() -> turns.size
            else -> 0
        }
        shownProposalId = proposal?.id
        shownSavedTaskId = savedTask?.id
        listState.scrollToItem(targetIndex)
    }

    Column(
        modifier = Modifier.fillMaxSize().imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ConversationScope(
            conversation = conversation,
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()
                .padding(start = 20.dp, top = 14.dp, end = 20.dp),
        )
        LazyColumn(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().weight(1f),
            state = listState,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                PromptShortcuts(
                    showImmunizations = isChild,
                    draftPrompt = draftPrompt,
                    onPrompt = ::sendPrompt,
                    onAddPdf = { addPdf(personId) },
                )
                records.errorMessage?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
            items(turns) { turn ->
                ConversationTurn(
                    turn = turn,
                    selectedVisitId = conversation.visitId,
                    onSelectVisit = conversation::selectVisit,
                    onImport = { conversation.importDemoRecord() },
                    onOpenCitation = ::openCitation,
                    onDraft = if (proposal == null) ({ sendPrompt(draftPrompt) }) else null,
                )
            }
            if (proposal != null || savedTask != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        proposal?.let {
                            QuestionPreview(
                                proposal = it,
                                personLabel = person?.assistantLabel().orEmpty(),
                                needsReview = conversation.proposalNeedsReview,
                                onEdit = conversation::editProposal,
                                onOpenCitation = ::openCitation,
                                onConfirm = {
                                    focusManager.clearFocus()
                                    conversation.confirm(it.id, it.revision)
                                },
                                onDiscard = conversation::discardProposal,
                                onReview = conversation::refreshProposal,
                            )
                        }
                        savedTask?.let {
                            SavedQuestion(it, person?.assistantLabel().orEmpty()) { onOpenTask(it) }
                        }
                    }
                }
            }
            if (actions.task.personId == personId) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Sample follow-up",
                            modifier = Modifier.semantics { heading() },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        SampleFollowUp(actions, onSaved, onOpenCalendar)
                    }
                }
            }
        }
        ChatComposer(
            prompt = prompt,
            message = conversation.message,
            onEdit = { prompt = it },
            onSend = { sendPrompt(prompt) },
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
        )
    }

    source?.let { opened ->
        OriginalRecordDialog(
            title = "Original record",
            fileName = opened.citation.sourceLabel(),
            originalText = opened.original.textPreview,
            pdfBytes = opened.original.pdfBytes,
            pdfPage = opened.citation.page ?: 1,
            onClose = { source = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConversationScope(
    conversation: AssistantConversation,
    modifier: Modifier = Modifier,
) {
    val typography = MaterialTheme.typography
    var pickingVisit by remember(conversation.personId) { mutableStateOf(false) }
    val chosenVisit = conversation.visits.firstOrNull { it.id == conversation.visitId }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Assistant", Modifier.semantics { heading() }, style = typography.headlineSmall)
            conversation.people.forEach { member ->
                FilterChip(
                    selected = member.id == conversation.personId,
                    onClick = { conversation.selectPerson(member.id) },
                    label = { Text(member.assistantLabel()) },
                )
            }
        }
        Box {
            OutlinedButton(
                onClick = { pickingVisit = true },
                enabled = conversation.visitId != null || conversation.visits.isNotEmpty(),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(chosenVisit?.let { "${it.title} · ${it.date.monthAndDay()}" } ?: "Choose visit")
            }
            DropdownMenu(expanded = pickingVisit, onDismissRequest = { pickingVisit = false }) {
                DropdownMenuItem(
                    text = { Text("No visit selected") },
                    onClick = {
                        pickingVisit = false
                        conversation.selectVisit(null)
                    },
                )
                conversation.visits.forEach { visit ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(visit.title)
                                Text(visit.assistantDetails(), style = typography.bodySmall)
                            }
                        },
                        onClick = {
                            pickingVisit = false
                            conversation.selectVisit(visit.id)
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PromptShortcuts(
    showImmunizations: Boolean,
    draftPrompt: String,
    onPrompt: (String) -> Unit,
    onAddPdf: () -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(
            onClick = { onPrompt("Find the original record") },
            label = { Text("Find a record") },
        )
        AssistChip(
            onClick = { onPrompt("Help me prepare for the visit") },
            label = { Text("Prepare for a visit") },
        )
        AssistChip(
            onClick = { onPrompt(draftPrompt) },
            label = { Text("Draft a question") },
        )
        AssistChip(onClick = onAddPdf, label = { Text("Add a PDF") })
        if (showImmunizations) {
            AssistChip(
                onClick = { onPrompt("What is the missing immunization date?") },
                label = { Text("Check immunization date") },
            )
        }
    }
}

@Composable
private fun ConversationTurn(
    turn: AssistantTurn,
    selectedVisitId: String?,
    onSelectVisit: (String) -> Unit,
    onImport: () -> Unit,
    onOpenCitation: (AssistantCitation) -> Unit,
    onDraft: (() -> Unit)?,
) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            modifier = Modifier.align(Alignment.End).fillMaxWidth(0.88f),
            shape = RoundedCornerShape(16.dp),
            color = colors.primaryContainer,
            contentColor = colors.onPrimaryContainer,
        ) {
            Text(turn.prompt, Modifier.padding(12.dp), style = typography.bodyMedium)
        }
        Column(
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(turn.reply.text, style = typography.bodyLarge)
            when (val reply = turn.reply) {
                is AssistantReply.Answer -> {
                    reply.citations.forEach { citation ->
                        CitationCard(citation) { onOpenCitation(citation) }
                    }
                    if (reply.citations.isNotEmpty() && onDraft != null) {
                        OutlinedButton(onClick = onDraft, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Draft a question")
                        }
                    }
                }
                is AssistantReply.ChooseVisit -> reply.visits.forEach { visit ->
                    OutlinedButton(
                        onClick = { onSelectVisit(visit.id) },
                        enabled = visit.id != selectedVisitId,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text("${visit.title} · ${visit.assistantDetails()}")
                    }
                }
                is AssistantReply.ImportRecord -> OutlinedButton(
                    onClick = onImport,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("Import synthetic clinic PDF")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionPreview(
    proposal: QuestionProposal,
    personLabel: String,
    needsReview: Boolean,
    onEdit: (String, String) -> Unit,
    onOpenCitation: (AssistantCitation) -> Unit,
    onConfirm: () -> Unit,
    onDiscard: () -> Unit,
    onReview: () -> Unit,
) {
    val typography = MaterialTheme.typography
    Surface(
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Review your question", Modifier.semantics { heading() }, style = typography.titleLarge)
            Text(personLabel, style = typography.labelLarge)
            proposal.visit?.let { Text("${it.title} · ${it.assistantDetails()}", style = typography.bodyMedium) }
            OutlinedTextField(
                value = proposal.title,
                onValueChange = { onEdit(it, proposal.dateText) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Question for clinician") },
                minLines = 2,
                maxLines = 4,
            )
            OutlinedTextField(
                value = proposal.dateText,
                onValueChange = { onEdit(proposal.title, it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Due date (YYYY-MM-DD)") },
                singleLine = true,
            )
            CitationCard(proposal.citation) { onOpenCitation(proposal.citation) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (needsReview) {
                    Button(onClick = onReview, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Review changes")
                    }
                } else {
                    Button(
                        onClick = onConfirm,
                        enabled = proposal.title.isNotBlank() && proposal.dateText.isNotBlank(),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text("Confirm question")
                    }
                }
                TextButton(onClick = onDiscard, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Discard")
                }
            }
        }
    }
}

@Composable
private fun SavedQuestion(task: Task, personLabel: String, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    Surface(
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        color = colors.secondaryContainer,
        contentColor = colors.onSecondaryContainer,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Question saved to your plan", style = typography.titleMedium)
            Text("$personLabel · ${task.date.monthAndDay()}", style = typography.labelLarge)
            Text(task.title, style = typography.bodyMedium)
            TextButton(onClick = onOpen, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Open saved question")
            }
        }
    }
}

@Composable
private fun CitationCard(citation: AssistantCitation, onOpen: () -> Unit) {
    SourceExcerpt(
        sourceLabel = citation.sourceLabel(),
        excerpt = citation.quote.takeIf { it.isNotBlank() },
        openLabel = "Open original",
        onOpen = onOpen,
    )
}

private fun AssistantCitation.sourceLabel(): String = buildString {
    append(fileName)
    page?.let { append(" · Page $it") }
    documentDate?.let { append(" · Document dated $it") }
}

@Composable
private fun ChatComposer(
    prompt: String,
    message: String?,
    onEdit: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier = modifier, tonalElevation = 2.dp) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            message?.let {
                Text(
                    it,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = onEdit,
                    modifier = Modifier.weight(1f),
                    label = { Text("Message Assistant") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                )
                Button(
                    onClick = onSend,
                    enabled = prompt.isNotBlank(),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("Send")
                }
            }
        }
    }
}

private data class OpenedCitation(val citation: AssistantCitation, val original: RetrievedOriginal)

@Composable
private fun OriginalRecordDialog(
    title: String,
    fileName: String?,
    originalText: String?,
    pdfBytes: ByteArray? = null,
    pdfPage: Int = 1,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title, Modifier.semantics { heading() }) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                fileName?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                pdfBytes?.let { RecordPdfPreview(it, Modifier.fillMaxWidth(), page = pdfPage) }
                originalText?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Close")
            }
        },
    )
}

private fun FamilyMember.assistantLabel(): String = if (role == FamilyRole.MOTHER) "For you" else displayName

private fun Visit.assistantDetails(): String = buildString {
    append(date.monthAndDay())
    time?.let { append(" at $it") }
    append(" · ")
    append(status.name.lowercase().replaceFirstChar { it.uppercaseChar() })
}

@Composable
private fun SampleFollowUp(actions: AssistantActions, onSaved: () -> Unit, onOpenCalendar: () -> Unit) {
    var showSource by remember(actions) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    Surface(
        color = lerp(colors.surface, colors.primaryContainer, 0.18f),
        contentColor = colors.onSurface,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (actions.canConfirm) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (actions.confirmed) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = colors.secondary,
                                modifier = Modifier.size(18.dp),
                            )
                            Text("Added to your plan", style = typography.labelLarge)
                        }
                    }
                    val recipient = if (actions.task.personId == DemoFamily.motherId) {
                        "For you"
                    } else {
                        actions.personName
                    }
                    Text(
                        "$recipient · ${actions.task.date.monthAndDay()}",
                        style = typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                    Text(actions.task.title, style = typography.titleLarge)
                }
            }

            SourceExcerpt(
                sourceLabel = "From the visit note",
                excerpt = actions.excerpt,
                openLabel = "Open original note",
                onOpen = if (actions.originalText != null) ({ showSource = true }) else null,
            )

            actions.errorMessage?.let { message ->
                Text(
                    message,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = typography.bodyMedium,
                    color = colors.error,
                )
            }

            if (actions.visitHasChanged) {
                Text("The visit has changed. Check its date or status in Calendar.", style = typography.bodyMedium)
                TextButton(onClick = onOpenCalendar, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Open Calendar")
                }
            }

            if (actions.canConfirm) {
                val actionModifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                if (actions.confirmed) {
                    OutlinedButton(
                        onClick = onSaved,
                        modifier = actionModifier,
                        border = BorderStroke(1.dp, colors.secondary),
                    ) {
                        Text(if (actions.task.date == DemoFamily.today) "View Today" else "View in plan")
                    }
                } else {
                    Button(
                        onClick = { if (actions.confirm()) onSaved() },
                        modifier = actionModifier,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primaryContainer,
                            contentColor = colors.onPrimaryContainer,
                        ),
                    ) {
                        Text("Add to Today")
                    }
                }
            }
        }
    }

    if (showSource) {
        OriginalRecordDialog(
            title = "Original visit note",
            fileName = actions.sourceName,
            originalText = actions.originalText,
            onClose = { showSource = false },
        )
    }
}

@Composable
private fun SourceExcerpt(sourceLabel: String, excerpt: String?, openLabel: String, onOpen: (() -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    Surface(
        color = colors.surfaceVariant,
        contentColor = colors.onSurface,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(sourceLabel, style = typography.labelLarge, color = colors.onSurfaceVariant)
            Text(
                excerpt?.let { "“$it”" } ?: "The source excerpt is unavailable.",
                style = typography.bodyLarge,
            )
            if (onOpen != null) {
                TextButton(
                    onClick = onOpen,
                    modifier = Modifier.heightIn(min = 48.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    Text(openLabel, textDecoration = TextDecoration.Underline)
                }
            }
        }
    }
}
