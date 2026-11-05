package com.example.hiremydriver.notifications

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.hiremydriver.*
import com.google.firebase.firestore.ListenerRegistration

object NotificationFeed {
    fun bind(activity: AppCompatActivity, driver: Boolean) {
        val uid = AppNotifications.currentUid(activity, driver) ?: return
        val db = AppNotifications.db(activity, driver)
        val ref = db.collection("users").document(uid).collection("notifications")
        val container = activity.findViewById<View>(R.id.notif1).parent as LinearLayout
        val mark = activity.findViewById<View>(R.id.btnMarkRead)
        var rows = emptyList<com.google.firebase.firestore.DocumentSnapshot>()
        var allRows = rows
        val pending = mutableSetOf<String>()
        val visibleRows = mutableMapOf<String, View>()
        fun markVisible() {
            if (!activity.hasWindowFocus() || !activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return
            rows.filter { it.getBoolean("read") != true && it.id !in pending }.forEach { doc ->
                val row = visibleRows[doc.id] ?: return@forEach
                val rect = android.graphics.Rect()
                if (row.getGlobalVisibleRect(rect) && rect.height() >= row.height / 2) {
                    pending.add(doc.id)
                    doc.reference.update("read", true).addOnCompleteListener { pending.remove(doc.id) }
                }
            }
        }
        val scrolled = android.view.ViewTreeObserver.OnScrollChangedListener { markVisible() }
        val focus = android.view.ViewTreeObserver.OnWindowFocusChangeListener { if (it) markVisible() }
        mark?.setOnClickListener { allRows.filter { it.getBoolean("read") != true }.chunked(400).forEach { group ->
            db.batch().apply { group.forEach { update(it.reference, "read", true) } }.commit()
        } }
        container.removeAllViews(); AccountDisplay.empty(container, "Loading notifications…")
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            var listener: ListenerRegistration? = null
            override fun onStart(owner: LifecycleOwner) {
                container.viewTreeObserver.addOnScrollChangedListener(scrolled)
                container.viewTreeObserver.addOnWindowFocusChangeListener(focus)
                // Filter before limiting: a busy chat must not push booking updates out of the feed.
                // A single-field query also works with the existing Firestore indexes.
                listener = ref.whereIn("kind", NotificationFeedPolicy.kinds(driver))
                    .addSnapshotListener { result, error ->
                        if (AppNotifications.currentUid(activity, driver) != uid) return@addSnapshotListener
                        if (error != null) { if (rows.isEmpty()) { container.removeAllViews(); AccountDisplay.empty(container, "Could not load notifications. Please retry.") }; return@addSnapshotListener }
                        allRows = result?.documents.orEmpty()
                            .filter { NotificationFeedPolicy.includes(it.getString("kind"), driver) }
                            .sortedByDescending { it.getTimestamp("createdAt")?.toDate()?.time ?: 0L }
                        rows = allRows.take(100)
                        container.removeAllViews(); visibleRows.clear(); mark?.isEnabled = allRows.any { it.getBoolean("read") != true }
                        if (rows.isEmpty()) AccountDisplay.empty(container, "No notifications yet.")
                        rows.forEach { doc ->
                            val row = activity.layoutInflater.inflate(R.layout.item_notification, container, false)
                            row.findViewById<android.widget.ImageView>(R.id.imgNotificationIcon).setImageResource(
                                when (doc.getString("kind")) {
                                    "review" -> R.drawable.ic_star
                                    "approval" -> R.drawable.ic_verified
                                    else -> R.drawable.icons_calendar
                                })
                            row.findViewById<TextView>(R.id.txtNotificationTitle).text = doc.getString("title")
                            row.findViewById<TextView>(R.id.txtNotificationDesc).text = doc.getString("body")
                            row.findViewById<TextView>(R.id.txtNotificationTime).text = doc.getTimestamp("createdAt")?.toDate()?.let {
                                java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(it)
                            }.orEmpty()
                            row.findViewById<View>(R.id.viewUnreadDot).visibility = if (doc.getBoolean("read") == true) View.GONE else View.VISIBLE
                            row.setOnClickListener {
                                val data = doc.data.orEmpty().filterValues { it is String }.mapValues { it.value as String }
                                activity.startActivity(AppNotifications.intent(activity, data))
                            }
                            container.addView(row)
                            visibleRows[doc.id] = row
                        }
                        container.post { markVisible() }
                    }
            }
            override fun onResume(owner: LifecycleOwner) { container.post { markVisible() } }
            override fun onStop(owner: LifecycleOwner) {
                listener?.remove(); listener = null
                container.viewTreeObserver.removeOnScrollChangedListener(scrolled)
                container.viewTreeObserver.removeOnWindowFocusChangeListener(focus)
            }
        })
    }
}
