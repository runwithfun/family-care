package com.runwithfun.familycare.records

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.runwithfun.familycare.demo.DemoFamily
import com.runwithfun.familycare.domain.CareDocument
import com.runwithfun.familycare.domain.DocumentKind
import com.runwithfun.familycare.domain.FamilyMember
import com.runwithfun.familycare.domain.FamilyRole
import com.runwithfun.familycare.domain.Task
import com.runwithfun.familycare.domain.TaskStatus
import com.runwithfun.familycare.domain.Visit
import com.runwithfun.familycare.domain.hasGaps
import com.runwithfun.familycare.domain.isComplete
import com.runwithfun.familycare.domain.isConfirmed
import com.runwithfun.familycare.domain.isExplicitlyUnresolved
import com.runwithfun.familycare.domain.isUntouched
import com.runwithfun.familycare.domain.providerFollowUpMessage
import com.runwithfun.familycare.domain.shownValue

private val NarrowWidth = 600.dp

@Composable
fun RecordsScreen(
    records: RecordsController,
    people: Map<String, FamilyMember>,
    selectedDocumentId: String?,
    onSelectDocument: (String?) -> Unit,
    onOpenTask: (Task) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = selectedDocumentId?.let { records.document(it) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (selected == null) {
            Text("Records", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
        }

        records.errorMessage?.let { message ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    message,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        if (selected == null) {
            RecordsOverview(
                records = records,
                people = people,
                onOpenDocument = { onSelectDocument(it) },
            )
        } else {
            DocumentDetail(
                records = records,
                document = selected,
                person = people.getValue(selected.personId),
                onBack = { onSelectDocument(null) },
                onOpenTask = onOpenTask,
            )
        }
    }
}

@Composable
private fun RecordsOverview(
    records: RecordsController,
    people: Map<String, FamilyMember>,
    onOpenDocument: (String) -> Unit,
) {
    if (!records.isPdfImported()) {
        Button(onClick = { records.importSyntheticImmunizationPdf() }) {
            Text("Import synthetic clinic PDF")
        }
    } else {
        Text("Synthetic clinic PDF is stored on this device.", style = MaterialTheme.typography.bodyMedium)
    }

    SectionTitle("Documents")
    for (document in records.documents()) {
        val person = people.getValue(document.personId)
        DocumentRow(document, person, onClick = { onOpenDocument(document.id) })
    }

    HorizontalDivider()
    SectionTitle("Documented immunizations")
    if (records.plan.recordedImmunizations.isEmpty()) {
        Text("No documented immunizations yet.", style = MaterialTheme.typography.bodyMedium)
    } else {
        for (record in records.plan.recordedImmunizations) {
            val person = people.getValue(record.personId)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(record.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${person.displayName} · ${record.dateAdministered}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Source snapshot from document (not rewritten by field corrections)",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    HorizontalDivider()
    SectionTitle("Public references")
    Text(
        "Schedules from public health sites. These are not your child's record.",
        style = MaterialTheme.typography.bodyMedium,
    )
    for (reference in records.plan.immunizationReferences) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "${reference.authority} (${reference.jurisdiction})",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(reference.sourceUrl, style = MaterialTheme.typography.bodySmall)
        }
    }

    HorizontalDivider()
    SectionTitle("Appointments")
    for (visit in records.plan.appointments()) {
        AppointmentRow(visit, people.getValue(visit.personId))
    }
}

@Composable
private fun DocumentRow(
    document: CareDocument,
    person: FamilyMember,
    onClick: () -> Unit,
) {
    val status = when {
        document.isComplete() -> "Complete"
        document.hasGaps() -> "Needs clinic follow-up"
        else -> "Ready to review"
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(document.original.fileName, style = MaterialTheme.typography.titleMedium)
            Text(
                "${kindLabel(document.kind)} · ${person.displayName} · $status",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun AppointmentRow(visit: Visit, person: FamilyMember) {
    val owner = if (person.role == FamilyRole.MOTHER) "For you" else person.displayName
    val details = listOfNotNull(
        owner,
        visit.date.toString(),
        visit.time?.toString(),
        visit.provider,
        visit.status.name.lowercase(),
    ).joinToString(" · ")
    Column {
        Text(visit.title, style = MaterialTheme.typography.titleMedium)
        Text(details, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DocumentDetail(
    records: RecordsController,
    document: CareDocument,
    person: FamilyMember,
    onBack: () -> Unit,
    onOpenTask: (Task) -> Unit,
) {
    // Observe controller revision so Save refreshes field state.
    records.revision
    val current = records.document(document.id) ?: document

    TextButton(onClick = onBack) {
        Text("Back to records")
    }
    Text(
        kindLabel(current.kind),
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.headlineLarge,
    )
    Text(
        "${person.displayName} · ${current.original.fileName}",
        style = MaterialTheme.typography.bodyMedium,
    )

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val stack = maxWidth < NarrowWidth
        if (stack) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OriginalPanel(records, current)
                ExtractedPanel(records, current)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OriginalPanel(records, current, modifier = Modifier.weight(1f))
                ExtractedPanel(records, current, modifier = Modifier.weight(1f))
            }
        }
    }

    current.providerFollowUpMessage()?.let { message ->
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Provider follow-up", style = MaterialTheme.typography.titleMedium)
                Text(message, style = MaterialTheme.typography.bodyMedium)
                val linked = records.providerRequest(current.id)
                if (linked != null) {
                    TextButton(onClick = { onOpenTask(linked) }) {
                        Text(if (linked.status == TaskStatus.DONE) "View completed request" else "View provider request")
                    }
                } else {
                    OutlinedButton(onClick = { records.ensureProviderRequest(current.id)?.let(onOpenTask) }) {
                        Text("Add provider request")
                    }
                }
            }
        }
    }

    val openRequests = records.openRequestTasks(current.id)
    if (openRequests.isNotEmpty()) {
        SectionTitle("Open requests")
        for (task in openRequests) {
            Text("${task.title} · ${task.status.name.lowercase()}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun OriginalPanel(
    records: RecordsController,
    document: CareDocument,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Original", style = MaterialTheme.typography.titleMedium)
        if (document.id == DemoFamily.immunizationDocumentId && !records.isPdfImported(document.id)) {
            Button(onClick = { records.importSyntheticImmunizationPdf() }) {
                Text("Import synthetic clinic PDF")
            }
        } else {
            Button(onClick = { records.reopenOriginal(document.id) }) {
                Text("Open stored original")
            }
        }
        val opened = records.openedOriginal?.takeIf { it.documentId == document.id }
        if (opened != null) {
            opened.pdfBytes?.let { RecordPdfPreview(it, Modifier.fillMaxWidth()) }
            opened.textPreview?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun ExtractedPanel(
    records: RecordsController,
    document: CareDocument,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Extracted details", style = MaterialTheme.typography.titleMedium)
        for (field in document.fields) {
            FieldEditor(
                records = records,
                documentId = document.id,
                label = field.label,
                proposal = field.proposal?.value,
                quotedText = field.proposal?.quotedText,
                statusLabel = records.fieldStatusLabel(document.id, field.label),
                isConfirmed = field.isConfirmed(),
                isExplicitlyUnresolved = field.isExplicitlyUnresolved(),
                isUntouched = field.isUntouched(),
                shown = field.shownValue(),
            )
            if (field.label == DemoFamily.immunizationAField) {
                val snapshot = records.recordedDoseSnapshot(
                    document.id,
                    DemoFamily.immunizationAName,
                )
                if (snapshot != null) {
                    val reviewed = field.review?.value
                    Text(
                        buildString {
                            append("Recorded dose (source snapshot): ")
                            append(snapshot.dateAdministered)
                            if (reviewed != null && reviewed != snapshot.dateAdministered.toString()) {
                                append(
                                    " — document review is $reviewed; the recorded dose stays as its own snapshot.",
                                )
                            } else {
                                append(" — field corrections do not rewrite this recorded dose.")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun FieldEditor(
    records: RecordsController,
    documentId: String,
    label: String,
    proposal: String?,
    quotedText: String?,
    statusLabel: String,
    isConfirmed: Boolean,
    isExplicitlyUnresolved: Boolean,
    isUntouched: Boolean,
    shown: String?,
) {
    var draft by remember(label, shown, isExplicitlyUnresolved, isUntouched, records.revision) {
        mutableStateOf(shown.orEmpty())
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        quotedText?.let {
            Text("Quoted: “$it”", style = MaterialTheme.typography.bodySmall)
        }
        Text(statusLabel, style = MaterialTheme.typography.bodyMedium)
        if (isConfirmed) {
            Text("Saved value: $shown", style = MaterialTheme.typography.bodySmall)
        } else if (isExplicitlyUnresolved) {
            Text("Saved as explicitly unresolved.", style = MaterialTheme.typography.bodySmall)
        }
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(label) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { records.saveCorrection(documentId, label, draft) }) {
                Text("Save")
            }
            OutlinedButton(onClick = { records.leaveUnresolved(documentId, label) }) {
                Text("Leave unresolved")
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge)
}

private fun kindLabel(kind: DocumentKind): String = when (kind) {
    DocumentKind.VISIT_SUMMARY -> "Visit summary"
    DocumentKind.IMMUNIZATION_RECORD -> "Immunization record"
    DocumentKind.OTHER -> "Document"
}
