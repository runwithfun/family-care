package com.runwithfun.familycare.records

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.system.Os
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal actual fun rememberLocalRecordPdfPicker(onResult: (Result<LocalRecordPdf?>) -> Unit): () -> Unit {
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    val resultHandler = rememberUpdatedState(onResult)
    var waitingForPicker by rememberSaveable { mutableStateOf(false) }
    var readingPdf by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        waitingForPicker = false
        if (uri == null) {
            resultHandler.value(Result.success(null))
        } else {
            readingPdf = true
            scope.launch {
                val result = try {
                    Result.success(withContext(Dispatchers.IO) { resolver.readLocalRecordPdf(uri) })
                } catch (canceled: CancellationException) {
                    throw canceled
                } catch (error: Exception) {
                    Result.failure(error)
                } finally {
                    readingPdf = false
                }
                resultHandler.value(result)
            }
        }
    }

    return launchPicker@{
        if (waitingForPicker || readingPdf) return@launchPicker
        waitingForPicker = true
        try {
            picker.launch(arrayOf("application/pdf"))
        } catch (error: Exception) {
            waitingForPicker = false
            resultHandler.value(Result.failure(error))
        }
    }
}

internal actual fun recordPdfSha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).recordPdfDigestHex()

@Composable
internal actual fun rememberImportedPdfStorage(): ImportedPdfStorage {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidImportedPdfStorage(File(context.noBackupFilesDir, "record-pdfs")) }
}

private fun ContentResolver.readLocalRecordPdf(uri: Uri): LocalRecordPdf {
    val name = query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    }
    val bytes = openInputStream(uri)?.use { it.readBoundedRecordPdf() }
        ?: throw IOException("Could not open the selected PDF at $uri.")
    return LocalRecordPdf(name?.takeIf { it.isNotBlank() } ?: "Document.pdf", bytes)
}

private class AndroidImportedPdfStorage(private val directory: File) : ImportedPdfStorage {
    override fun write(hash: String, bytes: ByteArray) {
        val destination = pdfFile(hash)
        requireRecordPdfDigest(hash, bytes)
        check(directory.mkdirs() || directory.isDirectory) { "Could not create the PDF store at $directory." }
        val temporary = File.createTempFile("record-", ".tmp", directory)
        try {
            FileOutputStream(temporary).use { stream ->
                stream.write(bytes)
                stream.fd.sync()
            }
            Os.rename(temporary.path, destination.path)
        } catch (failure: Exception) {
            if (!temporary.delete() && temporary.exists()) {
                failure.addSuppressed(IOException("Could not remove the failed PDF write at $temporary."))
            }
            throw failure
        }
    }

    override fun read(hash: String): ByteArray? {
        val file = pdfFile(hash)
        if (!file.exists()) return null
        return file.inputStream().use { it.readBoundedRecordPdf() }.also { requireRecordPdfDigest(hash, it) }
    }

    override fun delete(hash: String) {
        val file = pdfFile(hash)
        check(file.delete() || !file.exists()) { "Could not remove the stored PDF at $file." }
    }

    private fun pdfFile(hash: String): File {
        requireRecordPdfHash(hash)
        return File(directory, "$hash.pdf")
    }
}

private fun InputStream.readBoundedRecordPdf(): ByteArray = ByteArrayOutputStream().use { output ->
    val buffer = ByteArray(16 * 1024)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        requireRecordPdfSize(output.size() + count)
        output.write(buffer, 0, count)
    }
    requireRecordPdfSize(output.size())
    output.toByteArray()
}
