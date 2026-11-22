package com.runwithfun.familycare.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.runwithfun.familycare.demo.DemoFamily
import com.runwithfun.familycare.domain.AgeStage
import com.runwithfun.familycare.growing.ActivityPaper
import com.runwithfun.familycare.growing.ActivityPdf
import com.runwithfun.familycare.growing.rememberActivityPdfExport

@Composable
fun ActivityPrintScreen(
    stage: AgeStage,
    childName: String,
    onBack: () -> Unit,
) {
    val activity = remember(stage) { DemoFamily.reviewedActivity(stage) }
    val uriHandler = LocalUriHandler.current
    var paperName by rememberSaveable { mutableStateOf(ActivityPaper.LETTER.name) }
    val paper = ActivityPaper.valueOf(paperName)
    var exporting by rememberSaveable { mutableStateOf(false) }
    var exported by rememberSaveable { mutableStateOf(false) }
    var exportFailed by rememberSaveable { mutableStateOf(false) }
    val export = rememberActivityPdfExport { result ->
        result.exceptionOrNull()?.let { error ->
            println("Could not export activity ${activity.id} as ${paper.label}\n${error.stackTraceToString()}")
        }
        exported = result.getOrDefault(false)
        exportFailed = result.isFailure
        exporting = false
    }
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Back to Growing up")
        }
        Text(
            activity.title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(
            "For $childName · ${activity.stage.label}",
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant,
        )
        Text(activity.summary, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (option in ActivityPaper.entries) {
                FilterChip(
                    selected = paper == option,
                    onClick = { paperName = option.name },
                    label = { Text(option.label) },
                    enabled = !exporting,
                )
            }
        }
        Button(
            onClick = {
                exporting = true
                exported = false
                exportFailed = false
                try {
                    export(ActivityPdf.fileName(activity), ActivityPdf.render(activity, paper))
                } catch (error: Exception) {
                    println("Could not create activity PDF ${activity.id} as ${paper.label}\n${error.stackTraceToString()}")
                    exporting = false
                    exportFailed = true
                }
            },
            enabled = !exporting,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primaryContainer,
                contentColor = colors.onPrimaryContainer,
            ),
        ) {
            Text("Export PDF")
        }
        if (exported) {
            Text("PDF exported", modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        if (exportFailed) {
            Text(
                "Couldn't export the PDF. Try again.",
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = colors.error,
            )
        }
        HorizontalDivider(color = colors.outlineVariant)
        Text("Steps", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        activity.steps.forEachIndexed { index, step ->
            Text("${index + 1}. $step", style = MaterialTheme.typography.bodyLarge)
        }
        TextButton(
            onClick = { uriHandler.openUri(activity.sourceUrl) },
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(activity.sourceLabel)
        }
    }
}
