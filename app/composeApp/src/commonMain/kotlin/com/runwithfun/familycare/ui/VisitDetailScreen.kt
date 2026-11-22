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
import com.runwithfun.familycare.domain.CareDate
import com.runwithfun.familycare.domain.CareTime
import com.runwithfun.familycare.domain.FamilyRole
import com.runwithfun.familycare.domain.Visit
import com.runwithfun.familycare.domain.VisitStatus
import com.runwithfun.familycare.session.CareSession
import com.runwithfun.familycare.session.logCareSessionError

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VisitDetailScreen(
    session: CareSession,
    visitId: String,
    onBack: () -> Unit,
    onSaved: (CareDate) -> Unit,
) {
    val visit = session.event(visitId) as? Visit
    if (visit == null) {
        MissingEvent(onBack)
        return
    }

    val person = session.person(visit.personId)
    var title by remember(visit.id) { mutableStateOf(visit.title) }
    var provider by remember(visit.id) { mutableStateOf(visit.provider) }
    var dateText by remember(visit.id) { mutableStateOf(visit.date.toString()) }
    var timeText by remember(visit.id) { mutableStateOf(visit.time?.toString().orEmpty()) }
    var status by remember(visit.id) { mutableStateOf(visit.status) }
    var error by remember { mutableStateOf<String?>(null) }
    var savedNotice by remember { mutableStateOf<String?>(null) }

    fun attemptSave() {
        val parsedDate = parseCareDate(dateText)
        val parsedTime = parseCareTime(timeText)
        if (parsedDate == null) {
            error = "Enter a valid date as YYYY-MM-DD."
            savedNotice = null
            return
        }
        if (timeText.isNotBlank() && parsedTime == null) {
            error = "Enter a valid time as HH:MM, or leave it blank."
            savedNotice = null
            return
        }
        if (title.isBlank() || provider.isBlank()) {
            error = "Title and provider are required."
            savedNotice = null
            return
        }
        val updated = visit.copy(
            title = title.trim(),
            provider = provider.trim(),
            date = parsedDate,
            time = parsedTime,
            status = status,
        )
        try {
            session.saveVisit(updated)
            error = null
            savedNotice = "Saved · still the same visit."
            onSaved(parsedDate)
        } catch (failure: Exception) {
            logCareSessionError("Could not save visit ${visit.id}.", failure)
            error = "Could not save this visit."
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
        Text("Visit details", style = MaterialTheme.typography.headlineSmall)
        Text(
            if (person.role == FamilyRole.MOTHER) "For you · ${person.displayName}"
            else person.displayName,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "Booking · ${prettyStatus(status.name)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = provider,
            onValueChange = { provider = it },
            label = { Text("Provider") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = dateText,
            onValueChange = { dateText = it },
            label = { Text("Date (YYYY-MM-DD)") },
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
        Text("Booking state", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VisitStatus.entries.forEach { option ->
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
            Text("Save visit")
        }
        TextButton(onClick = ::attemptSave, modifier = Modifier.heightIn(min = 44.dp)) {
            Text("Retry save")
        }
    }
}

@Composable
internal fun MissingEvent(onBack: () -> Unit) {
    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 44.dp)) { Text("Back") }
        Text("This item is no longer in the care plan.", style = MaterialTheme.typography.bodyLarge)
    }
}

internal fun parseCareDate(raw: String): CareDate? {
    val parts = raw.trim().split('-')
    if (parts.size != 3) return null
    val year = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    val day = parts[2].toIntOrNull() ?: return null
    return runCatching { CareDate(year, month, day) }.getOrNull()
}

internal fun parseCareTime(raw: String): CareTime? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val parts = trimmed.split(':')
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    return runCatching { CareTime(hour, minute) }.getOrNull()
}
