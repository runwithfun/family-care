package com.runwithfun.familycare.records

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun RecordPdfPreview(bytes: ByteArray, modifier: Modifier) {
    Text("PDF · ${bytes.size} bytes", modifier = modifier)
}
