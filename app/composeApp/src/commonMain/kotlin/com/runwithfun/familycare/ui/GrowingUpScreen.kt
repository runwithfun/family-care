package com.runwithfun.familycare.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.runwithfun.familycare.demo.DemoFamily
import com.runwithfun.familycare.domain.AgeStage
import com.runwithfun.familycare.domain.CarePlan
import com.runwithfun.familycare.domain.GrowingObservation
import com.runwithfun.familycare.growing.GrowingUpActions

@Composable
fun GrowingUpScreen(
    plan: CarePlan,
    onOpenActivity: (AgeStage) -> Unit,
    onBack: () -> Unit,
) {
    var stage by remember { mutableStateOf(AgeStage.NEWBORN_0_2_MONTHS) }
    var draftNote by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var historyTick by remember { mutableIntStateOf(0) }
    historyTick
    val child = plan.people.first { it.id == DemoFamily.childId }
    val history = plan.observations(DemoFamily.childId)
    val stageHistory = plan.observations(DemoFamily.childId, stage)

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(onClick = onBack) {
            Text("Back")
        }
        Text("Growing up", style = MaterialTheme.typography.headlineSmall)
        Text(
            "${child.displayName}'s milestones stay saved when you switch stages.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Age stage", style = MaterialTheme.typography.titleMedium)
            for (option in AgeStage.entries) {
                FilterChip(
                    selected = stage == option,
                    onClick = {
                        stage = option
                        statusMessage = null
                    },
                    label = { Text("${option.title} · ${option.label}") },
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
                        append(observation.recordedOn.toString())
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
                Text("Saved as a discussion point for today's well-baby visit.")
            },
        )
        Button(
            onClick = {
                val note = draftNote.trim()
                if (note.isEmpty()) {
                    statusMessage = "Enter a short observation before saving."
                    return@Button
                }
                val observation = GrowingObservation(
                    id = "local-obs-${historyTick + history.size + 1}",
                    personId = DemoFamily.childId,
                    stage = stage,
                    note = note,
                    recordedOn = DemoFamily.today,
                    forVisitId = DemoFamily.childVisitId,
                )
                GrowingUpActions.saveObservation(plan, observation)
                draftNote = ""
                historyTick++
                statusMessage = "Saved for the next visit discussion."
            },
        ) {
            Text("Save for next visit")
        }
        statusMessage?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }

        HorizontalDivider()
        Text("Printable activity", style = MaterialTheme.typography.titleMedium)
        Text(
            DemoFamily.reviewedActivity(stage).title,
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = { onOpenActivity(stage) }) {
            Text("Open activity")
        }
    }
}
