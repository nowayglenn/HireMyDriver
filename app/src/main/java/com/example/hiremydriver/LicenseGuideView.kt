package com.example.hiremydriver

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator

/** Visual framing hint; the photo crop always uses the independently detected card bounds. */
class LicenseGuideView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var aligned = false
    private var scanning = false
    private var progress = 0f
    private var animator: ValueAnimator? = null
    private var detectedFrame: LicenseAlignment.Frame? = null
    private var statusBottom = 84 * resources.displayMetrics.density
    internal var paper = false
        set(value) { field = value; invalidate() }

    internal fun reserveStatusSpace(bottom: Float) {
        if (statusBottom == bottom) return
        statusBottom = bottom
        invalidate()
    }

    internal fun update(aligned: Boolean, scanning: Boolean, detected: LicenseAlignment.Frame? = null) {
        this.aligned = aligned
        this.scanning = scanning
        detectedFrame = detected
        if (aligned && scanning && isAttachedToWindow && windowVisibility == VISIBLE && visibility == VISIBLE) {
            if (animator == null) animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1600
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = LinearInterpolator()
                addUpdateListener { progress = it.animatedValue as Float; invalidate() }
                start()
            }
        } else { animator?.cancel(); animator = null }
        invalidate()
    }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        update(aligned, scanning, detectedFrame)
    }
    override fun onDetachedFromWindow() {
        animator?.cancel(); animator = null
        super.onDetachedFromWindow()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val availableHeight = (height - statusBottom).toInt()
        if (availableHeight <= 0) return
        val fallback = LicenseAlignment.frame(width, availableHeight, paper).let {
            val lift = minOf(24 * resources.displayMetrics.density, it.top)
            LicenseAlignment.Frame(it.left, it.top + statusBottom - lift, it.right, it.bottom + statusBottom - lift)
        }
        val frame = detectedFrame?.let { LicenseAlignment.Frame(it.left * width, it.top * height,
            it.right * width, it.bottom * height) }
            // Keep the visual guide below the fixed label. This does not restrict detection
            // or change the real crop when a card extends into the label's reserved space.
            ?.takeIf { it.top >= statusBottom } ?: fallback
        val rect = RectF(frame.left, frame.top, frame.right, frame.bottom)
        val radius = 22 * resources.displayMetrics.density
        val mask = Path().apply {
            fillType = Path.FillType.EVEN_ODD
            addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
            addRoundRect(rect, radius, radius, Path.Direction.CW)
        }
        paint.style = Paint.Style.FILL
        paint.color = 0x99000000.toInt()
        canvas.drawPath(mask, paint)
        paint.style = Paint.Style.STROKE
        val color = if (aligned) Color.rgb(53, 214, 143) else Color.rgb(255, 99, 113)
        paint.color = color
        paint.strokeWidth = 3 * resources.displayMetrics.density
        canvas.drawRoundRect(rect, radius, radius, paint)
        if (aligned && scanning) {
            canvas.save()
            canvas.clipPath(Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) })
            val y = rect.top + rect.height() * progress
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(35, 53, 214, 143)
            canvas.drawRect(rect.left, y - 28 * resources.displayMetrics.density, rect.right, y, paint)
            paint.color = color
            paint.strokeWidth = 2 * resources.displayMetrics.density
            canvas.drawLine(rect.left, y, rect.right, y, paint)
            canvas.restore()
        }
    }
}
