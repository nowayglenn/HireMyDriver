package com.example.hiremydriver

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Measures the real card, but paints only placeholder shapes, never sample data. */
class CardSkeleton(private val card: View) : FrameLayout(card.context) {
    private val shapes = object : LoadingSkeleton(card.context, 0) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(230, 234, 240) }
        override fun onDraw(canvas: Canvas) {
            fun draw(view: View, x: Float, y: Float) {
                if (view.visibility == GONE) return
                if (view is TextView || view is ImageView) {
                    val rect = android.graphics.RectF(x, y, x + view.width, y + view.height)
                    val radius = if (view is ImageView) minOf(view.width, view.height) / 2f else 6 * resources.displayMetrics.density
                    canvas.drawRoundRect(rect, radius, radius, paint)
                } else if (view is ViewGroup) {
                    for (i in 0 until view.childCount) {
                        val child = view.getChildAt(i)
                        draw(child, x + child.left, y + child.top)
                    }
                }
            }
            draw(card, card.left.toFloat(), card.top.toFloat())
        }
    }
    init {
        tag = "card_skeleton"
        card.visibility = INVISIBLE
        card.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        addView(card, LayoutParams(-1, -2))
        addView(shapes, LayoutParams(-1, -1))
        contentDescription = "Loading"
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    fun loading(value: Boolean) {
        card.visibility = if (value) INVISIBLE else VISIBLE
        card.importantForAccessibility = if (value) IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS else IMPORTANT_FOR_ACCESSIBILITY_AUTO
        shapes.visibility = if (value) VISIBLE else GONE
        importantForAccessibility = if (value) IMPORTANT_FOR_ACCESSIBILITY_YES else IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    companion object {
        fun cover(card: View): CardSkeleton {
            val parent = card.parent as ViewGroup
            val index = parent.indexOfChild(card)
            val params = card.layoutParams
            parent.removeView(card)
            return CardSkeleton(card).also { parent.addView(it, index, params) }
        }
        fun show(parent: LinearLayout, layout: Int, count: Int = 2) {
            if ((0 until parent.childCount).any { parent.getChildAt(it) is CardSkeleton }) return
            parent.removeAllViews()
            repeat(count) {
                val card = android.view.LayoutInflater.from(parent.context).inflate(layout, parent, false)
                val params = card.layoutParams
                parent.addView(CardSkeleton(card), params)
            }
        }
        fun clear(parent: ViewGroup) {
            for (i in parent.childCount - 1 downTo 0) if (parent.getChildAt(i) is CardSkeleton) parent.removeViewAt(i)
        }
    }
}
