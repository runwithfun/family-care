package com.runwithfun.familycare.records

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal expect fun RecordPdfPreview(bytes: ByteArray, modifier: Modifier, page: Int = 1)
