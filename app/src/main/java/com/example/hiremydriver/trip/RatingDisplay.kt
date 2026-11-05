package com.example.hiremydriver.trip

import android.widget.TextView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.hiremydriver.DriverAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.WeakHashMap

/** Derive the average from server-authorized reviews, never client-editable profile totals. */
object RatingDisplay {
    private val bindings = WeakHashMap<TextView, String>()
    private val values = mutableMapOf<String, Pair<String, Boolean>>()
    fun bind(view: TextView, uid: String, driver: Boolean = false, numericSize: Float = view.textSize / view.resources.displayMetrics.scaledDensity, countView: TextView? = null) {
        values[uid]?.let { (label, empty) -> view.text = label; view.textSize = if (empty) minOf(14f, numericSize) else numericSize }
        if (uid.isBlank() || bindings[view] == uid) return
        if (values[uid] == null) { view.text = "No rating"; view.textSize = minOf(14f, numericSize) }
        val owner = com.example.hiremydriver.ScreenOwner.from(view.context) ?: return
        bindings[view] = uid
        val db = if (driver) FirebaseFirestore.getInstance(DriverAuth.firebaseApp(view.context)) else FirebaseFirestore.getInstance()
        val observer = object : DefaultLifecycleObserver {
            var listener: ListenerRegistration? = null
            override fun onStart(owner: LifecycleOwner) {
                listener = db.collection("reviews").whereEqualTo("driverUid", uid).addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { data, error ->
                    if (error != null || data == null || bindings[view] != uid) return@addSnapshotListener
                    if (data.isEmpty && data.metadata.isFromCache) return@addSnapshotListener
                    val ratings = data.documents.mapNotNull { BookingReview.fromRecord(it.id, it.data.orEmpty(), 0L)?.rating }
                    view.text = if (ratings.isEmpty()) "No rating" else String.format(java.util.Locale.getDefault(), "%.1f", ratings.average())
                    view.textSize = if (ratings.isEmpty()) minOf(14f, numericSize) else numericSize
                    values[uid] = view.text.toString() to ratings.isEmpty()
                    countView?.text = "(${ratings.size} ${if (ratings.size == 1) "review" else "reviews"})"
                }
            }
            override fun onStop(owner: LifecycleOwner) { listener?.remove(); listener = null }
        }
        owner.lifecycle.addObserver(observer)
        view.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: android.view.View) = Unit
            override fun onViewDetachedFromWindow(v: android.view.View) {
                observer.onStop(owner); owner.lifecycle.removeObserver(observer)
                bindings.remove(view); view.removeOnAttachStateChangeListener(this)
            }
        })
    }
}
