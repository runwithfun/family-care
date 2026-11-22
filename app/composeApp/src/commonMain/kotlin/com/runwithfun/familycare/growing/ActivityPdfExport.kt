package com.runwithfun.familycare.growing

import androidx.compose.runtime.Composable

@Composable
internal expect fun rememberActivityPdfExport(
    onComplete: (Result<Boolean>) -> Unit,
): (String, ByteArray) -> Unit
