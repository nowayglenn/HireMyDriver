package com.example.hiremydriver.trip

import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.example.hiremydriver.R

object BookingRouteView {
    fun render(container: LinearLayout, entries: List<BookingRouteEntry>, review: Boolean = false) {
        container.removeAllViews()
        val density = container.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        entries.forEachIndexed { index, entry ->
            val row = LayoutInflater.from(container.context).inflate(R.layout.item_booking_route_point, container, false)
            val name = row.findViewById<TextView>(R.id.routePointName)
            name.text = entry.place
            if (review) name.textSize = 14f
            row.findViewById<TextView>(R.id.routePointCaption).text = entry.caption
            val connector = row.findViewById<View>(R.id.routeConnector)
            val last = index == entries.lastIndex
            connector.layoutParams = (connector.layoutParams as FrameLayout.LayoutParams).apply {
                topMargin = if (index == 0) dp(10) else 0
                height = if (last) dp(10) else FrameLayout.LayoutParams.MATCH_PARENT
            }
            if (entries.size == 1) connector.visibility = View.GONE
            // Align to the first line, even when a long name wraps or system text is enlarged.
            name.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                val textLayout = name.layout ?: return@addOnLayoutChangeListener
                val center = name.totalPaddingTop + (textLayout.getLineTop(0) + textLayout.getLineBottom(0)) / 2
                val dot = row.findViewById<View>(R.id.routeDot)
                val dotParams = dot.layoutParams as FrameLayout.LayoutParams
                if (dotParams.topMargin != center - dp(5)) {
                    dot.layoutParams = dotParams.apply { topMargin = center - dp(5) }
                }
                val lineParams = connector.layoutParams as FrameLayout.LayoutParams
                val top = if (index == 0) center else 0
                val height = if (last) center else FrameLayout.LayoutParams.MATCH_PARENT
                if (lineParams.topMargin != top || lineParams.height != height) {
                    connector.layoutParams = lineParams.apply { topMargin = top; this.height = height }
                }
            }
            if (review && index == 0) row.findViewById<View>(R.id.routeDot).setBackgroundResource(R.drawable.bg_blue_dot)
            if (last) row.findViewById<View>(R.id.routePointText).setPadding(0, 0, 0, 0)
            container.addView(row)
        }
    }
}
