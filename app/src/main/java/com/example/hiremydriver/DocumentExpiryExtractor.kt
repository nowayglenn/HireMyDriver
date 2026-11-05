package com.example.hiremydriver

import java.util.GregorianCalendar
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

/** Read only dates associated with an expiry label; never guess from a birth/issue date. */
internal object DocumentExpiryExtractor {
    data class Date(val year: Int, val month: Int, val day: Int) {
        fun forField() = "$month/$day/$year"
    }
    data class Result(val date: Date?, val ambiguous: Boolean = false)
    private val label = Regex("""\b(?:EXPIRATION|EXPIRY|EXPIRES|EXPIRY\s+DATE|EXP\.)(?:\s+DATE)?\s*[:.]?""", RegexOption.IGNORE_CASE)
    private val otherLabel = Regex("""\b(?:BIRTH|DOB|ISSUE|ISSUED|LICENSE\s+NO|AGENCY|BLOOD)\b""", RegexOption.IGNORE_CASE)
    private val yearFirst = Regex("""(?<![\dA-Z])((?:19|20|21)\d{2})\s*[/.-]\s*(\d{1,2})\s*[/.-]\s*(\d{1,2})(?![\dA-Z])""")
    private val yearLast = Regex("""(?<![\dA-Z])(\d{1,2})\s*[/.-]\s*(\d{1,2})\s*[/.-]\s*((?:19|20|21)\d{2})(?![\dA-Z])""")
    private const val MONTH = "JAN(?:UARY)?|FEB(?:RUARY)?|MAR(?:CH)?|APR(?:IL)?|MAY|JUN(?:E)?|JUL(?:Y)?|AUG(?:UST)?|SEP(?:T(?:EMBER)?)?|OCT(?:OBER)?|NOV(?:EMBER)?|DEC(?:EMBER)?"
    // Letter/digit boundaries remain unambiguous when OCR removes spaces. Keep a
    // separator between a numeric day and year so SEP152026 is never split by guesswork.
    private val namedDayFirst = Regex("""\b(\d{1,2})(?:ST|ND|RD|TH)?[\s/-]*($MONTH)\.?[\s,/-]*((?:19|20|21)\d{2})\b""")
    private val namedMonthFirst = Regex("""\b($MONTH)\.?[\s/-]*(\d{1,2})(?:ST|ND|RD|TH)?[\s,/-]+((?:19|20|21)\d{2})\b""")
    private val months = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
    private enum class NumericOrder { MONTH_FIRST, DAY_FIRST }
    private val formatHint = Regex("""\b(MM\s*[/.-]\s*DD|DD\s*[/.-]\s*MM)\s*[/.-]\s*YYYY\b""")
    private fun numericOrder(text: String): NumericOrder? = formatHint.findAll(text).map {
        if (it.groupValues[1].startsWith("MM")) NumericOrder.MONTH_FIRST else NumericOrder.DAY_FIRST
    }.distinct().toList().singleOrNull()

    fun isLabel(text: String) = label.matches(text.trim())

    // Used by the police OCR adapter to keep each date's own box in multi-column rows.
    fun isDateValue(text: String): Boolean {
        val normalized = text.uppercase(Locale.ROOT).replace(Regex("[‐‑‒–—−]"), "-")
            .trim { it.isWhitespace() || it in ":,;.()" }
        return listOf(yearFirst, yearLast, namedDayFirst, namedMonthFirst).any { it.matches(normalized) }
    }

    fun extract(text: String) = analyze(text.lines().map { DocumentNumberExtractor.Line(it) })

    fun analyze(input: List<DocumentNumberExtractor.Line>, targetLabel: Regex = label,
                excludedLabels: Regex = otherLabel, strictAssociation: Boolean = false,
                allowValueAbove: Boolean = false): Result {
        val lines = input.map { it.copy(text = it.text.uppercase(Locale.ROOT).replace(Regex("[‐‑‒–—−]"), "-")) }
        fun tail(line: String, match: MatchResult): String {
            val remaining = line.substring(match.range.last + 1)
            return excludedLabels.find(remaining)?.let { remaining.substring(0, it.range.first) } ?: remaining
        }
        // Share an explicit format only among occurrences of the same target field (including
        // the adapter's tight label spans). Never assume an order from the device locale.
        val fieldOrder = lines.flatMap { line -> targetLabel.findAll(line.text)
            .mapNotNull { numericOrder(tail(line.text, it)) }.toList() }.distinct().singleOrNull()
        fun isTargetLabel(text: String) = targetLabel.containsMatchIn(text) &&
            formatHint.replace(targetLabel.replaceFirst(text, ""), "")
                .trim(' ', ':', '.', '(', ')').isEmpty()
        fun belongsToTarget(anchor: DocumentNumberExtractor.Box, value: DocumentNumberExtractor.Box): Boolean =
            lines.none { other ->
                val otherBox = other.box
                otherBox != null && excludedLabels.containsMatchIn(other.text) &&
                    !targetLabel.containsMatchIn(other.text) && nearby(otherBox, value, allowValueAbove) &&
                    associationDistance(otherBox, value) <= associationDistance(anchor, value)
            }
        val values = mutableSetOf<Date>()
        var uncertain = false
        lines.forEachIndexed { index, line ->
            targetLabel.findAll(line.text).forEach { match ->
                val tail = tail(line.text, match)
                val order = numericOrder(tail) ?: fieldOrder
                // In spatial mode, parse only a tight date span after establishing its
                // owner below. A merged row can contain an unrelated ambiguous birth date;
                // neither that date nor its ambiguity belongs to this field.
                val direct = if (strictAssociation && line.box != null) emptySet<Date>() to false
                    else dates(tail, order)
                values.addAll(direct.first)
                uncertain = uncertain || direct.second
                if (direct.first.isEmpty() && !direct.second) {
                    val neighbours = if (line.box == null) listOfNotNull(lines.getOrNull(index + 1))
                    // Only use exact label spans for spatial lookup. Whole table headings
                    // have misleading boxes; the camera adapter supplies word-level spans.
                    else if (isTargetLabel(line.text)) lines.filter { other ->
                        other.box != null && other !== line && nearby(line.box, other.box, allowValueAbove)
                    } else emptyList()
                    val candidates = neighbours.filterNot { excludedLabels.containsMatchIn(it.text) }
                        .filter { candidate ->
                            if (!strictAssociation) true
                            else isDateValue(candidate.text) && (line.box == null || candidate.box == null ||
                                belongsToTarget(line.box, candidate.box))
                        }
                    candidates.filterNot { broad ->
                        val outer = broad.box
                        outer != null && candidates.any { precise ->
                            val inner = precise.box
                            inner != null && inner != outer && isDateValue(precise.text) && contains(outer, inner)
                        }
                    }.forEach {
                        val parsed = dates(it.text, order)
                        values.addAll(parsed.first)
                        uncertain = uncertain || parsed.second
                    }
                }
            }
        }
        return Result(if (uncertain) null else values.singleOrNull(), uncertain || values.size > 1)
    }

    private fun nearby(label: DocumentNumberExtractor.Box, value: DocumentNumberExtractor.Box,
                       allowValueAbove: Boolean): Boolean {
        val sameRow = abs(label.centerY - value.centerY) <= maxOf(label.height, value.height) * .6f &&
            value.left >= label.right && value.left - label.right <= label.width * 2
        val below = value.top - label.bottom in -(label.height / 4)..(label.height * 3) &&
            abs(value.left - label.left) <= maxOf(label.width * .4f, label.height * 1.5f)
        // Some clearance forms put the value on a line with its caption underneath.
        // Limit this to nearby, aligned values; ownership still excludes birth/payment dates.
        val above = allowValueAbove && label.top - value.bottom in 0..(label.height * 2) &&
            abs(value.left - label.left) <= maxOf(label.width * .4f, label.height * 1.5f)
        return sameRow || below || above
    }

    private fun associationDistance(label: DocumentNumberExtractor.Box, value: DocumentNumberExtractor.Box): Float {
        val sameRow = abs(label.centerY - value.centerY) <= maxOf(label.height, value.height) * .6f &&
            value.left >= label.right
        return if (sameRow) (value.left - label.right).toFloat() + abs(label.centerY - value.centerY)
            else maxOf(value.top - label.bottom, label.top - value.bottom, 0).toFloat() + abs(value.left - label.left)
    }

    private fun contains(outer: DocumentNumberExtractor.Box, inner: DocumentNumberExtractor.Box) =
        inner.left >= outer.left && inner.right <= outer.right && inner.top >= outer.top && inner.bottom <= outer.bottom

    private fun dates(text: String, order: NumericOrder? = null): Pair<Set<Date>, Boolean> {
        val result = mutableSetOf<Date>()
        var ambiguous = false
        yearFirst.findAll(text).forEach { m -> valid(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())?.let(result::add) }
        yearLast.findAll(text).forEach { m ->
            val a = m.groupValues[1].toInt(); val b = m.groupValues[2].toInt(); val y = m.groupValues[3].toInt()
            val possibilities = when (order) {
                NumericOrder.MONTH_FIRST -> setOfNotNull(valid(y, a, b))
                NumericOrder.DAY_FIRST -> setOfNotNull(valid(y, b, a))
                null -> setOfNotNull(valid(y, a, b), valid(y, b, a))
            }
            if (possibilities.size == 1) result.addAll(possibilities)
            else if (possibilities.size > 1) ambiguous = true
        }
        namedDayFirst.findAll(text).forEach { m -> valid(m.groupValues[3].toInt(), months.indexOf(m.groupValues[2].take(3)) + 1, m.groupValues[1].toInt())?.let(result::add) }
        namedMonthFirst.findAll(text).forEach { m -> valid(m.groupValues[3].toInt(), months.indexOf(m.groupValues[1].take(3)) + 1, m.groupValues[2].toInt())?.let(result::add) }
        return result to ambiguous
    }

    private fun valid(year: Int, month: Int, day: Int): Date? = try {
        GregorianCalendar().apply {
            isLenient = false
            clear()
            set(year, month - 1, day)
            get(Calendar.DAY_OF_MONTH)
        }
        Date(year, month, day)
    } catch (_: IllegalArgumentException) { null }
}
