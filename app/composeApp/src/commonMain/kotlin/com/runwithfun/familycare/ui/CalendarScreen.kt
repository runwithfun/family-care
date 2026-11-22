package com.runwithfun.familycare.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick as semanticsOnClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.runwithfun.familycare.demo.DemoFamily
import com.runwithfun.familycare.domain.CareCalendar
import com.runwithfun.familycare.domain.CareDate
import com.runwithfun.familycare.domain.CareEvent
import com.runwithfun.familycare.domain.FamilyRole
import com.runwithfun.familycare.domain.Task
import com.runwithfun.familycare.domain.TaskStatus
import com.runwithfun.familycare.domain.Visit
import com.runwithfun.familycare.session.CareSession

private val calendarWeekdays = listOf(
    "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday",
)

@Composable
fun CalendarScreen(
    session: CareSession,
    selected: CareDate,
    onSelectDate: (CareDate) -> Unit,
    onOpenVisit: (String) -> Unit,
    onOpenTask: (String) -> Unit,
    onAddPlanItem: (CareDate) -> Unit,
    onBackHome: () -> Unit,
    focusedVisitId: String? = null,
) {
    var visibleYear by remember(selected.year, selected.month) { mutableStateOf(selected.year) }
    var visibleMonth by remember(selected.year, selected.month) { mutableStateOf(selected.month) }
    val today = DemoFamily.today
    val eventsByDay = session.eventsInMonth(visibleYear, visibleMonth).groupingBy { it.date.day }.eachCount()
    val dayEvents = session.eventsOn(selected)
    val people = session.people.associateBy { it.id }
    val colors = MaterialTheme.colorScheme
    val selectedLabel = selected.calendarLabel()
    val visitCount = dayEvents.count { it is Visit }
    val reminderCount = dayEvents.size - visitCount

    val changeMonth: (Int) -> Unit = { delta ->
        val (year, month) = CareCalendar.shiftMonth(visibleYear, visibleMonth, delta)
        if (year in 1..9999) {
            visibleYear = year
            visibleMonth = month
            onSelectDate(CareCalendar.clampDay(year, month, selected.day))
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            TextButton(onClick = onBackHome, modifier = Modifier.heightIn(min = 44.dp)) {
                Chevron(pointsLeft = true, color = colors.primary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.size(5.dp))
                Text("Back to home")
            }
            Text(
                "Calendar",
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .semantics {
                    contentDescription = "Selected day $selectedLabel"
                },
            shape = RoundedCornerShape(18.dp),
            color = colors.primaryContainer,
            contentColor = colors.onPrimaryContainer,
            border = BorderStroke(1.dp, colors.outline.copy(alpha = 0.35f)),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Selected day", style = MaterialTheme.typography.labelLarge)
                Text(selectedLabel, style = MaterialTheme.typography.titleLarge)
                Text(
                    when {
                        dayEvents.isEmpty() -> "No visits or reminders yet"
                        else -> buildString {
                            if (visitCount > 0) append("$visitCount ${if (visitCount == 1) "visit" else "visits"}")
                            if (visitCount > 0 && reminderCount > 0) append(" · ")
                            if (reminderCount > 0) {
                                append("$reminderCount ${if (reminderCount == 1) "reminder" else "reminders"}")
                            }
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, colors.outlineVariant),
            color = colors.surface,
        ) {
            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MonthArrow(
                        pointsLeft = true,
                        enabled = visibleYear > 1 || visibleMonth > 1,
                        onClick = { changeMonth(-1) },
                    )
                    Text(
                        CareCalendar.monthLabel(visibleYear, visibleMonth),
                        modifier = Modifier.weight(1f).semantics { heading() },
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                    )
                    MonthArrow(
                        pointsLeft = false,
                        enabled = visibleYear < 9999 || visibleMonth < 12,
                        onClick = { changeMonth(1) },
                    )
                }

                Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
                    for (day in calendarWeekdays) {
                        Text(
                            day.take(3),
                            modifier = Modifier.weight(1f).clearAndSetSemantics {},
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                val firstColumn = (CareCalendar.dayOfWeekMondayBased(visibleYear, visibleMonth, 1) + 1) % 7
                val numberOfDays = CareCalendar.lengthOfMonth(visibleYear, visibleMonth)
                val weekCount = (firstColumn + numberOfDays + 6) / 7
                Column(Modifier.fillMaxWidth().selectableGroup()) {
                    repeat(weekCount) { week ->
                        Row(Modifier.fillMaxWidth()) {
                            repeat(7) { column ->
                                val day = week * 7 + column - firstColumn + 1
                                if (day in 1..numberOfDays) {
                                    val date = CareDate(visibleYear, visibleMonth, day)
                                    MonthDayCell(
                                        date = date,
                                        isSelected = date == selected,
                                        isToday = date == today,
                                        count = eventsByDay[day] ?: 0,
                                        modifier = Modifier.weight(1f),
                                        onClick = { onSelectDate(date) },
                                    )
                                } else {
                                    Spacer(Modifier.weight(1f).heightIn(min = 48.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (selected == today) "Today’s plan" else "Day plan",
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        selectedLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { onAddPlanItem(selected) }, modifier = Modifier.heightIn(min = 44.dp)) {
                    Text("Add plan item")
                }
            }
            if (dayEvents.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = colors.surfaceVariant,
                    contentColor = colors.onSurfaceVariant,
                ) {
                    Text(
                        "No visits or reminders on this day. Pick another date above, or add a plan item.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                Spacer(Modifier.size(8.dp))
                dayEvents.forEachIndexed { index, event ->
                    if (index > 0) HorizontalDivider(color = colors.outlineVariant)
                    val person = people.getValue(event.personId)
                    val owner = if (person.role == FamilyRole.MOTHER) "For you" else person.displayName
                    CalendarEventRow(
                        event = event,
                        owner = owner,
                        isFocused = event.id == focusedVisitId,
                        onClick = {
                            when (event) {
                                is Visit -> onOpenVisit(event.id)
                                is Task -> onOpenTask(event.id)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthArrow(pointsLeft: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp).semantics {
            contentDescription = if (pointsLeft) "Previous month" else "Next month"
        },
    ) {
        Surface(
            modifier = Modifier.size(34.dp),
            shape = CircleShape,
            color = colors.surfaceVariant,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Chevron(
                    pointsLeft = pointsLeft,
                    color = if (enabled) colors.onSurface else colors.onSurfaceVariant.copy(alpha = 0.38f),
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

@Composable
private fun MonthDayCell(
    date: CareDate,
    isSelected: Boolean,
    isToday: Boolean,
    count: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .heightIn(min = 48.dp)
            .selectable(selected = isSelected, onClick = onClick, role = Role.Button)
            .clearAndSetSemantics {
                contentDescription = buildString {
                    append("${date.calendarLabel()}, ${date.year}, $count ")
                    append(if (count == 1) "event" else "events")
                    if (isToday) append(", Today")
                    if (isSelected) append(", Selected")
                }
                selected = isSelected
                role = Role.Button
                semanticsOnClick {
                    onClick()
                    true
                }
            },
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.sizeIn(minWidth = 36.dp, minHeight = 36.dp),
            shape = CircleShape,
            color = when {
                isSelected -> colors.primary
                else -> colors.surface
            },
            contentColor = when {
                isSelected -> colors.onPrimary
                else -> colors.onSurface
            },
            border = when {
                isSelected -> null
                isToday -> BorderStroke(1.5.dp, colors.outline)
                else -> null
            },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(date.day.toString(), style = MaterialTheme.typography.labelLarge)
            }
        }
        val dotColor = when {
            count == 0 -> Color.Transparent
            isSelected -> colors.primary
            else -> colors.secondary
        }
        Spacer(Modifier.size(4.dp).background(dotColor, CircleShape))
    }
}

@Composable
private fun CalendarEventRow(
    event: CareEvent,
    owner: String,
    isFocused: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val kind = when (event) {
        is Visit -> "Visit"
        is Task -> "Reminder"
    }
    val state = when (event) {
        is Visit -> prettyStatus(event.status.name)
        is Task -> if (event.status == TaskStatus.DONE) "Done" else "Open"
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append("$kind for $owner, ${event.title}, $state")
                    if (isFocused) append(", Open visit")
                }
            },
        shape = RoundedCornerShape(14.dp),
        color = if (isFocused) colors.primaryContainer.copy(alpha = 0.55f) else colors.surface,
        border = if (isFocused) BorderStroke(1.dp, colors.outline) else null,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = colors.secondaryContainer,
                        contentColor = colors.onSecondaryContainer,
                    ) {
                        Text(
                            owner,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    Text(
                        "$kind · $state",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
                Text(event.title, style = MaterialTheme.typography.titleMedium)
                if (isFocused) {
                    Text(
                        "Open visit",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondary,
                    )
                }
            }
            event.time?.let { Text(it.toString(), style = MaterialTheme.typography.labelLarge) }
            Chevron(pointsLeft = false, color = colors.onSurfaceVariant, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun Chevron(pointsLeft: Boolean, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val tipX = size.width * if (pointsLeft) 0.33f else 0.67f
        val backX = size.width - tipX
        val tip = Offset(tipX, center.y)
        drawLine(color, Offset(backX, size.height * 0.19f), tip, 1.5.dp.toPx(), StrokeCap.Round)
        drawLine(color, tip, Offset(backX, size.height * 0.81f), 1.5.dp.toPx(), StrokeCap.Round)
    }
}

private fun CareDate.calendarLabel(): String {
    val weekday = (CareCalendar.dayOfWeekMondayBased(year, month, day) + 1) % 7
    val monthName = CareCalendar.monthLabel(year, month).substringBeforeLast(' ')
    return "${calendarWeekdays[weekday]}, $monthName $day"
}

internal fun prettyStatus(raw: String): String =
    raw.lowercase().replaceFirstChar { it.titlecase() }

internal fun TaskStatus.label(): String = prettyStatus(name)
