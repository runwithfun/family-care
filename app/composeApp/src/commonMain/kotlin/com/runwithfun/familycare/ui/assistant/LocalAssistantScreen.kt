package com.runwithfun.familycare.ui.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun LocalAssistantScreen(actions: AssistantActions, onSaved: () -> Unit) {
    var showSource by remember(actions) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Assistant", style = MaterialTheme.typography.headlineLarge)

        if (actions.canConfirm) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (actions.confirmed) "Added to your plan" else "Suggested follow-up",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(actions.task.title, style = MaterialTheme.typography.titleLarge)
                Text(
                    "${actions.personName} · ${actions.task.date}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        HorizontalDivider()

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Visit note", style = MaterialTheme.typography.titleMedium)
            Text(
                actions.excerpt ?: "The source excerpt is unavailable.",
                style = MaterialTheme.typography.bodyLarge,
            )
            if (actions.originalText != null) {
                TextButton(
                    onClick = { showSource = true },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("Open original note")
                }
            }
        }

        actions.errorMessage?.let { message ->
            Text(
                message,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (actions.canConfirm) {
            Button(
                onClick = { if (actions.confirm()) onSaved() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Text(if (actions.confirmed) "View Today" else "Add to Today")
            }
        }
    }

    if (showSource) {
        AlertDialog(
            onDismissRequest = { showSource = false },
            title = { Text("Original visit note") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    actions.sourceName?.let { name ->
                        Text(
                            name,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(actions.originalText.orEmpty())
                }
            },
            confirmButton = {
                TextButton(onClick = { showSource = false }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Close")
                }
            },
        )
    }
}
