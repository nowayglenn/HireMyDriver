package com.example.hiremydriver

import java.util.Locale
import kotlin.math.abs

/** Transcription only: no ambiguous-character substitutions or authenticity checks. */
internal object DocumentNumberExtractor {
    enum class Kind { LICENSE, POLICE_CLEARANCE }
    data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val height get() = (bottom - top).coerceAtLeast(1)
        val width get() = (right - left).coerceAtLeast(1)
        val centerY get() = (top + bottom) / 2f
    }
    data class Line(val text: String, val box: Box? = null)
    data class Result(val number: String?, val ambiguous: Boolean = false)

    private val licenseLabel = Regex("""\blicen[cs]e\s*(?:number|no\.?|#)\s*[:.]?\s*""", RegexOption.IGNORE_CASE)
    private val policeLabel = Regex("""\b(?:(?:police\s+)?clearance|NPCS?|PCC|PC|transaction)\s*(?:number|no\.?|id|#)\s*[:.]?\s*""", RegexOption.IGNORE_CASE)
    private val unrelatedLabel = Regex("""\b(?:reference|receipt|contact|phone|mobile|transaction)\s*(?:number|no\.?|#)\s*[:.]?\s*""", RegexOption.IGNORE_CASE)
    // Narrow fallback for Philippine license cards when OCR misses the label or reorders columns.
    private val licenseNumber = Regex("""(?<![A-Z0-9/-])([A-Z]\d{2})[ -]*(\d{2})[ -]*(\d{6})(?![A-Z0-9/-])""")

    fun extract(text: String, kind: Kind): String? = analyze(text.lines().map { Line(it) }, kind).number
    fun isPoliceLabel(text: String) = policeLabel.matches(normalize(text))

    fun analyze(input: List<Line>, kind: Kind): Result {
        val lines = input.map { it.copy(text = normalize(it.text)) }.filter { it.text.isNotBlank() }
        val label = if (kind == Kind.LICENSE) licenseLabel else policeLabel
        val candidates = mutableSetOf<String>()
        lines.forEachIndexed { index, line ->
            label.findAll(line.text).forEach { match ->
                val remainder = line.text.substring(match.range.last + 1).trim()
                candidates.addAll(numbersAtStart(remainder, kind))
                if (remainder.isEmpty()) {
                    if (line.box == null) {
                        lines.getOrNull(index + 1)?.let { candidates.addAll(numbersAtStart(it.text, kind)) }
                    } else {
                        // Associate nearby values with labels using position, not OCR block order.
                        lines.filter { it !== line && it.box != null && nearby(line.box, it.box) }
                            .forEach { candidates.addAll(numbersAtStart(it.text, kind)) }
                    }
                }
            }
        }
        if (kind == Kind.LICENSE) {
            lines.forEachIndexed { index, line ->
                val previous = lines.getOrNull(index - 1)?.text.orEmpty()
                val otherLabel = unrelatedLabel.containsMatchIn(line.text) || policeLabel.containsMatchIn(line.text)
                val followsOtherLabel = line.box == null &&
                    (unrelatedLabel.matches(previous) || policeLabel.matches(previous))
                if (!otherLabel && !followsOtherLabel) candidates.addAll(licenseNumbers(line.text))
            }
        }
        return Result(candidates.singleOrNull(), candidates.size > 1)
    }

    private fun nearby(label: Box, value: Box): Boolean {
        val sameRow = abs(label.centerY - value.centerY) <= maxOf(label.height, value.height) * .6f &&
            value.left >= label.right && value.left - label.right <= label.width * 3
        val below = value.top - label.bottom in -(label.height / 4)..(label.height * 3) &&
            abs(value.left - label.left) <= maxOf(label.width * .6f, label.height * 2f)
        return sameRow || below
    }

    private fun licenseNumbers(text: String) = licenseNumber.findAll(text).map {
        "${it.groupValues[1]}-${it.groupValues[2]}-${it.groupValues[3]}"
    }.toSet()

    private fun numbersAtStart(text: String, kind: Kind): Set<String> {
        if (kind == Kind.LICENSE && licenseNumber.find(text)?.range?.first == 0) return licenseNumbers(text)
        // OCR sometimes joins the number and the next field into one line.
        val token = Regex("""^([A-Z0-9]+(?:\s*-\s*[A-Z0-9]+)*)(?=$|[\s,;])""")
            .find(text)?.groupValues?.get(1) ?: return emptySet()
        return candidate(token)?.let { setOf(it) } ?: emptySet()
    }

    private fun normalize(text: String) = text.uppercase(Locale.ROOT)
        .replace(Regex("[‐‑‒–—−]"), "-").replace('\u00A0', ' ').trim()

    private fun candidate(value: String): String? {
        val normalized = value.replace(Regex("""\s*-\s*"""), "-")
        if (normalized.length !in 6..30 || normalized.count(Char::isDigit) < 5) return null
        // Reject date-shaped values even when OCR places one below a number label.
        if (Regex("(?:19|20)\\d{2}-\\d{1,2}-\\d{1,2}|\\d{1,2}-\\d{1,2}-(?:19|20)\\d{2}").matches(normalized)) return null
        return normalized
    }
}

/** Reject stale images and edits made while recognition was running. */
internal class DocumentScanGuard {
    data class Ticket(val document: String, val field: String, val generation: Int, val revision: Int)
    private val generations = mutableMapOf<String, Int>()
    private val revisions = mutableMapOf<String, Int>()

    fun start(document: String, field: String): Ticket {
        val generation = (generations[document] ?: 0) + 1
        generations[document] = generation
        return Ticket(document, field, generation, revisions[field] ?: 0)
    }

    fun edited(field: String) { revisions[field] = (revisions[field] ?: 0) + 1 }
    fun forField(ticket: Ticket, field: String) = ticket.copy(field = field, revision = revisions[field] ?: 0)
    fun isCurrent(ticket: Ticket) = generations[ticket.document] == ticket.generation
    fun canFill(ticket: Ticket, currentValue: String) = isCurrent(ticket) &&
        currentValue.isBlank() && ticket.revision == (revisions[ticket.field] ?: 0)
    fun invalidate() { generations.keys.toList().forEach { generations[it] = generations.getValue(it) + 1 } }
}
