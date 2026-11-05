package com.example.hiremydriver.trip

import android.app.Activity
import android.graphics.Color
import android.view.View
import android.widget.*
import androidx.core.widget.doAfterTextChanged
import com.example.hiremydriver.R
import com.example.hiremydriver.NetworkStatus
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Small additions to the existing details and timer; all changes wait for server acknowledgement. */
object BookingExtras {
    fun money(value: Double) = "₱%,.2f".format(Locale.US, value)
    fun duration(minutes: Int) = listOfNotNull((minutes / 60).takeIf { it > 0 }?.let { "${it}h" },
        (minutes % 60).takeIf { it > 0 }?.let { "${it}m" }).joinToString(" ").ifBlank { "0m" }
    fun time(value: Long?) = value?.let { SimpleDateFormat("MMM d, h:mm a", Locale.ENGLISH).apply {
        timeZone = TimeZone.getTimeZone("Asia/Manila")
    }.format(java.util.Date(it)) } ?: "Not started"
    fun timer(t: TripSession): String {
        if (t.phase != TripPhase.IN_PROGRESS) return TripUi.timer(t)
        val minutes = (t.remainingMillis(System.currentTimeMillis()) + 59999) / 60000
        return "Service Time\n${duration(minutes.toInt())} remaining\nEnds at ${time(t.scheduledEnd())}\n" +
            if (t.extensionStatus == "PENDING") "Extension request pending" else "Request more time →"
    }
    private fun label(box: LinearLayout, value: String, heading: Boolean = false) = TextView(box.context).apply {
        text = value; textSize = if (heading) 20f else 15f; setTextColor(Color.rgb(6,27,65))
        setPadding(0,12,0,12); if (heading) setTypeface(typeface, android.graphics.Typeface.BOLD)
        box.addView(this)
    }
    fun details(a: Activity, t: TripSession, driver: Boolean, refresh: () -> Unit) {
        val anchor = a.findViewById<TextView>(R.id.savedBookingNotes) ?: return
        val parent = (anchor.parent as? View)?.parent as? LinearLayout ?: return
        val old = parent.findViewWithTag<LinearLayout>("booking_extras")
        val box = old ?: LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; tag = "booking_extras"; parent.addView(this) }
        box.removeAllViews()
        if (t.cancellationReason.isNotBlank()) label(box, "Cancellation reason: ${t.cancellationReason}")
        if (t.extensionMinutes > 0) label(box, "Original duration: ${duration(t.durationHours * 60)}\nApproved extension: ${duration(t.extensionMinutes)}\nTotal duration: ${duration(t.totalMinutes())}\nOriginal amount: ${money(t.rate() * t.durationHours)}\nExtension fee: ${money(t.extensionFee())}\nTotal amount: ${money(t.totalAmount())}\nScheduled end: ${time(t.scheduledEnd())}")
        if (t.extensionStatus.isNotBlank()) {
            label(box, "Extension request: ${t.extensionStatus.lowercase().replaceFirstChar { it.uppercase() }}\nRequested additional time: ${duration(t.requestedMinutes)}" +
                if (t.extensionStatus == "PENDING") "\nProposed end: ${time(t.scheduledEnd()?.plus(t.requestedMinutes * 60000L))}\nAdditional fee: ${money(t.rate() * t.requestedMinutes / 60)}" else "")
            if (driver && t.phase == TripPhase.IN_PROGRESS && t.extensionStatus == "PENDING") {
                val review = a.layoutInflater.inflate(R.layout.view_review_extension_button, box, false)
                box.addView(review)
                review.setOnClickListener {
                    a.startActivity(android.content.Intent(a, com.example.hiremydriver.maps.DriverTripMapActivity::class.java)
                        .putExtra(LocalTripStore.ID, t.id))
                }
            }
        }
    }
    fun cancelPending(a: Activity, id: String, refresh: () -> Unit) {
        val t = LocalTripStore(a).get(id)
        if (t == null || t.phase != TripPhase.REQUESTED) {
            Toast.makeText(a, "This booking is no longer pending.", Toast.LENGTH_SHORT).show()
            refresh(); return
        }
        val reasons = arrayOf("Change of plans","Driver no longer needed","Incorrect booking details","Schedule conflict","Other reason (please specify)")
        BookingDialogs.choose(a,"Why cancel this booking?",reasons,-1) { index ->
            if (index != 4) confirmCancel(a,t,reasons[index],refresh)
            else {
                val view = a.layoutInflater.inflate(R.layout.dialog_booking_cancel_reason, null)
                val input = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.cancelReasonInput)
                val field = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.cancelReasonField)
                input.doAfterTextChanged { field.error = null }
                val dialog = BookingDialogs.custom(a,view)
                dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                view.findViewById<MaterialButton>(R.id.cancelReasonContinue).setOnClickListener {
                    val reason = input.text.toString().trim()
                    if (reason.length !in 1..1000) field.error = "Enter a reason (up to 1,000 characters)."
                    else { dialog.dismiss(); submitCancel(a,t,reason,refresh) }
                }
                view.findViewById<MaterialButton>(R.id.cancelReasonKeep).setOnClickListener { dialog.dismiss() }
            }
        }
    }
    private fun confirmCancel(a: Activity,t: TripSession,reason: String,refresh: () -> Unit) {
        if (LocalTripStore(a).get(t.id)?.phase != TripPhase.REQUESTED) {
            Toast.makeText(a, "This booking is no longer pending.", Toast.LENGTH_SHORT).show()
            refresh(); return
        }
        BookingDialogs.confirm(a,"Cancel booking?",reason,"Cancel Booking","Keep booking") {
            submitCancel(a,t,reason,refresh)
        }
    }
    private fun submitCancel(a: Activity, t: TripSession, reason: String, refresh: () -> Unit) {
        val store = LocalTripStore(a)
        if (store.get(t.id)?.phase != TripPhase.REQUESTED) {
            Toast.makeText(a, "This booking is no longer pending.", Toast.LENGTH_SHORT).show()
            refresh(); return
        }
        if (NetworkStatus.requireConnection(a as androidx.appcompat.app.AppCompatActivity) { refresh() })
            store.updateConfirmed(t.id, { it.cancelPending(reason) }) { saved ->
                if (saved && !a.isFinishing && !a.isDestroyed)
                    Toast.makeText(a, "Cancel request submitted. Booking cancelled.", Toast.LENGTH_LONG).show()
                refresh()
            }
    }
    fun request(a: Activity, t: TripSession, refresh: () -> Unit) = ServiceExtensionSheet.show(a, t, refresh)
}
