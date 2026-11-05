package com.example.hiremydriver

import android.graphics.Color
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged

/** Inline errors for the existing non-Material license fields, retaining their normal styling. */
class RegistrationFieldError(private val field: TextView, private val box: View = field) {
    private val normalBackground = box.background
    private val message = TextView(field.context).apply {
        setTextColor(Color.rgb(198, 40, 40))
        textSize = 12f
        visibility = View.GONE
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        setPadding(12, 4, 12, 0)
    }

    init {
        val parent = box.parent as LinearLayout
        parent.addView(message, parent.indexOfChild(box) + 1,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        field.doAfterTextChanged { show(null) }
    }

    fun show(error: String?) {
        message.text = error
        message.visibility = if (error == null) View.GONE else View.VISIBLE
        if (error == null) box.background = normalBackground
        else box.setBackgroundResource(R.drawable.bg_registration_error)
    }
}
