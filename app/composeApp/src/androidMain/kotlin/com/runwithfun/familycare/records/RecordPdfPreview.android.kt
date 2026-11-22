package com.runwithfun.familycare.records

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal actual fun RecordPdfPreview(bytes: ByteArray, modifier: Modifier) {
    val cacheDir = LocalContext.current.cacheDir
    val preview by produceState<Result<ImageBitmap>?>(null, bytes, cacheDir) {
        value = null
        value = withContext(Dispatchers.IO) {
            runCatching { renderPage(bytes, cacheDir) }.onFailure { error ->
                println("Could not render the stored Records PDF.\n${error.stackTraceToString()}")
            }
        }
    }
    val result = preview
    if (result == null) {
        Text("Opening PDF…", modifier = modifier)
    } else {
        result.fold(
            onSuccess = { Image(it, contentDescription = "Original clinic PDF, page 1", modifier = modifier) },
            onFailure = { Text("Could not display the stored PDF.", modifier = modifier) },
        )
    }
}

private fun renderPage(bytes: ByteArray, cacheDir: File): ImageBitmap {
    val file = File.createTempFile("family-care-record-", ".pdf", cacheDir)
    try {
        file.writeBytes(bytes)
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = try {
            PdfRenderer(descriptor)
        } catch (error: Exception) {
            runCatching { descriptor.close() }.exceptionOrNull()?.let(error::addSuppressed)
            throw error
        }
        return renderer.use {
            check(it.pageCount > 0) { "The stored PDF has no pages" }
            it.openPage(0).use { page ->
                val scale = minOf(900f / page.width, 1200f / page.height)
                val width = (page.width * scale).toInt().coerceAtLeast(1)
                val height = (page.height * scale).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap.asImageBitmap()
            }
        }
    } finally {
        runCatching {
            check(file.delete() || !file.exists()) { "Could not remove the PDF preview at ${file.path}" }
        }.onFailure { println(it.stackTraceToString()) }
    }
}
