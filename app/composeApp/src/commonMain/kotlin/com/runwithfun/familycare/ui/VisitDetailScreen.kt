package com.runwithfun.familycare.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.runwithfun.familycare.domain.CareCalendar
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
    val ownerLabel = if (person.role == FamilyRole.MOTHER) {
        "For you · ${person.displayName}"
    } else {
        person.displayName
    }
    var title by remember(visit.id) { mutableStateOf(visit.title) }
    var provider by remember(visit.id) { mutableStateOf(visit.provider) }
    var dateText by remember(visit.id) { mutableStateOf(visit.date.toString()) }
    var timeText by remember(visit.id) { mutableStateOf(visit.time?.toString().orEmpty()) }
    var status by remember(visit.id) { mutableStateOf(visit.status) }
    var error by remember { mutableStateOf<String?>(null) }
    var savedNotice by remember { mutableStateOf<String?>(null) }
    val colors = MaterialTheme.colorScheme
    val draftDate = parseCareDate(dateText)
    val draftTime = parseCareTime(timeText)
    val scheduleChanged = draftDate != visit.date || draftTime != visit.time

    fun attemptSave(asReschedule: Boolean) {
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
            savedNotice = if (asReschedule || scheduleChanged) {
                "Rescheduled · Calendar will show ${parsedDate}."
            } else {
                "Saved · still the same visit."
            }
            onSaved(parsedDate)
        } catch (failure: Exception) {
            logCareSessionError("Could not save visit ${visit.id}.", failure)
            error = "Could not save this visit."
            savedNotice = null
        }
    }

    fun moveByDays(days: Int) {
        val base = parseCareDate(dateText) ?: visit.date
        dateText = CareCalendar.shiftDay(base, days).toString()
        savedNotice = null
        error = null
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
            "Visit details",
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = colors.primaryContainer,
            contentColor = colors.onPrimaryContainer,
            border = BorderStroke(1.dp, colors.outline.copy(alpha = 0.3f)),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(ownerLabel, style = MaterialTheme.typography.titleLarge)
                Text(title.ifBlank { visit.title }, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Date · ${(draftDate ?: visit.date)}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    "Time · ${(draftTime ?: visit.time)?.toString() ?: "Not set"}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    "Booking status · ${prettyStatus(status.name)}",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.outlineVariant),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Reschedule",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "Change the date or time, then save. The day plan and Calendar use the same visit.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = dateText,
                    onValueChange = {
                        dateText = it
                        savedNotice = null
                    },
                    label = { Text("Date (YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = timeText,
                    onValueChange = {
                        timeText = it
                        savedNotice = null
                    },
                    label = { Text("Time (HH:MM, optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { moveByDays(1) },
                        modifier = Modifier.heightIn(min = 44.dp),
                    ) {
                        Text("Move 1 day later")
                    }
                    TextButton(
                        onClick = { dateText = DemoFamily.today.toString() },
                        modifier = Modifier.heightIn(min = 44.dp),
                    ) {
                        Text("Use today")
                    }
                }
                Button(
                    onClick = { attemptSave(asReschedule = true) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primaryContainer,
                        contentColor = colors.onPrimaryContainer,
                    ),
                ) {
                    Text(if (scheduleChanged) "Save new date & time" else "Save schedule")
                }
            }
        }

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
            Text(it, color = colors.error, style = MaterialTheme.typography.bodyMedium)
        }
        savedNotice?.let {
            Text(it, color = colors.secondary, style = MaterialTheme.typography.bodyMedium)
        }
        Button(
            onClick = { attemptSave(asReschedule = false) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
        ) {
            Text("Save visit")
        }
        TextButton(onClick = { attemptSave(asReschedule = false) }, modifier = Modifier.heightIn(min = 44.dp)) {
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
