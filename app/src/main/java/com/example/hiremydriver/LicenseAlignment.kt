package com.example.hiremydriver

import kotlin.math.abs
import kotlin.math.roundToInt

/** Fast edge-based framing hint, not document classification or authenticity detection. */
internal object LicenseAlignment {
    data class Frame(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width get() = right - left
        val height get() = bottom - top
    }
    fun frame(width: Int, height: Int, paper: Boolean = false): Frame {
        val aspect = if (paper) .707f else 1.586f
        val w = minOf(width * .88f, height * .8f * aspect)
        val h = w / aspect
        return Frame((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
    }

    fun matches(pixels: IntArray, width: Int, height: Int) = detect(pixels, width, height) != null

    fun detect(pixels: IntArray, width: Int, height: Int, paper: Boolean = false): Frame? {
        if (width < 60 || height < 60 || pixels.size != width * height) return null
        val gray = IntArray(pixels.size) { i ->
            val p = pixels[i]; ((p shr 16 and 255) * 77 + (p shr 8 and 255) * 150 + (p and 255) * 29) shr 8
        }
        detectUpright(gray, width, height, paper)?.let { return it }
        // A small hand tilt should not require exact alignment with the screen guide.
        // Map all four corners back so the saved bounding crop never cuts off a tilted ID.
        val cx = (width - 1) / 2f; val cy = (height - 1) / 2f
        for (degrees in listOf(-4, 4, -8, 8)) {
            val radians = Math.toRadians(degrees.toDouble())
            val c = kotlin.math.cos(radians).toFloat(); val s = kotlin.math.sin(radians).toFloat()
            val rotated = IntArray(gray.size) { i ->
                val x = i % width - cx; val y = i / width - cy
                val sx = (cx + x * c - y * s).roundToInt()
                val sy = (cy + x * s + y * c).roundToInt()
                if (sx in 0 until width && sy in 0 until height) gray[sy * width + sx] else 0
            }
            val found = detectUpright(rotated, width, height, paper) ?: continue
            val corners = listOf(found.left to found.top, found.right to found.top,
                found.right to found.bottom, found.left to found.bottom).map { (nx, ny) ->
                val x = nx * width - cx; val y = ny * height - cy
                (cx + x * c - y * s) to (cy + x * s + y * c)
            }
            val left = corners.minOf { it.first }; val right = corners.maxOf { it.first }
            val top = corners.minOf { it.second }; val bottom = corners.maxOf { it.second }
            if (left <= 3 || top <= 3 || right >= width - 4 || bottom >= height - 4) continue
            return Frame(left / width, top / height, right / width, bottom / height)
        }
        return null
    }

    private fun detectUpright(gray: IntArray, width: Int, height: Int, paper: Boolean): Frame? {
        // Search actual card edges throughout the preview, independently of the guide.
        data class Edge(val y: Int, val left: Int, val right: Int) {
            val length get() = right - left
        }
        val minimumWidth = minOf(width.toFloat(), height * if (paper) .707f else 1.586f) * .45f
        val edges = mutableListOf<Edge>()
        for (y in 2 until height - 2) {
            var start = -1; var last = -1; var hits = 0
            fun finish() {
                if (start >= 0 && last - start >= minimumWidth && hits >= (last - start + 1) * .8f)
                    edges.add(Edge(y, start, last))
                start = -1; last = -1; hits = 0
            }
            for (x in 2 until width - 2) {
                if (abs(gray[(y - 2) * width + x] - gray[(y + 2) * width + x]) >= 24) {
                    if (start < 0) start = x
                    last = x; hits++
                } else if (start >= 0 && x - last > 3) finish()
            }
            finish()
        }
        val candidates = mutableListOf<Edge>()
        for (edge in edges.sortedByDescending { it.length }) {
            if (candidates.none { abs(it.y - edge.y) <= 3 &&
                    abs(it.left - edge.left) <= 5 && abs(it.right - edge.right) <= 5 }) {
                candidates.add(edge)
                if (candidates.size == 64) break
            }
        }
        fun vertical(expected: Int, top: Int, bottom: Int): Int? {
            val a = top + ((bottom - top) * .12f).roundToInt()
            val b = bottom - ((bottom - top) * .12f).roundToInt()
            var best: Int? = null; var bestHits = 0
            for (x in (expected - 3).coerceAtLeast(2)..(expected + 3).coerceAtMost(width - 3)) {
                var hits = 0
                val sections = IntArray(4)
                for (y in a..b) {
                    if (abs(gray[y * width + x - 2] - gray[y * width + x + 2]) >= 24) {
                        hits++
                        sections[((y - a) * 4 / (b - a + 1)).coerceAtMost(3)]++
                    }
                }
                if (hits >= (b - a + 1) * .65f && sections.all { it >= (b - a + 1) * .12f } &&
                    (hits > bestHits || (hits == bestHits && best != null && abs(x - expected) < abs(best - expected)))) {
                    best = x; bestHits = hits
                }
            }
            return best
        }
        var best: Frame? = null; var bestArea = 0f
        for (top in candidates) for (bottom in candidates) {
            val h = bottom.y - top.y
            if (h < minimumWidth / 1.9f) continue
            val tolerance = maxOf(5f, top.length * .04f)
            if (abs(top.left - bottom.left) > tolerance || abs(top.right - bottom.right) > tolerance) continue
            val w = (top.length + bottom.length) / 2f
            val aspect = w / h
            val fits = if (paper) aspect in .5f.. .9f || aspect in 1.1f..2f else aspect in 1.35f..1.85f
            if (!fits || w * h <= bestArea) continue
            val left = vertical((top.left + bottom.left) / 2, top.y, bottom.y) ?: continue
            val right = vertical((top.right + bottom.right) / 2, top.y, bottom.y) ?: continue
            // Partial cards cannot be safely cropped.
            if (left <= 3 || right >= width - 4 || top.y <= 3 || bottom.y >= height - 4) continue
            // The edge filter spans two pixels; include its uncertainty on every side.
            best = Frame((left - 2f) / width, (top.y - 2f) / height,
                (right + 2f) / width, (bottom.y + 2f) / height)
            bestArea = w * h
        }
        return best
    }
}

/** A missing/moving/stale frame immediately locks the shutter; auto capture fires once. */
internal class LicenseCaptureGate {
    private var count = 0
    private var greenSince = 0L
    private var lastAt = -10000L
    private var consumed = false
    var frame: LicenseAlignment.Frame? = null
        private set
    var aligned = false
        private set
    fun update(value: LicenseAlignment.Frame?, now: Long) {
        val previous = frame
        val moving = previous != null && value != null && maxOf(abs(value.left - previous.left),
            abs(value.top - previous.top), abs(value.right - previous.right), abs(value.bottom - previous.bottom)) > .025f
        if (value == null || moving || now - lastAt > 600) { count = 0; aligned = false; greenSince = now }
        frame = value; lastAt = now
        if (value != null && !consumed) {
            count++
            if (count >= 2 && !aligned) { aligned = true; greenSince = now }
        }
    }
    fun canCapture(now: Long) = !consumed && aligned && frame != null && now - lastAt in 0..700
    fun autoReady(now: Long) = canCapture(now) && now - greenSince >= 450
    fun consume(now: Long): LicenseAlignment.Frame? {
        if (!canCapture(now)) return null
        consumed = true
        return frame
    }
    fun reset() { count = 0; greenSince = 0; lastAt = -10000; consumed = false; frame = null; aligned = false }
}

internal class LicenseAlignmentStability {
    private var good = 0
    private var bad = 0
    var aligned = false
        private set
    fun update(matches: Boolean): Boolean {
        if (matches) { good++; bad = 0 } else { bad++; good = 0 }
        if (good >= 3) aligned = true
        if (bad >= 2) aligned = false
        return aligned
    }
    fun reset() { good = 0; bad = 0; aligned = false }
}
