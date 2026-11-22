package com.runwithfun.familycare.records

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal actual fun RecordPdfPreview(bytes: ByteArray, modifier: Modifier, page: Int) {
    val cacheDir = LocalContext.current.cacheDir
    var selectedPage by remember(bytes, page) { mutableIntStateOf(page) }
    var pageCount by remember(bytes) { mutableIntStateOf(0) }
    val displayedPage = selectedPage
    val preview by key(bytes, cacheDir, displayedPage) {
        produceState<Result<RenderedPdfPage>?>(null) {
            val rendered = withContext(Dispatchers.IO) {
                runCatching { renderPage(bytes, cacheDir, displayedPage) }.onFailure { error ->
                    println("Could not render the stored Records PDF. Requested page $displayedPage.\n${error.stackTraceToString()}")
                }
            }
            rendered.getOrNull()?.let { pageCount = it.pageCount }
            value = rendered
        }
    }
    Column(modifier) {
        if (pageCount > 1 && displayedPage in 1..pageCount) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { if (selectedPage > 1) selectedPage-- },
                    modifier = Modifier.weight(1f),
                    enabled = displayedPage > 1,
                ) {
                    Text("Previous page")
                }
                Text(
                    "Page $displayedPage of $pageCount",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                TextButton(
                    onClick = { if (selectedPage < pageCount) selectedPage++ },
                    modifier = Modifier.weight(1f),
                    enabled = displayedPage < pageCount,
                ) {
                    Text("Next page")
                }
            }
        }
        val result = preview
        if (result == null) {
            Text("Opening PDF…")
        } else {
            result.fold(
                onSuccess = {
                    Image(
                        it.image,
                        contentDescription = "Original clinic PDF, page $displayedPage",
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                onFailure = { Text("Could not display the stored PDF.") },
            )
        }
    }
}

private data class RenderedPdfPage(val image: ImageBitmap, val pageCount: Int)

private fun renderPage(bytes: ByteArray, cacheDir: File, pageNumber: Int): RenderedPdfPage {
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
            val pageCount = it.pageCount
            check(pageCount > 0) { "The stored PDF has no pages" }
            check(pageNumber in 1..pageCount) {
                "The stored PDF has $pageCount pages. Requested page $pageNumber."
            }
            it.openPage(pageNumber - 1).use { page ->
                val scale = minOf(900f / page.width, 1200f / page.height)
                val width = (page.width * scale).toInt().coerceAtLeast(1)
                val height = (page.height * scale).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                RenderedPdfPage(bitmap.asImageBitmap(), pageCount)
            }
        }
    } finally {
        runCatching {
            check(file.delete() || !file.exists()) { "Could not remove the PDF preview at ${file.path}" }
        }.onFailure { println(it.stackTraceToString()) }
    }
}
