package com.example.hiremydriver

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

/** Zoom and pan affect only the display; the submitted photo is never changed. */
class DocumentZoomView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    AppCompatImageView(context, attrs) {
    private var zoom = 1f
    private var offsetX = 0f
    private var offsetY = 0f
    private val scale = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val next = (zoom * detector.scaleFactor).coerceIn(1f, 5f)
            val factor = next / zoom
            offsetX = factor * offsetX + (1 - factor) * (detector.focusX - width / 2f)
            offsetY = factor * offsetY + (1 - factor) * (detector.focusY - height / 2f)
            zoom = next
            constrainPan()
            invalidate()
            return true
        }
    })
    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(event: MotionEvent) = true
        override fun onSingleTapUp(event: MotionEvent): Boolean = performClick()
        override fun onDoubleTap(event: MotionEvent): Boolean {
            resetZoom()
            return true
        }
        override fun onScroll(first: MotionEvent?, current: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (!scale.isInProgress && zoom > 1f) {
                offsetX -= distanceX
                offsetY -= distanceY
                constrainPan()
                invalidate()
            }
            return true
        }
    })

    private fun constrainPan() {
        val image = drawable ?: return
        if (image.intrinsicWidth <= 0 || image.intrinsicHeight <= 0) return
        val fit = minOf(width.toFloat() / image.intrinsicWidth, height.toFloat() / image.intrinsicHeight)
        val limitX = ((image.intrinsicWidth * fit * zoom - width) / 2).coerceAtLeast(0f)
        val limitY = ((image.intrinsicHeight * fit * zoom - height) / 2).coerceAtLeast(0f)
        offsetX = offsetX.coerceIn(-limitX, limitX)
        offsetY = offsetY.coerceIn(-limitY, limitY)
    }

    private fun resetZoom() { zoom = 1f; offsetX = 0f; offsetY = 0f; invalidate() }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        resetZoom()
    }

    override fun onDraw(canvas: Canvas) {
        val saved = canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(zoom, zoom, width / 2f, height / 2f)
        super.onDraw(canvas)
        canvas.restoreToCount(saved)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (drawable == null) return super.onTouchEvent(event)
        scale.onTouchEvent(event)
        gestures.onTouchEvent(event)
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}
