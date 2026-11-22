package com.runwithfun.familycare.records

/**
 * Builds a minimal synthetic PDF whose content stream includes [bodyText].
 * Used only for on-device demo import — not a clinical document.
 */
fun buildSyntheticClinicPdf(bodyText: String): ByteArray {
    val escaped = buildString {
        for (char in bodyText) {
            when (char) {
                '\\', '(', ')' -> append('\\').append(char)
                '\r' -> Unit
                '\n' -> append(") Tj T* (")
                in ' '..'~' -> append(char)
                '–' -> append("\\226")
                '—' -> append("\\227")
                in '\u00a0'..'\u00ff' -> append('\\').append(char.code.toString(8).padStart(3, '0'))
                else -> append('?')
            }
        }
    }
    val stream = "BT /F1 11 Tf 14 TL 72 720 Td ($escaped) Tj ET\n"
    val objects = listOf(
        "1 0 obj<< /Type /Catalog /Pages 2 0 R >>endobj\n",
        "2 0 obj<< /Type /Pages /Kids [3 0 R] /Count 1 >>endobj\n",
        "3 0 obj<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] " +
            "/Contents 4 0 R /Resources<< /Font<< /F1 5 0 R >> >> >>endobj\n",
        "4 0 obj<< /Length ${stream.encodeToByteArray().size} >>stream\n${stream}endstream\nendobj\n",
        "5 0 obj<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>endobj\n",
    )
    val header = "%PDF-1.4\n%\u00E2\u00E3\u00CF\u00D3\n"
    val builder = StringBuilder(header)
    val offsets = IntArray(objects.size + 1)
    for (index in objects.indices) {
        offsets[index + 1] = builder.toString().encodeToByteArray().size
        builder.append(objects[index])
    }
    val xrefStart = builder.toString().encodeToByteArray().size
    builder.append("xref\n0 ${objects.size + 1}\n")
    builder.append("0000000000 65535 f \n")
    for (index in 1..objects.size) {
        builder.append(offsets[index].toString().padStart(10, '0'))
        builder.append(" 00000 n \n")
    }
    builder.append("trailer<< /Size ${objects.size + 1} /Root 1 0 R >>\n")
    builder.append("startxref\n$xrefStart\n%%EOF\n")
    return builder.toString().encodeToByteArray()
}

fun ByteArray.isPdfHeader(): Boolean =
    size >= 5 && decodeToString(endIndex = 5).startsWith("%PDF")

fun ByteArray.containsLatin1(text: String): Boolean =
    decodeToString(throwOnInvalidSequence = false).contains(text)
