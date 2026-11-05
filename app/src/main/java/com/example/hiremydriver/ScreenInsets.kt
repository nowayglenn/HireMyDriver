package com.example.hiremydriver

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import androidx.core.graphics.Insets

internal object ScreenInsets {
    fun padding(original: Insets, bars: Insets, keyboardBottom: Int): Insets = Insets.of(
        original.left + bars.left,
        original.top + bars.top,
        original.right + bars.right,
        original.bottom + maxOf(bars.bottom, keyboardBottom)
    )
}

/** Match the original header and screen colors in the newly reserved system-bar areas. */
internal class SystemBarBackground(private val topColor: Int, private val bottomColor: Int) : Drawable() {
    private val paint = Paint()
    var topInset: Int = 0
        set(value) { field = value; invalidateSelf() }

    override fun draw(canvas: Canvas) {
        paint.color = bottomColor
        canvas.drawRect(bounds, paint)
        paint.color = topColor
        canvas.drawRect(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), (bounds.top + topInset).toFloat(), paint)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}
