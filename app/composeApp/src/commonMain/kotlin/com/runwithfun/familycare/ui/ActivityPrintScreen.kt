package com.runwithfun.familycare.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import com.runwithfun.familycare.domain.AgeStage
import com.runwithfun.familycare.growing.ActivityPdf
import com.runwithfun.familycare.growing.saveLocalPdf

@Composable
fun ActivityPrintScreen(
    stage: AgeStage,
    childName: String,
    onBack: () -> Unit,
) {
    val activity = remember(stage) { DemoFamily.reviewedActivity(stage) }
    var exportPath by remember { mutableStateOf<String?>(null) }
    var exportError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(onClick = onBack) {
            Text("Back to Growing up")
        }
        Text(activity.title, style = MaterialTheme.typography.headlineSmall)
        Text(
            "For $childName · ${activity.stage.label}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(activity.summary, style = MaterialTheme.typography.bodyMedium)
        HorizontalDivider()
        Text("Steps", style = MaterialTheme.typography.titleMedium)
        activity.steps.forEachIndexed { index, step ->
            Text("${index + 1}. $step", style = MaterialTheme.typography.bodyMedium)
        }
        HorizontalDivider()
        Button(
            onClick = {
                try {
                    val bytes = ActivityPdf.render(activity, childName)
                    exportPath = saveLocalPdf(ActivityPdf.fileName(activity), bytes)
                    exportError = null
                } catch (error: Exception) {
                    exportPath = null
                    exportError = "${error::class.simpleName}: ${error.message}"
                }
            },
        ) {
            Text("Export PDF")
        }
        exportPath?.let {
            Text("Saved locally: $it", style = MaterialTheme.typography.bodyMedium)
        }
        exportError?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
