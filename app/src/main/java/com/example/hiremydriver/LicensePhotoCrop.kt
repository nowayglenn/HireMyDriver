package com.example.hiremydriver

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ceil

internal object LicenseCropBounds {
    data class Bounds(val left: Int, val top: Int, val width: Int, val height: Int)
    fun pixels(frame: LicenseAlignment.Frame, width: Int, height: Int): Bounds {
        require(width > 0 && height > 0 && frame.left >= 0 && frame.top >= 0 && frame.right <= 1 && frame.bottom <= 1 && frame.width > 0 && frame.height > 0)
        // Tiny edge margin preserves rounded card corners without keeping the surrounding scene.
        val mx = frame.width * .008f; val my = frame.height * .008f
        val left = floor((frame.left - mx) * width).toInt().coerceAtLeast(0)
        val top = floor((frame.top - my) * height).toInt().coerceAtLeast(0)
        val right = ceil((frame.right + mx) * width).toInt().coerceAtMost(width)
        val bottom = ceil((frame.bottom + my) * height).toInt().coerceAtMost(height)
        return Bounds(left, top, right - left, bottom - top)
    }
}

internal object LicensePhotoCrop {
    data class Result(val file: File, val preview: Bitmap, val frame: LicenseAlignment.Frame)
    fun create(context: Context, raw: File, destination: File, previewAspect: Float, paper: Boolean = false): Result? {
        val source = DocumentPhotoDecoder.decode(context, Uri.fromFile(raw), 3200) ?: return null
        var small: Bitmap? = null
        var cropped: Bitmap? = null
        try {
            // CameraX viewport and EXIF rotation must produce the same displayed aspect.
            if (abs(source.width.toFloat() / source.height - previewAspect) > .035f) return null
            val scale = 360f / maxOf(source.width, source.height)
            small = Bitmap.createScaledBitmap(source, (source.width * scale).toInt().coerceAtLeast(1),
                (source.height * scale).toInt().coerceAtLeast(1), true)
            val pixels = IntArray(small.width * small.height)
            small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
            // Recheck the actual still, not just an earlier preview: movement must not crop text away.
            val detected = LicenseAlignment.detect(pixels, small.width, small.height, paper) ?: return null
            val bounds = LicenseCropBounds.pixels(detected, source.width, source.height)
            cropped = Bitmap.createBitmap(source, bounds.left, bounds.top, bounds.width, bounds.height)
            destination.outputStream().use { check(cropped.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
            val result = Result(destination, cropped, detected)
            cropped = null // Ownership moves to the animation/review.
            return result
        } finally {
            cropped?.takeIf { it !== source && it !== small }?.recycle()
            small?.takeIf { it !== source }?.recycle()
            source.recycle()
        }
    }
}
