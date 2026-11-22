package com.runwithfun.familycare.records

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import java.awt.FileDialog
import java.awt.Frame
import java.io.ByteArrayOutputStream
import java.io.FilenameFilter
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal actual fun rememberLocalRecordPdfPicker(onResult: (Result<LocalRecordPdf?>) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val resultHandler = rememberUpdatedState(onResult)
    var busy by remember { mutableStateOf(false) }

    return launchPicker@{
        if (busy) return@launchPicker
        busy = true
        scope.launch {
            val result = try {
                val dialog = FileDialog(null as Frame?, "Choose a clinic PDF", FileDialog.LOAD).apply {
                    filenameFilter = FilenameFilter { _, name -> name.endsWith(".pdf", ignoreCase = true) }
                }
                val file = try {
                    dialog.isVisible = true
                    dialog.files.firstOrNull()
                } finally {
                    dialog.dispose()
                }
                Result.success(file?.let {
                    withContext(Dispatchers.IO) { LocalRecordPdf(it.name, it.inputStream().use(InputStream::readBoundedRecordPdf)) }
                })
            } catch (canceled: CancellationException) {
                throw canceled
            } catch (error: Exception) {
                Result.failure(error)
            } finally {
                busy = false
            }
            resultHandler.value(result)
        }
    }
}

internal actual fun recordPdfSha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).recordPdfDigestHex()

@Composable
internal actual fun rememberImportedPdfStorage(): ImportedPdfStorage = remember {
    DesktopImportedPdfStorage(Paths.get(System.getProperty("user.home"), ".family-care", "record-pdfs"))
}

private class DesktopImportedPdfStorage(private val directory: Path) : ImportedPdfStorage {
    override fun write(hash: String, bytes: ByteArray) {
        val destination = pdfPath(hash)
        requireRecordPdfDigest(hash, bytes)
        if ("posix" in directory.fileSystem.supportedFileAttributeViews()) {
            Files.createDirectories(directory, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
        } else {
            Files.createDirectories(directory)
        }
        val temporary = Files.createTempFile(directory, "record-", ".tmp")
        try {
            Files.write(temporary, bytes)
            Files.move(temporary, destination, ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (failure: Exception) {
            runCatching { Files.deleteIfExists(temporary) }.exceptionOrNull()?.let(failure::addSuppressed)
            throw failure
        }
    }

    override fun read(hash: String): ByteArray? {
        val path = pdfPath(hash)
        return try {
            Files.newInputStream(path).use { it.readBoundedRecordPdf() }.also { requireRecordPdfDigest(hash, it) }
        } catch (_: NoSuchFileException) {
            null
        }
    }

    override fun delete(hash: String) {
        Files.deleteIfExists(pdfPath(hash))
    }

    private fun pdfPath(hash: String): Path {
        requireRecordPdfHash(hash)
        return directory.resolve("$hash.pdf")
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
