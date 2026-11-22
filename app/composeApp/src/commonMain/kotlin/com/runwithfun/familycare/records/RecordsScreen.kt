package com.runwithfun.familycare.records

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.runwithfun.familycare.demo.DemoFamily
import com.runwithfun.familycare.domain.CareDocument
import com.runwithfun.familycare.domain.DocumentKind
import com.runwithfun.familycare.domain.FamilyMember
import com.runwithfun.familycare.domain.FamilyRole
import com.runwithfun.familycare.domain.OriginalContent
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
import com.runwithfun.familycare.resources.Res
import com.runwithfun.familycare.resources.records_appointments_sketch
import com.runwithfun.familycare.resources.records_documents_sketch
import com.runwithfun.familycare.resources.records_immunization_sketch
import com.runwithfun.familycare.resources.records_import_sketch
import com.runwithfun.familycare.resources.records_references_sketch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private val NarrowWidth = 600.dp
private val InsetCorner = RoundedCornerShape(12.dp)
private val HeaderSketchSize = 40.dp
private val EmptySketchSize = 96.dp

@Composable
fun RecordsScreen(
    records: RecordsController,
    people: Map<String, FamilyMember>,
    selectedDocumentId: String?,
    onSelectDocument: (String?) -> Unit,
    onOpenTask: (Task) -> Unit,
    modifier: Modifier = Modifier,
) {
    var ownerId by rememberSaveable { mutableStateOf(DemoFamily.childId) }
    val selected = selectedDocumentId?.let { records.document(it) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (selected == null) {
            Text("Records", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
        }

        records.errorMessage?.let { message ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = InsetCorner,
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
                ownerId = ownerId,
                onSelectOwner = { ownerId = it },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordsOverview(
    records: RecordsController,
    people: Map<String, FamilyMember>,
    ownerId: String,
    onSelectOwner: (String) -> Unit,
    onOpenDocument: (String) -> Unit,
) {
    val addPdf = rememberRecordImportAction(records) { _, documentId -> onOpenDocument(documentId) }
    val ownerDocuments = records.documents().filter { it.personId == ownerId }
    val ownerImmunizations = records.plan.recordedImmunizations.filter { it.personId == ownerId }
    val ownerAppointments = records.plan.appointments().filter { it.personId == ownerId }

    Text("Who these records belong to", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        people.values.forEach { person ->
            FilterChip(
                selected = person.id == ownerId,
                onClick = { onSelectOwner(person.id) },
                label = { Text(person.displayName) },
            )
        }
    }

    RecordsSection(
        title = "Add a clinic PDF",
        sketch = Res.drawable.records_import_sketch,
        sketchDescription = "Pencil sketch of a clinic page with a paperclip",
    ) {
        InsetGroup {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Store an original PDF on this device for ${people[ownerId]?.displayName ?: "this person"}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { addPdf(ownerId) },
                    enabled = ownerId in people,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Choose PDF")
                }
                if (ownerId == DemoFamily.childId) {
                    if (!records.isPdfImported()) {
                        OutlinedButton(
                            onClick = { records.importSyntheticImmunizationPdf() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Import synthetic clinic PDF")
                        }
                    } else {
                        Text(
                            "Synthetic clinic PDF is stored on this device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    RecordsSection(
        title = "Documents",
        sketch = Res.drawable.records_documents_sketch,
        sketchDescription = "Pencil sketch of a folder with a baby footprint",
    ) {
        if (ownerDocuments.isEmpty()) {
            EmptySketchBlock(
                sketch = Res.drawable.records_documents_sketch,
                message = "No clinic documents for this person yet.",
            )
        } else {
            InsetGroup {
                ownerDocuments.forEachIndexed { index, document ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    DocumentRow(document, people.getValue(document.personId), onClick = { onOpenDocument(document.id) })
                }
            }
        }
    }

    RecordsSection(
        title = "Immunizations",
        sketch = Res.drawable.records_immunization_sketch,
        sketchDescription = "Pencil sketch of a shield with a heart",
    ) {
        if (ownerImmunizations.isEmpty()) {
            EmptySketchBlock(
                sketch = Res.drawable.records_immunization_sketch,
                message = "No documented immunizations yet.",
            )
        } else {
            InsetGroup {
                ownerImmunizations.forEachIndexed { index, record ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(record.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${people.getValue(record.personId).displayName} · ${record.dateAdministered}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Source snapshot from document",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    RecordsSection(
        title = "Appointments",
        sketch = Res.drawable.records_appointments_sketch,
        sketchDescription = "Pencil sketch of a calendar with a heart",
    ) {
        if (ownerAppointments.isEmpty()) {
            EmptySketchBlock(
                sketch = Res.drawable.records_appointments_sketch,
                message = "No appointments for this person.",
            )
        } else {
            InsetGroup {
                ownerAppointments.forEachIndexed { index, visit ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    AppointmentRow(visit, people.getValue(visit.personId))
                }
            }
        }
    }

    RecordsSection(
        title = "Public references",
        sketch = Res.drawable.records_references_sketch,
        sketchDescription = "Pencil sketch of an open booklet",
    ) {
        InsetGroup {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Schedules from public health sites. These are not your child's record.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                records.plan.immunizationReferences.forEachIndexed { index, reference ->
                    if (index > 0) Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "${reference.authority} (${reference.jurisdiction})",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            reference.sourceUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordsSection(
    title: String,
    sketch: DrawableResource,
    sketchDescription: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Image(
                painter = painterResource(sketch),
                contentDescription = sketchDescription,
                modifier = Modifier.size(HeaderSketchSize),
                contentScale = ContentScale.Fit,
            )
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
        }
        content()
    }
}

@Composable
private fun InsetGroup(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = InsetCorner,
    ) {
        Column(content = { content() })
    }
}

@Composable
private fun EmptySketchBlock(
    sketch: DrawableResource,
    message: String,
) {
    InsetGroup {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Image(
                painter = painterResource(sketch),
                contentDescription = null,
                modifier = Modifier.size(EmptySketchSize),
                contentScale = ContentScale.Fit,
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
        document.hasGaps() -> "Needs follow-up"
        else -> "Ready to review"
    }
    val statusTone = when {
        document.isComplete() -> MaterialTheme.colorScheme.secondaryContainer
        document.hasGaps() -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val statusOnTone = when {
        document.isComplete() -> MaterialTheme.colorScheme.onSecondaryContainer
        document.hasGaps() -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = "${document.original.fileName}, $status"
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                document.original.fileName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${kindLabel(document.kind)} · ${person.displayName}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Surface(color = statusTone, shape = RoundedCornerShape(6.dp)) {
                Text(
                    status,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = statusOnTone,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(visit.title, style = MaterialTheme.typography.titleMedium)
        Text(
            details,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
    val isImportedPdf = current.original.content is OriginalContent.ImportedPdf

    TextButton(onClick = onBack) {
        Text("Back to records")
    }
    Text(
        if (isImportedPdf) "Clinic PDF" else kindLabel(current.kind),
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.headlineLarge,
    )
    Text(
        "${person.displayName} · ${current.original.fileName}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (isImportedPdf) {
        Text("Original PDF · review the document for details", style = MaterialTheme.typography.bodyMedium)
    } else if (current.kind == DocumentKind.IMMUNIZATION_RECORD && current.hasGaps()) {
        Text("Some immunization details still need review.", style = MaterialTheme.typography.bodyMedium)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val stack = maxWidth < NarrowWidth
        if (stack) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OriginalPanel(records, current, modifier = Modifier.fillMaxWidth())
                if (current.fields.isNotEmpty()) ExtractedPanel(records, current, modifier = Modifier.fillMaxWidth())
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OriginalPanel(records, current, modifier = Modifier.weight(1f))
                if (current.fields.isNotEmpty()) ExtractedPanel(records, current, modifier = Modifier.weight(1f))
            }
        }
    }

    current.providerFollowUpMessage()?.let { message ->
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = InsetCorner,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Provider follow-up", style = MaterialTheme.typography.titleMedium)
                Text(message, style = MaterialTheme.typography.bodyMedium)
                if (current.kind == DocumentKind.IMMUNIZATION_RECORD) {
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
    }

    val openRequests = records.openRequestTasks(current.id)
    if (openRequests.isNotEmpty()) {
        Text("Open follow-ups", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
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
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, InsetCorner)
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
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, InsetCorner)
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

private fun kindLabel(kind: DocumentKind): String = when (kind) {
    DocumentKind.VISIT_SUMMARY -> "Visit summary"
    DocumentKind.IMMUNIZATION_RECORD -> "Immunization record"
    DocumentKind.OTHER -> "Document"
}
