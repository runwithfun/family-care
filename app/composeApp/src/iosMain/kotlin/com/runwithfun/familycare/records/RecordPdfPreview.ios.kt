@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.runwithfun.familycare.records

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.PDFKit.PDFDocument
import platform.PDFKit.PDFView

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
    val selectedPage = remember(document, page) {
        document?.let {
            runCatching {
                check(page > 0 && page.toULong() <= it.pageCount) {
                    "The stored PDF has ${it.pageCount} pages. Requested page $page."
                }
                checkNotNull(it.pageAtIndex((page - 1).toULong())) { "PDF page $page is unavailable" }
            }.onFailure { error ->
                println("Could not open page $page of the stored Records PDF.\n${error.stackTraceToString()}")
            }.getOrNull()
        }
    }
    if (document == null || selectedPage == null) {
        Text("Could not display the stored PDF.", modifier = modifier)
    } else {
        key(document, page) {
            UIKitView(
                factory = { PDFView().apply { autoScales = true } },
                modifier = modifier.aspectRatio(612f / 792f).semantics {
                    contentDescription = if (page == 1) "Stored clinic PDF preview" else "Stored clinic PDF preview, page $page"
                },
                update = { view ->
                    if (view.document != document) {
                        view.document = document
                        view.goToPage(selectedPage)
                    }
                },
            )
        }
    }
}
