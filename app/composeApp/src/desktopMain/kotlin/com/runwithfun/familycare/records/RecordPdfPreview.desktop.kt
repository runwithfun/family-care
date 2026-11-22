package com.runwithfun.familycare.records

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun RecordPdfPreview(bytes: ByteArray, modifier: Modifier, page: Int) {
    val description = "PDF · ${bytes.size} bytes"
    val pageNote = if (page == 1) "" else " · Page $page preview unavailable"
    Text(description + pageNote, modifier = modifier)
}
