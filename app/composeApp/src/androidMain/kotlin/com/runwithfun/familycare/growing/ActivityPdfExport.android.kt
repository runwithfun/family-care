package com.runwithfun.familycare.growing

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal actual fun rememberActivityPdfExport(
    onComplete: (Result<Boolean>) -> Unit,
): (String, ByteArray) -> Unit {
    val complete by rememberUpdatedState(onComplete)
    val resolver = LocalContext.current.applicationContext.contentResolver
    var pendingBytes by rememberSaveable { mutableStateOf<ByteArray?>(null) }
    var pendingDestination by rememberSaveable { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri ->
        if (uri == null) {
            pendingBytes = null
            complete(Result.success(false))
        } else {
            pendingDestination = uri.toString()
        }
    }

    val destination = pendingDestination
    if (destination != null) {
        LaunchedEffect(destination) {
            val result: Result<Boolean> = try {
                val bytes = checkNotNull(pendingBytes) { "No PDF bytes were restored for the selected document." }
                withContext(Dispatchers.IO) {
                    val uri = Uri.parse(destination)
                    val stream = resolver.openOutputStream(uri, "wt")
                        ?: throw IOException("Document provider ${uri.authority} did not open a PDF output stream.")
                    stream.use { it.write(bytes) }
                }
                Result.success(true)
            } catch (canceled: CancellationException) {
                throw canceled
            } catch (error: Exception) {
                Result.failure(error)
            }
            pendingDestination = null
            pendingBytes = null
            complete(result)
        }
    }

    return { fileName, bytes ->
        if (pendingBytes == null && pendingDestination == null) {
            pendingBytes = bytes.copyOf()
            try {
                launcher.launch(fileName)
            } catch (error: Exception) {
                pendingBytes = null
                complete(Result.failure(error))
            }
        }
    }
}
