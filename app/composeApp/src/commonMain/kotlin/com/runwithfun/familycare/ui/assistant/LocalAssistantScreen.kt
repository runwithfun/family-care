package com.runwithfun.familycare.ui.assistant

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.runwithfun.familycare.demo.DemoFamily
import com.runwithfun.familycare.ui.home.monthAndDay

@Composable
fun LocalAssistantScreen(actions: AssistantActions, onSaved: () -> Unit, onOpenCalendar: () -> Unit) {
    var showSource by remember(actions) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 560.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Assistant", Modifier.semantics { heading() }, style = typography.headlineSmall)
            Surface(
                color = lerp(colors.surface, colors.primaryContainer, 0.18f),
                contentColor = colors.onSurface,
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, colors.outlineVariant),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (actions.canConfirm) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (actions.confirmed) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = colors.secondary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text("Added to your plan", style = typography.labelLarge)
                                }
                            }
                            val recipient = if (actions.task.personId == DemoFamily.motherId) {
                                "For you"
                            } else {
                                actions.personName
                            }
                            Text(
                                "$recipient · ${actions.task.date.monthAndDay()}",
                                style = typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                            )
                            Text(actions.task.title, style = typography.titleLarge)
                        }
                    }

                    VisitNoteExcerpt(
                        excerpt = actions.excerpt,
                        onOpen = if (actions.originalText != null) ({ showSource = true }) else null,
                    )

                    actions.errorMessage?.let { message ->
                        Text(
                            message,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            style = typography.bodyMedium,
                            color = colors.error,
                        )
                    }

                    if (actions.visitHasChanged) {
                        Text("The visit has changed. Check its date or status in Calendar.", style = typography.bodyMedium)
                        TextButton(onClick = onOpenCalendar, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Open Calendar")
                        }
                    }

                    if (actions.canConfirm) {
                        val actionModifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        if (actions.confirmed) {
                            OutlinedButton(
                                onClick = onSaved,
                                modifier = actionModifier,
                                border = BorderStroke(1.dp, colors.secondary),
                            ) {
                                Text(if (actions.task.date == DemoFamily.today) "View Today" else "View in plan")
                            }
                        } else {
                            Button(
                                onClick = { if (actions.confirm()) onSaved() },
                                modifier = actionModifier,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.primaryContainer,
                                    contentColor = colors.onPrimaryContainer,
                                ),
                            ) {
                                Text("Add to Today")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSource) {
        OriginalVisitNote(actions.sourceName, actions.originalText.orEmpty(), onClose = { showSource = false })
    }
}

@Composable
private fun VisitNoteExcerpt(excerpt: String?, onOpen: (() -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    Surface(
        color = colors.surfaceVariant,
        contentColor = colors.onSurface,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text("From the visit note", style = typography.labelLarge, color = colors.onSurfaceVariant)
            Text(
                excerpt?.let { "“$it”" } ?: "The source excerpt is unavailable.",
                style = typography.bodyLarge,
            )
            if (onOpen != null) {
                TextButton(
                    onClick = onOpen,
                    modifier = Modifier.heightIn(min = 48.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    Text("Open original note", textDecoration = TextDecoration.Underline)
                }
            }
        }
    }
}

@Composable
private fun OriginalVisitNote(sourceName: String?, originalText: String, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Original visit note", Modifier.semantics { heading() }) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sourceName?.let { name ->
                    Text(
                        name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(originalText, style = MaterialTheme.typography.bodyLarge)
            }
        },
        confirmButton = {
            TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Close")
            }
        },
    )
}
