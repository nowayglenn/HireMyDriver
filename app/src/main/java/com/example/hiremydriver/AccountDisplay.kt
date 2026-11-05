package com.example.hiremydriver

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/** Real account identity and empty states using the existing screen/card layouts. */
object AccountDisplay {
    fun scope(context: Context): String {
        val driver = context is DriverNavigationActivity || context.javaClass.simpleName.startsWith("Driver") ||
            (context as? Activity)?.intent?.getBooleanExtra("DRIVER_REVIEW", false) == true ||
            (context as? Activity)?.intent?.getBooleanExtra("DRIVER_MAP", false) == true
        return if (driver) "driver_${DriverAuth.currentUser(context)?.uid ?: "signed_out"}"
            else "customer_${CustomerAuth.currentUser(context)?.uid ?: "signed_out"}"
    }
    fun empty(parent: LinearLayout, message: String) {
        if (parent.findViewWithTag<View>("real_data_empty") != null) return
        parent.addView(TextView(parent.context).apply {
            tag = "real_data_empty"; text = message; textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#687386"))
            val gap = (16 * resources.displayMetrics.density).toInt()
            setPadding(gap, gap, gap, gap)
        })
    }
    fun emptyScroll(activity: Activity, scrollId: Int, message: String) {
        val scroll = activity.findViewById<ViewGroup>(scrollId)
        val content = scroll.getChildAt(0) as LinearLayout
        // Keep the prepared XML cards intact as templates, but never display sample people.
        for (index in 0 until content.childCount) {
            val child = content.getChildAt(index)
            if (child.tag != "real_data_empty") child.visibility = View.GONE
        }
        empty(content, message)
    }
}
