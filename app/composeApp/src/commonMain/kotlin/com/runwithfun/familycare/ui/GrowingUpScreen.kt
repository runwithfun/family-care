package com.runwithfun.familycare.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.runwithfun.familycare.demo.DemoFamily
import com.runwithfun.familycare.domain.AgeStage
import com.runwithfun.familycare.domain.CarePlan
import com.runwithfun.familycare.domain.FamilyMember
import com.runwithfun.familycare.growing.GrowingNotes
import com.runwithfun.familycare.resources.Res
import com.runwithfun.familycare.resources.growing_book
import com.runwithfun.familycare.ui.home.monthAndDay
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun GrowingUpScreen(
    plan: CarePlan,
    child: FamilyMember,
    notes: GrowingNotes,
    onOpenActivity: (AgeStage) -> Unit,
    onSaved: () -> Unit,
) {
    var stageKey by rememberSaveable { mutableStateOf(AgeStage.NEWBORN_0_2_MONTHS.name) }
    val stage = AgeStage.valueOf(stageKey)
    var draftNote by rememberSaveable { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val history = notes.observations(child.id)
    val stageHistory = notes.observations(child.id, stage)
    val nextVisit = plan.nextEligibleVisit(child.id, DemoFamily.today)
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Growing up", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
                Text(child.displayName, color = colors.onSurfaceVariant)
            }
            Image(
                painter = painterResource(Res.drawable.growing_book),
                contentDescription = null,
                modifier = Modifier.width(108.dp),
            )
        }
        Column {
            for (option in AgeStage.entries) {
                FilterChip(
                    selected = stage == option,
                    onClick = {
                        stageKey = option.name
                        statusMessage = null
                    },
                    label = { Text("${option.title} · ${option.label}") },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }

        HorizontalDivider()
        Text("Observations · ${stage.label}", style = MaterialTheme.typography.titleMedium)
        if (stageHistory.isEmpty()) {
            Text("No notes for this stage yet.", style = MaterialTheme.typography.bodyMedium)
        } else {
            for (observation in stageHistory) {
                Column {
                    Text(observation.note, style = MaterialTheme.typography.titleMedium)
                    val detail = buildString {
                        append(observation.recordedOn.monthAndDay())
                        if (observation.forVisitId != null) append(" · next visit discussion")
                    }
                    Text(detail, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Text(
            "All stages: ${history.size} saved",
            style = MaterialTheme.typography.bodyMedium,
        )

        OutlinedTextField(
            value = draftNote,
            onValueChange = { draftNote = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("New observation") },
            supportingText = {
                Text(
                    if (nextVisit == null) "You can save this note for later."
                    else "Save a discussion point for the next visit.",
                )
            },
        )
        Button(
            onClick = {
                try {
                    val observation = notes.save(child.id, stage, draftNote, DemoFamily.today)
                    draftNote = ""
                    statusMessage = if (observation.forVisitId == null) {
                        "Observation saved."
                    } else {
                        "Saved with a discussion point for the next visit."
                    }
                    onSaved()
                } catch (_: Exception) {
                    statusMessage = "Could not save this observation. Try again."
                }
            },
            enabled = draftNote.isNotBlank(),
        ) {
            Text("Save observation")
        }
        statusMessage?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = colors.surfaceVariant,
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Printable activity", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                Text(
                    DemoFamily.reviewedActivity(stage).title,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface,
                )
                TextButton(onClick = { onOpenActivity(stage) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Open activity")
                }
            }
        }
    }
}
