package com.example.hiremydriver.trip

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import com.example.hiremydriver.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox

/** Scoped booking confirmations; callbacks run only after explicit confirmation. */
object BookingDialogs {
    private fun scrollable(activity: Activity, view: View): View {
        if (view is android.widget.ScrollView || view is androidx.core.widget.NestedScrollView) return view
        return android.widget.ScrollView(activity).apply {
            isFillViewport = true
            setBackgroundResource(R.drawable.bg_booking_dialog)
            clipToOutline = true
            addView(view)
        }
    }
    fun custom(activity: Activity, view: View): AlertDialog =
        AlertDialog.Builder(activity).setView(scrollable(activity, view)).create().also { show(activity, it) }
    private val visible = java.util.WeakHashMap<Activity, java.lang.ref.WeakReference<AlertDialog>>()

    private fun show(activity: Activity, dialog: AlertDialog, smoothCompletion: Boolean = false) {
        // Finish measuring before the first frame; animate the window, not its layout.
        dialog.create()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val widthDp = minOf(420, (activity.resources.configuration.screenWidthDp - 48).coerceAtLeast(1))
            setLayout((widthDp * activity.resources.displayMetrics.density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            setDimAmount(0.4f)
            setWindowAnimations(if (smoothCompletion) R.style.ServiceCompletedDialogAnimation else R.style.BookingDialogAnimation)
        }
        visible[activity] = java.lang.ref.WeakReference(dialog)
        dialog.show()
    }
    fun choose(
        activity: Activity,
        title: String,
        options: Array<String>,
        selectedIndex: Int,
        onSelected: (Int) -> Unit
    ): AlertDialog {
        visible[activity]?.get()?.takeIf { it.isShowing }?.let { return it }
        val view = activity.layoutInflater.inflate(R.layout.dialog_booking_choice, null)
        view.findViewById<TextView>(R.id.bookingChoiceTitle).text = title
        ViewCompat.setAccessibilityHeading(view.findViewById(R.id.bookingChoiceTitle), true)
        val list = view.findViewById<android.widget.ListView>(R.id.bookingChoiceList)
        list.choiceMode = android.widget.ListView.CHOICE_MODE_SINGLE
        list.selector = ColorDrawable(Color.TRANSPARENT)
        list.adapter = android.widget.ArrayAdapter(activity, R.layout.item_booking_choice, options)
        if (selectedIndex in options.indices) {
            list.setItemChecked(selectedIndex, true)
            list.setSelection(selectedIndex)
        }
        val dialog = AlertDialog.Builder(activity).setView(scrollable(activity, view)).create()
        var selected = false
        list.setOnItemClickListener { _, _, index, _ ->
            if (selected) return@setOnItemClickListener
            selected = true
            dialog.dismiss()
            onSelected(index)
        }
        view.findViewById<View>(R.id.bookingChoiceCancel).setOnClickListener { dialog.dismiss() }
        val listHeightDp = minOf(options.size * 60, 360, (activity.resources.configuration.screenHeightDp - 260).coerceAtLeast(64))
        list.layoutParams = list.layoutParams.apply { height = (listHeightDp * activity.resources.displayMetrics.density).toInt() }
        show(activity, dialog)
        return dialog
    }

    fun confirm(
        activity: Activity,
        title: String,
        message: String,
        positive: String,
        negative: String? = "Go back",
        icon: Int = R.drawable.ic_check_circle,
        requiredCheck: String? = null,
        smoothCompletion: Boolean = false,
        onConfirm: () -> Unit
    ): AlertDialog {
        visible[activity]?.get()?.takeIf { it.isShowing }?.let { return it }
        val view = activity.layoutInflater.inflate(R.layout.dialog_booking_confirmation, null)
        view.findViewById<TextView>(R.id.bookingDialogTitle).text = title
        ViewCompat.setAccessibilityHeading(view.findViewById(R.id.bookingDialogTitle), true)
        view.findViewById<TextView>(R.id.bookingDialogMessage).text = message
        view.findViewById<ImageView>(R.id.bookingDialogIcon).setImageResource(icon)
        val confirm = view.findViewById<MaterialButton>(R.id.bookingDialogConfirm).apply { text = positive }
        val cancel = view.findViewById<MaterialButton>(R.id.bookingDialogCancel).apply {
            text = negative
            visibility = if (negative == null) View.GONE else View.VISIBLE
        }
        val check = view.findViewById<MaterialCheckBox>(R.id.bookingDialogCheck)
        if (requiredCheck != null) {
            check.visibility = View.VISIBLE
            check.text = requiredCheck
            confirm.isEnabled = false
            check.setOnCheckedChangeListener { _, checked -> confirm.isEnabled = checked }
        }
        val dialog = AlertDialog.Builder(activity).setView(scrollable(activity, view)).create()
        var confirmed = false
        confirm.setOnClickListener {
            if (!confirmed && (requiredCheck == null || check.isChecked)) {
                confirmed = true
                confirm.isEnabled = false
                dialog.dismiss()
                onConfirm()
            }
        }
        cancel.setOnClickListener { dialog.dismiss() }
        show(activity, dialog, smoothCompletion)
        return dialog
    }
}
