package com.example.hiremydriver

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

class LicenseCropAnimationView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var image: Bitmap? = null
    private var from = RectF()
    private var progress = 0f
    private var animator: ValueAnimator? = null
    internal fun play(bitmap: Bitmap, frame: LicenseAlignment.Frame, complete: () -> Unit) {
        clear()
        image = bitmap
        from = RectF(frame.left, frame.top, frame.right, frame.bottom)
        visibility = VISIBLE
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 650
            interpolator = DecelerateInterpolator()
            addUpdateListener { progress = it.animatedValue as Float; invalidate() }
            addListener(object : AnimatorListenerAdapter() {
                private var canceled = false
                override fun onAnimationCancel(animation: Animator) { canceled = true }
                override fun onAnimationEnd(animation: Animator) {
                    if (!canceled) complete()
                }
            })
            start()
        }
    }
    fun clear() { animator?.cancel(); animator = null; image?.recycle(); image = null; visibility = GONE }
    override fun onDetachedFromWindow() { clear(); super.onDetachedFromWindow() }
    override fun onDraw(canvas: Canvas) {
        val bitmap = image ?: return
        canvas.drawARGB((progress * 255).toInt(), 14, 33, 58)
        val scale = minOf(width * .94f / bitmap.width, height * .9f / bitmap.height)
        val w = bitmap.width * scale; val h = bitmap.height * scale
        val target = RectF((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
        val start = RectF(from.left * width, from.top * height, from.right * width, from.bottom * height)
        fun mix(a: Float, b: Float) = a + (b - a) * progress
        val rect = RectF(mix(start.left, target.left), mix(start.top, target.top), mix(start.right, target.right), mix(start.bottom, target.bottom))
        val radius = 20 * resources.displayMetrics.density
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) })
        canvas.drawBitmap(bitmap, null, rect, paint)
        canvas.restore()
    }
}
