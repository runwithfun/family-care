package com.runwithfun.familycare.growing

import com.runwithfun.familycare.domain.ReviewedActivity

object ActivityPdf {
    fun fileName(activity: ReviewedActivity): String =
        "family-care-${activity.id}.pdf"

    fun render(activity: ReviewedActivity, childName: String): ByteArray {
        val lines = buildList {
            add("Family Care")
            add(activity.title)
            add("For $childName · ${activity.stage.label}")
            add("")
            add(activity.summary)
            add("")
            add("Steps")
            activity.steps.forEachIndexed { index, step ->
                add("${index + 1}. $step")
            }
            add("")
            add("Reviewed demo activity for local printing only.")
        }
        return buildTextPdf(lines)
    }

    internal fun buildTextPdf(lines: List<String>): ByteArray {
        val content = buildString {
            append("BT\n/F1 12 Tf\n14 TL\n72 720 Td\n")
            lines.forEachIndexed { index, line ->
                if (index > 0) append("T*\n")
                append("(")
                append(escapePdfString(line))
                append(") Tj\n")
            }
            append("ET\n")
        }
        val contentBytes = content.encodeToByteArray()

        val objects = mutableListOf<String>()
        objects += "<< /Type /Catalog /Pages 2 0 R >>"
        objects += "<< /Type /Pages /Kids [3 0 R] /Count 1 >>"
        objects += "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] " +
            "/Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>"
        objects += "<< /Length ${contentBytes.size} >>\nstream\n$content\nendstream"
        objects += "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"

        val body = StringBuilder("%PDF-1.4\n")
        val offsets = IntArray(objects.size + 1)
        for (index in objects.indices) {
            offsets[index + 1] = body.length
            body.append(index + 1).append(" 0 obj\n")
            body.append(objects[index])
            body.append("\nendobj\n")
        }
        val xrefPosition = body.length
        body.append("xref\n0 ").append(objects.size + 1).append('\n')
        body.append("0000000000 65535 f \n")
        for (index in 1..objects.size) {
            body.append(offsets[index].toString().padStart(10, '0')).append(" 00000 n \n")
        }
        body.append("trailer << /Size ").append(objects.size + 1)
            .append(" /Root 1 0 R >>\n")
        body.append("startxref\n").append(xrefPosition).append("\n%%EOF\n")
        return body.toString().encodeToByteArray()
    }

    private fun escapePdfString(value: String): String = buildString(value.length) {
        for (char in value) {
            when (char) {
                '\\', '(', ')' -> {
                    append('\\')
                    append(char)
                }
                '\r', '\n', '\t' -> append(' ')
                in ' '..'~' -> append(char)
                else -> append('?')
            }
        }
    }
}
