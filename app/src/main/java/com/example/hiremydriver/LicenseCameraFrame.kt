package com.example.hiremydriver

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.widget.FrameLayout

/** Masks the live TextureView as well as the still-photo review on older devices. */
class LicenseCameraFrame(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs) {
    private val corners = Path()
    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xff07162b.toInt() }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val radius = minOf(36 * resources.displayMetrics.density, w / 2f, h / 2f)
        corners.reset()
        corners.fillType = Path.FillType.EVEN_ODD
        corners.addRect(0f, 0f, w.toFloat(), h.toFloat(), Path.Direction.CW)
        corners.addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radius, radius, Path.Direction.CW)
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        // A visible mask avoids relying only on ancestor outline clipping of a camera texture.
        canvas.drawPath(corners, cornerPaint)
    }
}
