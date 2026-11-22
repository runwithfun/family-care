package com.runwithfun.familycare.growing

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.create
import platform.Foundation.writeToFile

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual fun saveLocalPdf(fileName: String, bytes: ByteArray): String {
    val path = NSTemporaryDirectory() + fileName
    val data = bytes.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
    }
    val wrote = data.writeToFile(path, atomically = true)
    check(wrote) { "Failed to write local PDF at $path" }
    return path
}
