package com.runwithfun.familycare.records

import androidx.compose.runtime.Composable

internal const val MaxLocalRecordPdfBytes = 20 * 1024 * 1024

internal data class LocalRecordPdf(val fileName: String, val bytes: ByteArray)

@Composable
internal expect fun rememberLocalRecordPdfPicker(onResult: (Result<LocalRecordPdf?>) -> Unit): () -> Unit

internal expect fun recordPdfSha256(bytes: ByteArray): String

internal interface ImportedPdfStorage {
    fun write(hash: String, bytes: ByteArray)
    fun read(hash: String): ByteArray?
    fun delete(hash: String)
}

@Composable
internal expect fun rememberImportedPdfStorage(): ImportedPdfStorage

internal fun requireRecordPdfHash(hash: String) {
    require(hash.length == 64 && hash.all { it in '0'..'9' || it in 'a'..'f' }) {
        "Invalid SHA-256 for a local PDF: $hash"
    }
}

internal fun requireRecordPdfSize(size: Int) {
    require(size in 1..MaxLocalRecordPdfBytes) {
        "The selected PDF must contain between 1 and $MaxLocalRecordPdfBytes bytes. Read $size bytes."
    }
}

internal fun requireRecordPdfDigest(hash: String, bytes: ByteArray) {
    requireRecordPdfSize(bytes.size)
    check(recordPdfSha256(bytes) == hash) { "The stored PDF content does not match SHA-256 $hash." }
}

internal fun ByteArray.recordPdfDigestHex(): String = buildString(size * 2) {
    val digits = "0123456789abcdef"
    for (byte in this@recordPdfDigestHex) {
        val value = byte.toInt() and 0xff
        append(digits[value shr 4])
        append(digits[value and 0xf])
    }
}
