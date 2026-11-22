package com.runwithfun.familycare.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.runwithfun.familycare.demo.DemoFamily
import com.runwithfun.familycare.domain.AgeStage
import com.runwithfun.familycare.growing.ActivityPaper
import com.runwithfun.familycare.growing.ActivityPdf
import com.runwithfun.familycare.growing.rememberActivityPdfExport

@OptIn(ExperimentalLayoutApi::class)
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
    val type = MaterialTheme.typography

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.heightIn(min = 48.dp),
                    contentPadding = PaddingValues(end = 12.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Back to Growing up")
                }
                Text(
                    activity.title,
                    modifier = Modifier.semantics { heading() },
                    style = type.headlineLarge,
                )
                Text(
                    "For $childName · ${activity.stage.label}",
                    style = type.labelLarge,
                    color = colors.onSurfaceVariant,
                )
                Text(activity.summary, style = type.bodyLarge)
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = colors.surfaceVariant,
                contentColor = colors.onSurface,
                border = BorderStroke(1.dp, colors.outlineVariant),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Paper size", style = type.titleMedium, modifier = Modifier.semantics { heading() })
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (option in ActivityPaper.entries) {
                            FilterChip(
                                selected = paper == option,
                                onClick = { paperName = option.name },
                                label = { Text(option.label) },
                                modifier = Modifier.heightIn(min = 48.dp),
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
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primaryContainer,
                            contentColor = colors.onPrimaryContainer,
                        ),
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Print or save PDF", textAlign = TextAlign.Center)
                    }
                    Text(
                        "On iPhone or iPad, choose Print or Save to Files. Other devices save a PDF.",
                        style = type.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
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
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("How to play", style = type.titleLarge, modifier = Modifier.semantics { heading() })
                activity.steps.forEachIndexed { index, step ->
                    Row(
                        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colors.secondaryContainer,
                            contentColor = colors.onSecondaryContainer,
                        ) {
                            Text("${index + 1}", Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = type.labelLarge)
                        }
                        Text(step, modifier = Modifier.weight(1f), style = type.bodyLarge)
                    }
                }
            }

            HorizontalDivider(color = colors.outlineVariant)
            Column {
                Text("Source", style = type.labelLarge, color = colors.onSurfaceVariant)
                TextButton(
                    onClick = { uriHandler.openUri(activity.sourceUrl) },
                    modifier = Modifier.heightIn(min = 48.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    Text(activity.sourceLabel, textDecoration = TextDecoration.Underline)
                }
            }
        }
    }
}
