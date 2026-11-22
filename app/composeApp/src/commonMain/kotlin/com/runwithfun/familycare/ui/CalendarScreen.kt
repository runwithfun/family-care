package com.runwithfun.familycare.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.runwithfun.familycare.domain.CareCalendar
import com.runwithfun.familycare.domain.CareDate
import com.runwithfun.familycare.domain.FamilyRole
import com.runwithfun.familycare.domain.Task
import com.runwithfun.familycare.domain.Visit
import com.runwithfun.familycare.session.CareSession

private val weekdayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

@Composable
fun CalendarScreen(
    session: CareSession,
    selected: CareDate,
    onSelectDate: (CareDate) -> Unit,
    onOpenVisit: (String) -> Unit,
    onOpenTask: (String) -> Unit,
    onAddPlanItem: (CareDate) -> Unit,
    onBackHome: () -> Unit,
) {
    var visibleYear by remember(selected.year, selected.month) { mutableStateOf(selected.year) }
    var visibleMonth by remember(selected.year, selected.month) { mutableStateOf(selected.month) }
    val markedDays = session.daysWithEvents(visibleYear, visibleMonth)
    val dayEvents = session.eventsOn(selected)
    val people = session.people.associateBy { it.id }
    val canGoPrevious = CareCalendar.canShiftMonth(visibleYear, visibleMonth, -1)
    val canGoNext = CareCalendar.canShiftMonth(visibleYear, visibleMonth, 1)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBackHome, modifier = Modifier.heightIn(min = 44.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            Spacer(Modifier.size(6.dp))
            Text("Back")
        }
        Text("Calendar", style = MaterialTheme.typography.headlineSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = {
                    val shifted = CareCalendar.shiftMonthBounded(visibleYear, visibleMonth, -1) ?: return@TextButton
                    visibleYear = shifted.first
                    visibleMonth = shifted.second
                    onSelectDate(CareCalendar.clampDay(shifted.first, shifted.second, selected.day))
                },
                enabled = canGoPrevious,
                modifier = Modifier.heightIn(min = 44.dp),
            ) {
                Text("Previous")
            }
            Text(
                CareCalendar.monthLabel(visibleYear, visibleMonth),
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(
                onClick = {
                    val shifted = CareCalendar.shiftMonthBounded(visibleYear, visibleMonth, 1) ?: return@TextButton
                    visibleYear = shifted.first
                    visibleMonth = shifted.second
                    onSelectDate(CareCalendar.clampDay(shifted.first, shifted.second, selected.day))
                },
                enabled = canGoNext,
                modifier = Modifier.heightIn(min = 44.dp),
            ) {
                Text("Next")
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            for (label in weekdayLabels) {
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
        val cells = CareCalendar.monthCells(visibleYear, visibleMonth)
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                for (date in week) {
                    MonthDayCell(
                        date = date,
                        selected = date == selected,
                        marked = date != null && date.day in markedDays,
                        modifier = Modifier.weight(1f),
                        onClick = { if (date != null) onSelectDate(date) },
                    )
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text(
            "Daily plan · $selected",
            style = MaterialTheme.typography.titleMedium,
        )
        if (dayEvents.isEmpty()) {
            Text(
                "No visits or dated plan items on this day.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        } else {
            for (event in dayEvents) {
                val person = people.getValue(event.personId)
                val owner = if (person.role == FamilyRole.MOTHER) "For you" else person.displayName
                val state = when (event) {
                    is Visit -> "Visit · ${prettyStatus(event.status.name)}"
                    is Task -> "Plan item · ${prettyStatus(event.status.name)}"
                }
                val details = listOfNotNull(owner, event.time?.toString(), state).joinToString(" · ")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            when (event) {
                                is Visit -> onOpenVisit(event.id)
                                is Task -> onOpenTask(event.id)
                            }
                        }
                        .padding(vertical = 8.dp),
                ) {
                    Text(event.title, style = MaterialTheme.typography.titleMedium)
                    Text(details, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        TextButton(
            onClick = { onAddPlanItem(selected) },
            modifier = Modifier.heightIn(min = 44.dp),
        ) {
            Text("Add dated plan item")
        }
    }
}

@Composable
private fun MonthDayCell(
    date: CareDate?,
    selected: Boolean,
    marked: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(MaterialTheme.shapes.small)
            .then(
                if (selected) {
                    Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                } else {
                    Modifier
                },
            )
            .clickable(enabled = date != null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (date != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    date.day.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(
                            if (marked) MaterialTheme.colorScheme.primary
                            else Color.Transparent,
                        ),
                )
            }
        }
    }
}

internal fun prettyStatus(raw: String): String =
    raw.lowercase().replaceFirstChar { it.titlecase() }
