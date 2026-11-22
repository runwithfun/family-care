package com.runwithfun.familycare.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick as semanticsOnClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.runwithfun.familycare.domain.CareDate
import com.runwithfun.familycare.domain.CareEvent
import com.runwithfun.familycare.domain.FamilyMember
import com.runwithfun.familycare.domain.FamilyRole
import com.runwithfun.familycare.domain.Task
import com.runwithfun.familycare.domain.TaskStatus
import com.runwithfun.familycare.domain.Visit
import com.runwithfun.familycare.domain.VisitStatus

@Composable
internal fun HomeScreen(
    child: FamilyMember,
    childName: String,
    photo: ImageBitmap?,
    today: CareDate,
    selectedDay: CareDate,
    people: Map<String, FamilyMember>,
    eventsOn: (CareDate) -> List<CareEvent>,
    onSelectDay: (CareDate) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenEvent: (CareEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ProfileHeader(
            name = childName,
            age = child.birthDate?.let { childAge(it, today) },
            photo = photo,
            onOpenProfile = onOpenProfile,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(if (selectedDay == today) "Today" else selectedDay.weekdayName(), style = MaterialTheme.typography.headlineSmall)
                Text(
                    selectedDay.monthAndDay(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(
                onClick = onOpenCalendar,
                modifier = Modifier.heightIn(min = 44.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Calendar")
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp).selectableGroup()) {
            for (date in weekContaining(selectedDay)) {
                if (date == null) {
                    Spacer(Modifier.weight(1f))
                } else {
                    WeekDay(
                        date = date,
                        isToday = date == today,
                        isSelected = date == selectedDay,
                        itemCount = eventsOn(date).size,
                        onClick = { onSelectDay(date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        val events = eventsOn(selectedDay)
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            if (events.isEmpty()) {
                Text(
                    "No events saved for this day.",
                    modifier = Modifier.padding(vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            events.forEachIndexed { index, event ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val person = people.getValue(event.personId)
                AgendaRow(
                    event = event,
                    person = when {
                        person.role == FamilyRole.MOTHER -> "For you"
                        person.id == child.id -> childName
                        else -> person.displayName
                    },
                    onClick = { onOpenEvent(event) },
                )
            }
            if (selectedDay != today) {
                TextButton(onClick = { onSelectDay(today) }, modifier = Modifier.heightIn(min = 44.dp)) {
                    Text("Back to today")
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(
    name: String,
    age: String?,
    photo: ImageBitmap?,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.displaySmall)
            if (age != null) {
                Text(age, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onOpenProfile, modifier = Modifier.heightIn(min = 44.dp)) {
                Text("View profile")
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        }
        ProfileAvatar(photo)
    }
}

@Composable
internal fun ProfileAvatar(photo: ImageBitmap?, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(94.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = CircleShape,
    ) {
        if (photo != null) {
            Image(photo, contentDescription = "Child profile photo", contentScale = ContentScale.Crop)
        } else {
            val figure = MaterialTheme.colorScheme.outline
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(color = figure, radius = size.minDimension * .145f, center = Offset(center.x, size.height * .34f))
                drawOval(
                    color = figure,
                    topLeft = Offset(size.width * .18f, size.height * .55f),
                    size = Size(size.width * .64f, size.height * .54f),
                )
            }
        }
    }
}

@Composable
private fun WeekDay(
    date: CareDate,
    isToday: Boolean,
    isSelected: Boolean,
    itemCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val title = date.weekdayName()
    Column(
        modifier = modifier.heightIn(min = 68.dp).selectable(selected = isSelected, onClick = onClick, role = Role.Tab)
            .clearAndSetSemantics {
                contentDescription = "$title, ${date.monthAndDay()}, $itemCount " +
                    if (itemCount == 1) "item" else "items"
                if (isToday) stateDescription = "Today"
                selected = isSelected
                role = Role.Tab
                semanticsOnClick {
                    onClick()
                    true
                }
            },
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title.take(3), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        Box(
            modifier = Modifier.size(34.dp)
                .clip(CircleShape)
                .then(if (isToday && !isSelected) Modifier.border(1.dp, colors.primary, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = if (isSelected) colors.primary else Color.Transparent) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        date.day.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) colors.onPrimary else colors.onSurface,
                    )
                }
            }
        }
        Canvas(Modifier.size(5.dp)) {
            if (itemCount > 0) drawCircle(colors.secondary)
        }
    }
}

@Composable
private fun AgendaRow(event: CareEvent, person: String, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (event is Visit) Icons.Filled.DateRange else Icons.AutoMirrored.Filled.List,
                contentDescription = null,
                modifier = Modifier.size(21.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(event.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(person, event.statusLabel()).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            event.time?.let { Text(it.toString(), style = MaterialTheme.typography.labelLarge) }
        }
    }
}

internal fun CareEvent.statusLabel(): String? = when (this) {
    is Visit -> when (status) {
        VisitStatus.PLANNED -> "Planned"
        VisitStatus.BOOKED -> "Booked"
        VisitStatus.COMPLETED -> "Completed"
        VisitStatus.CANCELED -> "Canceled"
    }
    is Task -> if (status == TaskStatus.DONE) "Done" else null
}
