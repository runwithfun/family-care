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
            add(PdfLine("Family Care · printable play", LineKind.Brand))
            add(PdfLine(activity.title, LineKind.Title))
            add(PdfLine(activity.stage.label, LineKind.Stage))
            add(PdfLine("", LineKind.Spacer))
            add(PdfLine(activity.summary, LineKind.Body))
            add(PdfLine("", LineKind.Spacer))
            add(PdfLine("Steps", LineKind.Section))
            activity.steps.forEachIndexed { index, step ->
                add(PdfLine("${index + 1}. $step", LineKind.Step, index))
            }
            add(PdfLine("", LineKind.Spacer))
            add(PdfLine("Source: ${activity.sourceLabel}", LineKind.Source))
        }
        return buildColorfulPdf(lines, paper)
    }

    private fun buildColorfulPdf(lines: List<PdfLine>, paper: ActivityPaper): ByteArray {
        val margin = 48
        val content = buildString {
            // Soft page wash
            append("0.98 0.96 0.92 rg 0 0 ${paper.width} ${paper.height} re f\n")
            // Top banner
            append("0.35 0.72 0.78 rg 0 ${paper.height - 110} ${paper.width} 110 re f\n")
            append("0.98 0.62 0.45 rg 0 ${paper.height - 118} ${paper.width} 8 re f\n")
            // Decorative circles (kid-friendly blobs)
            append("0.98 0.78 0.35 rg ${circle(margin + 18, paper.height - 42, 16)}")
            append("0.74 0.55 0.88 rg ${circle(paper.width - margin - 28, paper.height - 58, 22)}")
            append("0.55 0.82 0.55 rg ${circle(paper.width - margin - 70, paper.height - 28, 10)}")
            append("0.98 0.55 0.62 rg ${circle(margin + 70, 70, 28)}")
            append("0.55 0.72 0.95 rg ${circle(paper.width - margin - 40, 90, 18)}")
            // Content card
            val cardBottom = 56
            val cardTop = paper.height - 140
            append("1 1 1 rg $margin $cardBottom ${paper.width - margin * 2} ${cardTop - cardBottom} re f\n")
            append("0.90 0.88 0.84 RG 1.5 w $margin $cardBottom ${paper.width - margin * 2} ${cardTop - cardBottom} re S\n")

            var baseline = paper.height - 168
            val textLeft = margin + 22
            val maxTextWidth = paper.width - margin * 2 - 44
            lines.forEach { line ->
                if (line.kind == LineKind.Spacer || line.text.isBlank()) {
                    baseline -= 12
                    return@forEach
                }
                val size = when (line.kind) {
                    LineKind.Brand -> 11
                    LineKind.Title -> 24
                    LineKind.Stage -> 12
                    LineKind.Section -> 16
                    LineKind.Step -> 13
                    LineKind.Source -> 10
                    LineKind.Body -> 13
                    LineKind.Spacer -> 12
                }
                val font = when (line.kind) {
                    LineKind.Title, LineKind.Section, LineKind.Brand -> "F2"
                    else -> "F1"
                }
                val stepIndent = if (line.kind == LineKind.Step) 22 else 0
                if (line.kind == LineKind.Step) {
                    append("${stepFill(line.stepIndex)}${circle(textLeft + 8, baseline + 2, 9)}")
                }
                append(
                    when (line.kind) {
                        LineKind.Brand -> "0.18 0.42 0.48 rg\n"
                        LineKind.Title -> "0.18 0.22 0.28 rg\n"
                        LineKind.Stage -> "0.55 0.35 0.62 rg\n"
                        LineKind.Section -> "0.63 0.25 0.18 rg\n"
                        LineKind.Step -> stepColor(line.stepIndex)
                        LineKind.Source -> "0.45 0.45 0.48 rg\n"
                        else -> "0.22 0.24 0.28 rg\n"
                    },
                )
                for (part in wrap(line.text, (maxTextWidth - stepIndent) * 10 / size)) {
                    check(baseline >= margin + 28) { "The activity does not fit on one ${paper.label} page" }
                    val x = textLeft + stepIndent
                    append("BT /$font $size Tf $x $baseline Td (")
                    append(escapePdfString(part))
                    append(") Tj ET\n")
                    baseline -= size + when (line.kind) {
                        LineKind.Title -> 10
                        LineKind.Brand, LineKind.Stage -> 8
                        LineKind.Section -> 10
                        LineKind.Step -> 8
                        else -> 6
                    }
                }
                if (line.kind == LineKind.Step) baseline -= 4
            }
            append("0 g\n")
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

    private fun circle(cx: Int, cy: Int, r: Int): String {
        // Four cubic Bézier approximations of a circle
        val k = (0.552284749831 * r).toInt().coerceAtLeast(1)
        return buildString {
            append("$cx ${cy + r} m ")
            append("${cx + k} ${cy + r} ${cx + r} ${cy + k} ${cx + r} $cy c ")
            append("${cx + r} ${cy - k} ${cx + k} ${cy - r} $cx ${cy - r} c ")
            append("${cx - k} ${cy - r} ${cx - r} ${cy - k} ${cx - r} $cy c ")
            append("${cx - r} ${cy + k} ${cx - k} ${cy + r} $cx ${cy + r} c f\n")
        }
    }

    private fun stepColor(index: Int): String = when (index % 4) {
        0 -> "0.12 0.45 0.55 rg\n"
        1 -> "0.55 0.28 0.55 rg\n"
        2 -> "0.20 0.50 0.28 rg\n"
        else -> "0.70 0.35 0.18 rg\n"
    }

    private fun stepFill(index: Int): String = when (index % 4) {
        0 -> "0.72 0.90 0.93 rg "
        1 -> "0.90 0.80 0.95 rg "
        2 -> "0.78 0.93 0.78 rg "
        else -> "0.98 0.86 0.76 rg "
    }

    private fun wrap(value: String, maxWidth: Int): List<String> = buildList {
        var line = ""
        for (word in value.trim().split(Regex("\\s+"))) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (line.isEmpty() || textWidth(candidate) <= maxWidth) {
                line = candidate
            } else {
                add(line)
                line = word
            }
        }
        if (line.isNotEmpty()) add(line)
    }

    private fun textWidth(value: String): Int = value.fold(0) { width, char ->
        width + when (char) {
            'M', 'W', 'm', '%', '@' -> 11
            'i', 'l', '.', ',', ' ', '\'' -> 4
            in 'A'..'Z' -> 9
            else -> 7
        }
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
                '·' -> append("\\267")
                in '\u00a0'..'\u00ff' -> append('\\').append(char.code.toString(8).padStart(3, '0'))
                else -> append('?')
            }
        }
    }

    private enum class LineKind { Brand, Title, Stage, Body, Section, Step, Source, Spacer }

    private data class PdfLine(val text: String, val kind: LineKind, val stepIndex: Int = 0)
}
