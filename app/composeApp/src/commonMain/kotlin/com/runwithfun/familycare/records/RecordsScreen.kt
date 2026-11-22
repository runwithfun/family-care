package com.runwithfun.familycare.records

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import com.runwithfun.familycare.domain.CareDocument
import com.runwithfun.familycare.domain.CarePlan
import com.runwithfun.familycare.domain.DocumentKind
import com.runwithfun.familycare.domain.FamilyMember
import com.runwithfun.familycare.domain.FamilyRole
import com.runwithfun.familycare.domain.FieldReview
import com.runwithfun.familycare.domain.Visit
import com.runwithfun.familycare.domain.hasGaps
import com.runwithfun.familycare.domain.isComplete
import com.runwithfun.familycare.domain.isConfirmed
import com.runwithfun.familycare.domain.originalRetrievalLabel
import com.runwithfun.familycare.domain.originalText
import com.runwithfun.familycare.domain.providerFollowUpMessage
import com.runwithfun.familycare.domain.shownValue

@Composable
fun RecordsScreen(
    plan: CarePlan,
    people: Map<String, FamilyMember>,
    modifier: Modifier = Modifier,
    onRevision: () -> Unit = {},
) {
    var selectedDocumentId by remember { mutableStateOf<String?>(null) }
    val selected = selectedDocumentId?.let { plan.document(it) }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Records", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Imported clinic documents stay on this device. " +
                "Review extracted details beside the original. " +
                "Missing immunization details stay unresolved until the clinic confirms them.",
            style = MaterialTheme.typography.bodyMedium,
        )

        if (selected == null) {
            RecordsOverview(
                plan = plan,
                people = people,
                onOpenDocument = { selectedDocumentId = it },
            )
        } else {
            DocumentDetail(
                plan = plan,
                document = selected,
                person = people.getValue(selected.personId),
                onBack = { selectedDocumentId = null },
                onReviewed = onRevision,
            )
        }
    }
}

@Composable
private fun RecordsOverview(
    plan: CarePlan,
    people: Map<String, FamilyMember>,
    onOpenDocument: (String) -> Unit,
) {
    SectionTitle("Documents")
    for (document in plan.documents) {
        val person = people.getValue(document.personId)
        DocumentRow(document, person, onClick = { onOpenDocument(document.id) })
    }

    HorizontalDivider()
    SectionTitle("Documented immunizations")
    if (plan.recordedImmunizations.isEmpty()) {
        Text("No documented immunizations yet.", style = MaterialTheme.typography.bodyMedium)
    } else {
        for (record in plan.recordedImmunizations) {
            val person = people.getValue(record.personId)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(record.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${person.displayName} · ${record.dateAdministered}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Source: ${plan.document(record.sourceDocumentId)?.originalRetrievalLabel() ?: record.sourceDocumentId}",
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
    for (reference in plan.immunizationReferences) {
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
    val appointments = plan.appointments()
    if (appointments.isEmpty()) {
        Text("No appointments yet.", style = MaterialTheme.typography.bodyMedium)
    } else {
        for (visit in appointments) {
            AppointmentRow(visit, people.getValue(visit.personId))
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
    plan: CarePlan,
    document: CareDocument,
    person: FamilyMember,
    onBack: () -> Unit,
    onReviewed: () -> Unit,
) {
    TextButton(onClick = onBack) {
        Text("Back to records")
    }
    Text(document.original.fileName, style = MaterialTheme.typography.titleLarge)
    Text(
        "${kindLabel(document.kind)} · ${person.displayName} · ${document.original.mediaType}",
        style = MaterialTheme.typography.bodyMedium,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OriginalPanel(
            document = document,
            modifier = Modifier.weight(1f),
        )
        ExtractedPanel(
            plan = plan,
            document = document,
            modifier = Modifier.weight(1f),
            onReviewed = onReviewed,
        )
    }

    document.providerFollowUpMessage()?.let { message ->
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
            }
        }
    }

    FollowUpTasks(plan = plan, document = document)
}

@Composable
private fun FollowUpTasks(plan: CarePlan, document: CareDocument) {
    if (!document.hasGaps()) return
    val linkedTitles = linkedRequestTitles(plan, document)
    if (linkedTitles.isEmpty()) return
    SectionTitle("Open requests")
    for (title in linkedTitles) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
    }
}
/**
 * Finds tasks that point at this document without guessing clinical status.
 * Uses appointment dates already in the plan as a scan window plus the document person's visits.
 */
private fun linkedRequestTitles(plan: CarePlan, document: CareDocument): List<String> {
    val dates = plan.appointments(document.personId).map { it.date }.toSet() +
        plan.appointments().map { it.date }.toSet()
    return dates
        .flatMap { date -> plan.eventsOn(date, document.personId) }
        .filter { it.sourceDocumentId == document.id }
        .map { it.title }
        .distinct()
}

@Composable
private fun OriginalPanel(document: CareDocument, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Original", style = MaterialTheme.typography.titleMedium)
        Text(
            "Retrievable as ${document.originalRetrievalLabel()}",
            style = MaterialTheme.typography.bodySmall,
        )
        val text = document.originalText()
        if (text != null) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(
                "Stored file key is available on this device.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ExtractedPanel(
    plan: CarePlan,
    document: CareDocument,
    modifier: Modifier = Modifier,
    onReviewed: () -> Unit,
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
                label = field.label,
                proposal = field.proposal?.value,
                quotedText = field.proposal?.quotedText,
                currentReview = field.review?.value,
                isConfirmed = field.isConfirmed(),
                shown = field.shownValue(),
                onConfirm = { value ->
                    plan.reviewField(document.id, field.label, FieldReview(value))
                    onReviewed()
                },
                onLeaveUnresolved = {
                    plan.reviewField(document.id, field.label, FieldReview(null))
                    onReviewed()
                },
            )
        }
    }
}

@Composable
private fun FieldEditor(
    label: String,
    proposal: String?,
    quotedText: String?,
    currentReview: String?,
    isConfirmed: Boolean,
    shown: String?,
    onConfirm: (String) -> Unit,
    onLeaveUnresolved: () -> Unit,
) {
    var draft by remember(label, currentReview, proposal) {
        mutableStateOf(currentReview ?: proposal.orEmpty())
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        when {
            proposal == null && currentReview == null -> {
                Text(
                    "Not found on this document. Leave unresolved and ask the clinic.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            quotedText != null -> {
                Text("Quoted: “$quotedText”", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (isConfirmed) {
            Text("Confirmed: $shown", style = MaterialTheme.typography.bodyMedium)
        } else if (currentReview == null && proposal == null) {
            Text("Status: unresolved — no vaccine or visit fact assumed.", style = MaterialTheme.typography.bodyMedium)
        } else if (currentReview == null) {
            Text("Proposed: $proposal", style = MaterialTheme.typography.bodyMedium)
        } else {
            Text("Left unresolved", style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Correction") },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { if (draft.isNotBlank()) onConfirm(draft.trim()) },
                enabled = draft.isNotBlank(),
            ) {
                Text("Save correction")
            }
            OutlinedButton(onClick = onLeaveUnresolved) {
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
