package com.runwithfun.familycare.growing

import java.io.File

actual fun saveLocalPdf(fileName: String, bytes: ByteArray): String {
    val directory = File(System.getProperty("java.io.tmpdir"), "family-care")
    if (!directory.exists()) directory.mkdirs()
    val file = File(directory, fileName)
    file.writeBytes(bytes)
    return file.absolutePath
}
