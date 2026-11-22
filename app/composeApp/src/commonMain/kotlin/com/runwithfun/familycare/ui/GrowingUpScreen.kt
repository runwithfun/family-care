package com.runwithfun.familycare.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.runwithfun.familycare.demo.DemoFamily
import com.runwithfun.familycare.domain.AgeStage
import com.runwithfun.familycare.domain.CarePlan
import com.runwithfun.familycare.domain.FamilyMember
import com.runwithfun.familycare.domain.ReviewedActivity
import com.runwithfun.familycare.domain.suggestedAgeStage
import com.runwithfun.familycare.growing.GrowingNotes
import com.runwithfun.familycare.resources.Res
import com.runwithfun.familycare.resources.growing_book
import com.runwithfun.familycare.resources.growing_mother_and_baby
import com.runwithfun.familycare.ui.home.monthAndDay
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GrowingUpScreen(
    plan: CarePlan,
    child: FamilyMember,
    notes: GrowingNotes,
    onOpenActivity: (AgeStage) -> Unit,
    onSaved: () -> Unit,
) {
    var selectedStageKey by rememberSaveable { mutableStateOf<String?>(null) }
    val suggestedStage = suggestedAgeStage(child.birthDate, DemoFamily.today)
    val stage = selectedStageKey?.let(AgeStage::valueOf) ?: suggestedStage
    var draftNote by rememberSaveable { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Growing up",
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                "Play and observations for ${child.displayName}",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Choose an age",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            FlowRow(
                modifier = Modifier.selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (option in AgeStage.entries) {
                    FilterChip(
                        selected = stage == option,
                        onClick = {
                            selectedStageKey = option.name
                            statusMessage = null
                        },
                        label = { Text(option.label) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
            if (suggestedStage != null) {
                Text(
                    "Suggested for ${child.displayName} · ${suggestedStage.label}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }

        if (stage == null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (child.birthDate == null) "Select an age to see a play card."
                    else "Play cards currently cover the first four months. You can browse them by age above.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                )
                Image(
                    painter = painterResource(Res.drawable.growing_book),
                    contentDescription = null,
                    modifier = Modifier.size(112.dp),
                )
            }
            return@Column
        }

        GrowingPlayCard(DemoFamily.reviewedActivity(stage)) { onOpenActivity(stage) }

        val history = notes.observations(child.id)
        val stageHistory = notes.observations(child.id, stage)
        val nextVisit = plan.nextEligibleVisit(child.id, DemoFamily.today)

        HorizontalDivider(color = colors.outlineVariant)
        Text(
            "Observations · ${stage.label}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
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
        OutlinedButton(
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
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text("Save observation")
        }
        statusMessage?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Composable
private fun GrowingPlayCard(activity: ReviewedActivity, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surface,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Play card · ${activity.stage.label}",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurfaceVariant,
                    )
                    Text(
                        activity.title,
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(activity.summary, style = MaterialTheme.typography.bodyMedium)
                }
                if (LocalDensity.current.fontScale <= 1.3f) {
                    Surface(color = Color.White, shape = RoundedCornerShape(16.dp)) {
                        Image(
                            painter = painterResource(Res.drawable.growing_mother_and_baby),
                            contentDescription = null,
                            modifier = Modifier.size(104.dp),
                        )
                    }
                }
            }
            Button(
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primaryContainer,
                    contentColor = colors.onPrimaryContainer,
                )
            ) {
                Text("Open printable card")
            }
            Text(
                activity.sourceLabel,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}
