package com.runwithfun.familycare.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.runwithfun.familycare.demo.DemoFamily
import com.runwithfun.familycare.domain.AgeStage

@Composable
fun ActivityPrintScreen(
    stage: AgeStage,
    childName: String,
    onBack: () -> Unit,
) {
    val activity = remember(stage) { DemoFamily.reviewedActivity(stage) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 44.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
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
        Text("Source", style = MaterialTheme.typography.titleMedium)
        Text(activity.sourceLabel, style = MaterialTheme.typography.bodyMedium)
        Text(activity.sourceUrl, style = MaterialTheme.typography.bodyMedium)
    }
}
