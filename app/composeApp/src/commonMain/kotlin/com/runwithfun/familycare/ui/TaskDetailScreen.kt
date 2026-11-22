package com.runwithfun.familycare.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.runwithfun.familycare.demo.DemoFamily
import com.runwithfun.familycare.domain.CareDate
import com.runwithfun.familycare.domain.FamilyRole
import com.runwithfun.familycare.domain.Task
import com.runwithfun.familycare.domain.TaskStatus
import com.runwithfun.familycare.domain.Visit
import com.runwithfun.familycare.session.CareSession
import com.runwithfun.familycare.session.logCareSessionError

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskDetailScreen(
    session: CareSession,
    taskId: String?,
    draftDate: CareDate,
    onBack: () -> Unit,
    onSaved: (taskId: String, date: CareDate) -> Unit,
) {
    val existing = taskId?.let { session.event(it) as? Task }
    if (taskId != null && existing == null) {
        MissingEvent(onBack)
        return
    }

    val isDraft = existing == null
    val people = session.people
    var title by remember(taskId) { mutableStateOf(existing?.title ?: "New dated plan item") }
    var dateText by remember(taskId, draftDate) {
        mutableStateOf((existing?.date ?: draftDate).toString())
    }
    var timeText by remember(taskId) { mutableStateOf(existing?.time?.toString().orEmpty()) }
    var status by remember(taskId) { mutableStateOf(existing?.status ?: TaskStatus.OPEN) }
    var personId by remember(taskId) {
        mutableStateOf(existing?.personId ?: DemoFamily.childId)
    }
    var assignedId by remember(taskId) { mutableStateOf(existing?.id) }
    var error by remember { mutableStateOf<String?>(null) }
    var savedNotice by remember { mutableStateOf<String?>(null) }
    val relatedVisit = existing?.visitId?.let { session.event(it) as? Visit }

    fun attemptSave() {
        val parsedDate = parseCareDate(dateText)
        val parsedTime = parseCareTime(timeText)
        if (parsedDate == null) {
            error = "Enter a valid due date as YYYY-MM-DD."
            savedNotice = null
            return
        }
        if (timeText.isNotBlank() && parsedTime == null) {
            error = "Enter a valid time as HH:MM, or leave it blank."
            savedNotice = null
            return
        }
        if (title.isBlank()) {
            error = "Title is required."
            savedNotice = null
            return
        }
        val id = assignedId ?: session.allocateLocalId().also { assignedId = it }
        val updated = Task(
            id = id,
            personId = if (isDraft) personId else existing.personId,
            title = title.trim(),
            date = parsedDate,
            time = parsedTime,
            status = status,
            sourceDocumentId = existing?.sourceDocumentId,
            visitId = existing?.visitId,
        )
        try {
            session.saveTask(updated)
            error = null
            savedNotice = "Saved · plan item ID ${updated.id} kept."
            onSaved(updated.id, parsedDate)
        } catch (failure: Exception) {
            logCareSessionError("Could not save plan item $id.", failure)
            error = "Could not save this plan item."
            savedNotice = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 44.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            Spacer(Modifier.size(6.dp))
            Text("Back")
        }
        Text(
            if (isDraft) "New dated plan item" else "Dated plan item",
            style = MaterialTheme.typography.headlineSmall,
        )
        if (isDraft) {
            Text("Family member", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (person in people) {
                    val label = if (person.role == FamilyRole.MOTHER) {
                        "For you · ${person.displayName}"
                    } else {
                        person.displayName
                    }
                    FilterChip(
                        selected = personId == person.id,
                        onClick = { personId = person.id },
                        label = { Text(label) },
                    )
                }
            }
        } else {
            val person = session.person(existing.personId)
            Text(
                if (person.role == FamilyRole.MOTHER) "For you · ${person.displayName}"
                else person.displayName,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Text(
            "Due · ${existing?.date ?: draftDate}" + (existing?.time?.let { " · $it" } ?: ""),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        relatedVisit?.let {
            Text(
                "Linked visit · ${it.title}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (isDraft) {
            Text(
                "This stays a draft until you save. Back leaves the care plan unchanged.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = dateText,
            onValueChange = { dateText = it },
            label = { Text("Due date (YYYY-MM-DD)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = timeText,
            onValueChange = { timeText = it },
            label = { Text("Time (HH:MM, optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Text("Plan item state", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TaskStatus.entries.forEach { option ->
                FilterChip(
                    selected = status == option,
                    onClick = { status = option },
                    label = { Text(prettyStatus(option.name)) },
                )
            }
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        savedNotice?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        }
        Button(onClick = ::attemptSave, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
            Text(if (isDraft) "Save plan item" else "Save plan item")
        }
        TextButton(onClick = ::attemptSave, modifier = Modifier.heightIn(min = 44.dp)) {
            Text("Retry save")
        }
    }
}
