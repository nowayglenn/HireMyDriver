package com.example.hiremydriver

/** A stable placeholder while the first data snapshot loads; existing content is never covered. */
open class LoadingSkeleton(context: android.content.Context, heightDp: Int = 120) : android.view.View(context) {
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(230, 234, 240) }
    private val density = resources.displayMetrics.density
    private var pulse: android.animation.ObjectAnimator? = null
    init { minimumHeight = (heightDp * density).toInt(); contentDescription = "Loading"; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) { setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize(minimumHeight, heightMeasureSpec)) }
    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        val left = 18 * density; val top = 24 * density
        canvas.drawCircle(left + 28 * density, top + 28 * density, 28 * density, paint)
        val start = left + 72 * density; val end = (width - left).coerceAtLeast(start)
        listOf(0f to 0.85f, 25f to 0.65f, 47f to 0.45f).forEach { (y, length) ->
            canvas.drawRoundRect(start, top + y * density, start + (end - start) * length, top + (y + 12) * density, 6 * density, 6 * density, paint)
        }
    }
    private fun updateAnimation() {
        pulse?.cancel(); pulse = null
        if (isAttachedToWindow && isShown) pulse = android.animation.ObjectAnimator.ofFloat(this, "alpha", 0.55f, 1f).apply {
            duration = 900; repeatMode = android.animation.ValueAnimator.REVERSE
            repeatCount = android.animation.ValueAnimator.INFINITE; start()
        }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); updateAnimation() }
    override fun onVisibilityChanged(changedView: android.view.View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility); updateAnimation()
    }
    override fun onDetachedFromWindow() { pulse?.cancel(); pulse = null; super.onDetachedFromWindow() }
}
