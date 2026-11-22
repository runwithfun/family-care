@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.runwithfun.familycare.records

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
internal actual fun RecordPdfPreview(bytes: ByteArray, modifier: Modifier) {
    val document = remember(bytes) {
        runCatching {
            val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
            PDFDocument(data).also { check(it.pageCount > 0uL) { "The stored PDF has no pages" } }
        }.onFailure { error ->
            println("Could not open the stored Records PDF.\n${error.stackTraceToString()}")
        }.getOrNull()
    }
    if (document == null) {
        Text("Could not display the stored PDF.", modifier = modifier)
    } else {
        UIKitView(
            factory = { PDFView().apply { autoScales = true } },
            modifier = modifier.aspectRatio(612f / 792f).semantics {
                contentDescription = "Stored clinic PDF preview"
            },
            update = { it.document = document },
        )
    }
}
