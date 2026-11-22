package com.runwithfun.familycare.growing

import androidx.compose.runtime.Composable

@Composable
internal actual fun rememberActivityPdfExport(
    onComplete: (Result<Boolean>) -> Unit,
): (String, ByteArray) -> Unit = { fileName, bytes ->
    onComplete(
        runCatching {
            saveLocalPdf(fileName, bytes)
            true
        },
    )
}
