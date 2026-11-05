package com.example.hiremydriver

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

/** A circular preview of the square image saved for the existing profile avatar. */
class CustomerPhotoCropView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private var photo: Bitmap? = null
    private val frame = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var centerX = .5f
    private var centerY = .5f
    private var zoom = 1f
    private var lastX = 0f
    private var lastY = 0f
    var onZoomChanged: (Float) -> Unit = {}
    private val gestures = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            changeZoom(zoom * detector.scaleFactor, detector.focusX, detector.focusY)
            return true
        }
    })

    fun setPhoto(bitmap: Bitmap, state: FloatArray? = null) {
        photo = bitmap
        centerX = state?.getOrNull(0) ?: .5f
        centerY = state?.getOrNull(1) ?: .5f
        zoom = (state?.getOrNull(2) ?: 1f).coerceIn(1f, 5f)
        constrain()
        onZoomChanged(zoom)
        invalidate()
    }

    fun cropState() = floatArrayOf(centerX, centerY, zoom)
    fun setZoom(value: Float) = changeZoom(value, frame.centerX(), frame.centerY())

    private fun scale(): Float {
        val bitmap = photo ?: return 1f
        return maxOf(frame.width() / bitmap.width, frame.height() / bitmap.height) * zoom
    }

    private fun constrain() {
        val bitmap = photo ?: return
        if (frame.width() <= 0f) return
        val halfX = (frame.width() / (2f * scale() * bitmap.width)).coerceAtMost(.5f)
        val halfY = (frame.height() / (2f * scale() * bitmap.height)).coerceAtMost(.5f)
        centerX = centerX.coerceIn(halfX, 1f - halfX)
        centerY = centerY.coerceIn(halfY, 1f - halfY)
    }

    private fun changeZoom(value: Float, focusX: Float, focusY: Float) {
        val bitmap = photo ?: return
        if (frame.width() <= 0) return
        val oldScale = scale()
        zoom = value.coerceIn(1f, 5f)
        // Keep the image point between the user's fingers stationary while zooming.
        centerX += (focusX - frame.centerX()) * (1f / oldScale - 1f / scale()) / bitmap.width
        centerY += (focusY - frame.centerY()) * (1f / oldScale - 1f / scale()) / bitmap.height
        constrain()
        onZoomChanged(zoom)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val side = (minOf(w, h) - 24f * resources.displayMetrics.density).coerceAtLeast(0f)
        frame.set((w - side) / 2f, (h - side) / 2f, (w + side) / 2f, (h + side) / 2f)
        constrain()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bitmap = photo ?: return
        if (frame.width() <= 0) return
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        canvas.save()
        canvas.translate(frame.centerX() - centerX * bitmap.width * scale(), frame.centerY() - centerY * bitmap.height * scale())
        canvas.scale(scale(), scale())
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        canvas.restore()
        val shade = Path().apply {
            fillType = Path.FillType.EVEN_ODD
            addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
            addCircle(frame.centerX(), frame.centerY(), frame.width() / 2f, Path.Direction.CW)
        }
        paint.color = 0xB3061B41.toInt()
        canvas.drawPath(shade, paint)
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * resources.displayMetrics.density
        canvas.drawCircle(frame.centerX(), frame.centerY(), frame.width() / 2f, paint)
        paint.style = Paint.Style.FILL
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || photo == null || frame.width() <= 0) return false
        gestures.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> parent?.requestDisallowInterceptTouchEvent(true)
            MotionEvent.ACTION_MOVE -> if (!gestures.isInProgress && event.pointerCount == 1) {
                val bitmap = photo ?: return false
                centerX -= (event.x - lastX) / (scale() * bitmap.width)
                centerY -= (event.y - lastY) / (scale() * bitmap.height)
                constrain()
                invalidate()
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val remaining = if (event.actionIndex == 0) 1 else 0
                lastX = event.getX(remaining)
                lastY = event.getY(remaining)
                return true
            }
            MotionEvent.ACTION_UP -> { performClick(); parent?.requestDisallowInterceptTouchEvent(false) }
            MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        lastX = event.x
        lastY = event.y
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    fun croppedBitmap(): Bitmap? {
        val bitmap = photo ?: return null
        if (frame.width() <= 0f) return null
        constrain()
        val output = Bitmap.createBitmap(768, 768, Bitmap.Config.ARGB_8888)
        val outputScale = scale() * 768f / frame.width()
        Canvas(output).apply {
            translate(384f - centerX * bitmap.width * outputScale, 384f - centerY * bitmap.height * outputScale)
            scale(outputScale, outputScale)
            drawBitmap(bitmap, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
        return output
    }
}
