package com.runwithfun.familycare.growing

import com.runwithfun.familycare.domain.ReviewedActivity

enum class ActivityPaper(val label: String, val width: Int, val height: Int) {
    A4("A4", 595, 842),
    LETTER("US Letter", 612, 792),
}

object ActivityPdf {
    fun fileName(activity: ReviewedActivity): String =
        "family-care-${activity.id}.pdf"

    fun render(activity: ReviewedActivity, paper: ActivityPaper = ActivityPaper.LETTER): ByteArray {
        val lines = buildList {
            add("Family Care")
            add(activity.title)
            add(activity.stage.label)
            add("")
            add(activity.summary)
            add("")
            add("Steps")
            activity.steps.forEachIndexed { index, step ->
                add("${index + 1}. $step")
            }
        }
        return buildTextPdf(lines, paper)
    }

    private fun buildTextPdf(lines: List<String>, paper: ActivityPaper): ByteArray {
        val margin = 56
        val content = buildString {
            var baseline = paper.height - 62
            lines.forEachIndexed { index, line ->
                if (line.isBlank()) {
                    baseline -= 12
                } else {
                    val size = when (index) {
                        0 -> 10
                        1 -> 22
                        else -> 12
                    }
                    val font = if (index <= 1) "F2" else "F1"
                    for (part in wrap(line, (paper.width - margin * 2) / size)) {
                        check(baseline >= margin) { "The activity does not fit on one ${paper.label} page" }
                        append("BT /$font $size Tf $margin $baseline Td (")
                        append(escapePdfString(part))
                        append(") Tj ET\n")
                        baseline -= size + if (index <= 2) 10 else 6
                    }
                }
            }
        }

        val objects = listOf(
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${paper.width} ${paper.height}] " +
                "/Contents 4 0 R /Resources << /Font << /F1 5 0 R /F2 6 0 R >> >> >>",
            "<< /Length ${content.length} >>\nstream\n${content}endstream",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>",
        )

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

    private fun wrap(value: String, length: Int): List<String> = buildList {
        var line = ""
        for (word in value.trim().split(Regex("\\s+")).flatMap { it.chunked(length) }) {
            if (line.isEmpty()) {
                line = word
            } else if (line.length + 1 + word.length <= length) {
                line += " $word"
            } else {
                add(line)
                line = word
            }
        }
        if (line.isNotEmpty()) add(line)
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
                '–' -> append("\\226")
                '—' -> append("\\227")
                '‘' -> append("\\221")
                '’' -> append("\\222")
                '“' -> append("\\223")
                '”' -> append("\\224")
                in '\u00a0'..'\u00ff' -> append('\\').append(char.code.toString(8).padStart(3, '0'))
                else -> append('?')
            }
        }
    }
}
