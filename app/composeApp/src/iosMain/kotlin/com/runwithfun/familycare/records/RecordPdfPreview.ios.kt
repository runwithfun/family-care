@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.runwithfun.familycare.records

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.PDFKit.PDFDocument
import platform.PDFKit.PDFView
import platform.PDFKit.kPDFDisplaySinglePage

@Composable
internal actual fun RecordPdfPreview(bytes: ByteArray, modifier: Modifier, page: Int) {
    val document = remember(bytes) {
        runCatching {
            check(bytes.isNotEmpty()) { "The stored PDF is empty" }
            val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
            PDFDocument(data).also { check(it.pageCount > 0uL) { "The stored PDF has no pages" } }
        }.onFailure { error ->
            println("Could not open the stored Records PDF.\n${error.stackTraceToString()}")
        }.getOrNull()
    }
    val pageCount = document?.pageCount?.coerceAtMost(Int.MAX_VALUE.toULong())?.toInt() ?: 0
    var pageNumber by remember(document, page) { mutableIntStateOf(page) }
    val displayedPage = pageNumber
    val selectedPage = remember(document, displayedPage) {
        document?.let {
            runCatching {
                check(displayedPage in 1..pageCount) {
                    "The stored PDF has ${it.pageCount} pages. Requested page $displayedPage."
                }
                checkNotNull(it.pageAtIndex((displayedPage - 1).toULong())) {
                    "PDF page $displayedPage is unavailable"
                }
            }.onFailure { error ->
                println("Could not open page $displayedPage of the stored Records PDF.\n${error.stackTraceToString()}")
            }.getOrNull()
        }
    }
    Column(modifier) {
        if (pageCount > 1 && displayedPage in 1..pageCount) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { if (pageNumber > 1) pageNumber-- },
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
                    onClick = { if (pageNumber < pageCount) pageNumber++ },
                    modifier = Modifier.weight(1f),
                    enabled = displayedPage < pageCount,
                ) {
                    Text("Next page")
                }
            }
        }
        if (document == null || selectedPage == null) {
            Text("Could not display the stored PDF.")
        } else {
            key(document) {
                UIKitView(
                    factory = {
                        PDFView().apply {
                            if (pageCount > 1) displayMode = kPDFDisplaySinglePage
                            autoScales = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth().aspectRatio(612f / 792f).semantics {
                        contentDescription = if (displayedPage == 1) {
                            "Stored clinic PDF preview"
                        } else {
                            "Stored clinic PDF preview, page $displayedPage"
                        }
                    },
                    update = { view ->
                        if (view.document != document) view.document = document
                        if (view.currentPage != selectedPage) view.goToPage(selectedPage)
                    },
                )
            }
        }
    }
}
