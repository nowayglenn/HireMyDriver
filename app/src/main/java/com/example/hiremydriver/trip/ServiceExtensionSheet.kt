package com.example.hiremydriver.trip

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.widget.doAfterTextChanged
import com.example.hiremydriver.NetworkStatus
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Request-only sheet. The approved booking duration changes only when the driver accepts. */
internal object ServiceExtensionSheet {
    private val visible = WeakHashMap<Activity, WeakReference<BottomSheetDialog>>()
    private val navy = Color.rgb(6, 27, 65)
    private val orange = Color.rgb(255, 128, 0)
    private val muted = Color.rgb(98, 111, 130)
    private val line = Color.rgb(226, 231, 238)
    private val pale = Color.rgb(255, 247, 237)

    fun show(a: Activity, trip: TripSession, refresh: () -> Unit) {
        if (a.isFinishing || a.isDestroyed || trip.phase != TripPhase.IN_PROGRESS ||
            trip.extensionStatus == "PENDING" || visible[a]?.get()?.isShowing == true) return
        fun dp(n: Int) = (n * a.resources.displayMetrics.density).toInt()
        fun background(color: Int, radius: Int = 16, stroke: Int? = null) = GradientDrawable().apply {
            setColor(color); cornerRadius = dp(radius).toFloat()
            stroke?.let { setStroke(dp(1), it) }
        }
        fun column() = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        fun text(value: String, size: Float = 14f, color: Int = navy, bold: Boolean = false) =
            TextView(a).apply {
                this.text = value; textSize = size; setTextColor(color)
                if (bold) setTypeface(typeface, Typeface.BOLD)
            }
        fun LinearLayout.space(n: Int) { addView(View(a), LinearLayout.LayoutParams(1, dp(n))) }
        fun LinearLayout.addRow(title: String, value: String, strong: Boolean = false): TextView {
            val row = LinearLayout(a).apply { gravity = Gravity.TOP }
            row.addView(text(title, 13f, muted), LinearLayout.LayoutParams(0, -2, 1f))
            val detail = text(value, 14f, navy, strong).apply { gravity = Gravity.END }
            row.addView(detail, LinearLayout.LayoutParams(0, -2, 1.6f).apply { marginStart = dp(12) })
            addView(row, LinearLayout.LayoutParams(-1, -2))
            return detail
        }
        val sheet = BottomSheetDialog(a)
        visible[a] = WeakReference(sheet)
        val root = column().apply {
            background = background(Color.WHITE, 0).apply {
                val radius = dp(28).toFloat()
                cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
            }
            clipToOutline = true
        }
        val handle = View(a).apply { background = background(line, 3) }
        root.addView(handle, LinearLayout.LayoutParams(dp(36), dp(4)).apply {
            gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(12); bottomMargin = dp(16)
        })
        val heading = column().apply { setPadding(dp(24), 0, dp(24), dp(16)) }
        val title = text("Extend service time", 22f, navy, true)
        ViewCompat.setAccessibilityHeading(title, true)
        heading.addView(title); heading.space(6)
        heading.addView(text("Choose how much more time you need.", 14f, muted))
        root.addView(heading)
        val content = column().apply { setPadding(dp(24), 0, dp(24), dp(20)) }
        val scroll = androidx.core.widget.NestedScrollView(a).apply {
            isFillViewport = false; isVerticalScrollBarEnabled = false; addView(content)
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val summary = column().apply {
            background = background(Color.rgb(246, 248, 251))
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        summary.addView(text("CURRENT BOOKING", 11f, muted, true)); summary.space(12)
        summary.addRow("Started", BookingExtras.time(trip.startedAt)); summary.space(10)
        summary.addRow("Scheduled end", BookingExtras.time(trip.scheduledEnd()), true); summary.space(10)
        summary.addRow("Booked time", BookingExtras.duration(trip.totalMinutes())); summary.space(10)
        summary.addRow("Hourly rate", "${BookingExtras.money(trip.rate())}/hr")
        content.addView(summary); content.space(20)
        content.addView(text("Additional time", 16f, navy, true)); content.space(10)
        val group = RadioGroup(a)
        content.addView(group)
        val remaining = (1440 - trip.totalMinutes()).coerceAtLeast(0)
        val options = listOf(30, 60, 120, 0).map { minutes ->
            MaterialRadioButton(a).apply {
                id = View.generateViewId(); tag = minutes
                text = when (minutes) {
                    0 -> "Custom duration"
                    30 -> "30 minutes  ·  ${BookingExtras.money(trip.rate() / 2)}"
                    60 -> "1 hour  ·  ${BookingExtras.money(trip.rate())}"
                    else -> "2 hours  ·  ${BookingExtras.money(trip.rate() * 2)}"
                }
                textSize = 14f; setTextColor(navy)
                minHeight = dp(54); gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(10), dp(16), dp(10))
                // CompoundButton draws its indicator at the view edge, outside text padding.
                // Inset the indicator itself so it sits inside the rounded option card.
                buttonDrawable = android.graphics.drawable.InsetDrawable(buttonDrawable, dp(12), 0, dp(8), 0)
                buttonTintList = ColorStateList(arrayOf(
                    intArrayOf(-android.R.attr.state_enabled), intArrayOf(android.R.attr.state_checked), intArrayOf()
                ), intArrayOf(line, orange, muted))
                isEnabled = remaining > 0 && minutes <= remaining
                alpha = if (isEnabled) 1f else 0.45f
                group.addView(this, RadioGroup.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            }
        }
        val customField = TextInputLayout(a).apply {
            hint = "Additional minutes"
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setBoxCornerRadii(dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat())
            boxStrokeColor = orange
            defaultHintTextColor = ColorStateList.valueOf(muted)
            helperText = "Up to $remaining minutes"
            visibility = View.GONE
        }
        val custom = TextInputEditText(customField.context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(navy); textSize = 16f
            filters = arrayOf(android.text.InputFilter.LengthFilter(4))
            setSingleLine(true)
        }
        customField.addView(custom, LinearLayout.LayoutParams(-1, -2))
        content.addView(customField)
        content.space(12)
        val preview = column().apply {
            background = background(pale)
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        preview.addView(text("AFTER DRIVER APPROVAL", 11f, muted, true)); preview.space(12)
        val end = preview.addRow("New end time", "—", true); preview.space(12)
        val fee = preview.addRow("Additional fee", "—", true); preview.space(12)
        val duration = preview.addRow("Total duration", "—")
        content.addView(preview); content.space(12)
        content.addView(text("Your driver must approve the request before extra time and charges are added.", 13f, muted))
        val feedback = text("", 13f, Color.rgb(170, 48, 32)).apply {
            visibility = View.GONE
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        val footer = column().apply {
            setPadding(dp(24), dp(12), dp(24), dp(16))
            background = background(Color.WHITE, 0)
        }
        footer.addView(feedback)
        val actions = LinearLayout(a).apply { gravity = Gravity.CENTER_VERTICAL }
        fun action(label: String, primary: Boolean) = MaterialButton(a).apply {
            text = label; isAllCaps = false; textSize = 14f
            cornerRadius = dp(14); minHeight = dp(52)
            insetTop = 0; insetBottom = 0
            setPadding(dp(12), dp(12), dp(12), dp(12))
            backgroundTintList = ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                intArrayOf(if (primary) line else Color.WHITE, if (primary) orange else Color.WHITE))
            setTextColor(if (primary) Color.WHITE else navy)
            if (!primary) { strokeWidth = dp(1); strokeColor = ColorStateList.valueOf(line) }
        }
        val cancel = action("Cancel", false)
        val send = action("Send request", true)
        actions.addView(cancel, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(send, LinearLayout.LayoutParams(0, -2, 1.7f).apply { marginStart = dp(12) })
        footer.addView(actions); root.addView(footer)
        var sending = false
        fun selected(): Int {
            val minutes = group.findViewById<View>(group.checkedRadioButtonId)?.tag as? Int ?: return 0
            return if (minutes == 0) custom.text.toString().toIntOrNull() ?: 0 else minutes
        }
        fun update() {
            val n = selected()
            val valid = n in 1..remaining && trip.scheduledEnd() != null
            options.forEach { it.background = background(if (it.isChecked) pale else Color.WHITE,
                14, if (it.isChecked) orange else line) }
            end.text = if (valid) BookingExtras.time(trip.scheduledEnd()!! + n * 60000L) else "—"
            fee.text = if (valid) BookingExtras.money(trip.rate() * n / 60) else "—"
            duration.text = if (valid) BookingExtras.duration(trip.totalMinutes() + n) else "—"
            customField.error = if (customField.visibility == View.VISIBLE && custom.text?.isNotEmpty() == true && !valid)
                "Enter 1–$remaining minutes." else null
            send.isEnabled = valid && !sending
        }
        group.setOnCheckedChangeListener { _, id ->
            customField.visibility = if (group.findViewById<View>(id)?.tag == 0) View.VISIBLE else View.GONE
            if (customField.visibility == View.GONE) {
                custom.clearFocus()
                (a.getSystemService(Activity.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                    .hideSoftInputFromWindow(custom.windowToken, 0)
            }
            feedback.visibility = View.GONE
            update()
        }
        custom.doAfterTextChanged { update() }
        cancel.setOnClickListener { sheet.dismiss() }
        send.setOnClickListener {
            val n = selected()
            if (sending || n !in 1..remaining) return@setOnClickListener
            if (!NetworkStatus.requireConnection(a as AppCompatActivity) { refresh() }) return@setOnClickListener
            sending = true; feedback.visibility = View.GONE
            options.forEach { it.isEnabled = false }; custom.isEnabled = false; cancel.isEnabled = false
            sheet.setCancelable(false); sheet.behavior.isDraggable = false; send.text = "Sending…"; update()
            LocalTripStore(a).updateConfirmed(trip.id, { current ->
                if (current.extensionSequence == trip.extensionSequence) current.requestExtension(n) else current
            }) { ok ->
                if (!a.isDestroyed && !a.isFinishing) {
                    sending = false
                    sheet.setCancelable(true); sheet.behavior.isDraggable = true; cancel.isEnabled = true; custom.isEnabled = true
                    options.forEach { it.isEnabled = remaining > 0 && (it.tag as Int) <= remaining }
                    send.text = "Send request"; update()
                    if (ok) {
                        sheet.dismiss()
                        Toast.makeText(a, "Request sent. Waiting for your driver's approval.", Toast.LENGTH_LONG).show()
                    } else {
                        feedback.text = "Request wasn’t sent. Please try again."
                        feedback.visibility = View.VISIBLE
                    }
                    refresh()
                }
            }
        }
        sheet.setContentView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT))
        sheet.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        sheet.setOnDismissListener { visible.remove(a) }
        sheet.setOnShowListener {
            val bottom = sheet.findViewById<android.widget.FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            bottom?.apply {
                setBackgroundColor(Color.TRANSPARENT)
                layoutParams.height = (a.resources.displayMetrics.heightPixels * 0.9f).toInt()
            }
            sheet.behavior.apply {
                skipCollapsed = true
                isDraggable = true
                state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
        val first = options.firstOrNull { it.isEnabled }
        if (first != null) group.check(first.id)
        else {
            feedback.text = "This booking has reached the 24-hour limit."
            feedback.visibility = View.VISIBLE
        }
        update()
        sheet.show()
    }
}
