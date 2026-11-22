package com.runwithfun.familycare.records

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

@Composable
internal fun rememberRecordImportAction(
    records: RecordsController,
    onImported: (personId: String, documentId: String) -> Unit,
): (String) -> Unit {
    var pendingOwner by rememberSaveable { mutableStateOf<String?>(null) }
    val onResult: (Result<LocalRecordPdf?>) -> Unit = { result ->
        val owner = pendingOwner
        pendingOwner = null
        owner?.let { personId ->
            records.completeLocalPdfImport(personId, result)?.let { onImported(personId, it) }
        }
    }
    val choose = rememberLocalRecordPdfPicker(onResult)
    return { owner ->
        if (pendingOwner == null) {
            pendingOwner = owner
            try {
                choose()
            } catch (error: Exception) {
                onResult(Result.failure(error))
            }
        }
    }
}
