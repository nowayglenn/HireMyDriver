package com.example.hiremydriver.notifications

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.example.hiremydriver.*
import com.google.firebase.Timestamp
import com.google.firebase.firestore.ListenerRegistration
import android.widget.TextView
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import java.util.Date

/** Counts incoming messages after the server read cursor, never just the number of conversations. */
class UnreadBadge(private val activity: AppCompatActivity) {
    private var inbox: ListenerRegistration? = null
    private val watches = mutableMapOf<String, ListenerRegistration>()
    private val cursors = mutableMapOf<String, Long>()
    private val counts = mutableMapOf<String, Int>()
    private var badge: TextView? = null
    private var host: ViewGroup? = null
    private var placement: View.OnLayoutChangeListener? = null
    private var icon: View? = null
    private var generation = 0

    @com.google.android.material.badge.ExperimentalBadgeUtils
    fun start() {
        stop()
        val ticket = ++generation
        val driver = activity.findViewById<View>(R.id.iconMessage) != null
        val anchor = activity.findViewById<View>(if (driver) R.id.iconMessage else R.id.iconMessages) ?: return
        icon = anchor
        val uid = AppNotifications.currentUid(activity, driver) ?: return
        val db = AppNotifications.db(activity, driver)
        val navigation = activity.findViewById<ViewGroup>(R.id.bottomNavigation) ?: return
        host = navigation
        val density = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val display = TextView(activity).apply {
            setTextColor(Color.WHITE); textSize = 11f; gravity = Gravity.CENTER
            includeFontPadding = false
            minWidth = dp(18); minHeight = dp(18); setPadding(dp(4), 0, dp(4), 0)
            background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = dp(20).toFloat(); setColor(Color.rgb(220, 38, 38)) }
            visibility = View.GONE
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        badge = display
        navigation.overlay.add(display)
        fun place() {
            if (badge !== display || navigation.width == 0) return
            display.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            val navPosition = IntArray(2); val iconPosition = IntArray(2)
            navigation.getLocationInWindow(navPosition); anchor.getLocationInWindow(iconPosition)
            // Anchor to the visible icon's center, independent of the active pill's wider bounds.
            val left = (iconPosition[0] - navPosition[0] + anchor.width / 2 + dp(8)).coerceIn(0, (navigation.width - display.measuredWidth).coerceAtLeast(0))
            val top = (iconPosition[1] - navPosition[1] - dp(4)).coerceIn(dp(2), (navigation.height - display.measuredHeight).coerceAtLeast(dp(2)))
            display.layout(left, top, left + display.measuredWidth, top + display.measuredHeight)
        }
        placement = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> place() }.also {
            navigation.addOnLayoutChangeListener(it); anchor.addOnLayoutChangeListener(it)
        }
        navigation.post { place() }
        fun render() {
            val total = counts.values.sum()
            display.text = if (total > 99) "99+" else total.toString()
            display.visibility = if (total > 0) View.VISIBLE else View.GONE
            place()
        }

        inbox = db.collection("conversations").whereEqualTo(if (driver) "driverId" else "customerId", uid)
            .addSnapshotListener { snapshot, error ->
                if (ticket != generation) return@addSnapshotListener
                if (error != null || snapshot == null || AppNotifications.currentUid(activity, driver) != uid) {
                    counts.clear(); render(); return@addSnapshotListener
                }
                val ids = snapshot.documents.map { it.id }.toSet()
                (watches.keys - ids).forEach { watches.remove(it)?.remove(); cursors.remove(it); counts.remove(it) }
                snapshot.documents.forEach { doc ->
                    val read = ((doc.get("readAt") as? Map<*, *>)?.get(uid) as? Timestamp)?.toDate()?.time ?: 0L
                    val updated = doc.getTimestamp("updatedAt")?.toDate()?.time ?: 0L
                    if (updated <= read) { watches.remove(doc.id)?.remove(); cursors.remove(doc.id); counts.remove(doc.id); return@forEach }
                    if (cursors[doc.id] == read && watches.containsKey(doc.id)) return@forEach
                    watches.remove(doc.id)?.remove(); counts.remove(doc.id); cursors[doc.id] = read
                    watches[doc.id] = doc.reference.collection("messages").whereGreaterThan("createdAt", Timestamp(Date(read)))
                        .addSnapshotListener messages@ { messages, failure ->
                            if (ticket != generation || cursors[doc.id] != read) return@messages
                            if (failure != null || messages == null || AppNotifications.currentUid(activity, driver) != uid) {
                                counts.remove(doc.id); render(); return@messages
                            }
                            counts[doc.id] = messages.documents.count { !it.metadata.hasPendingWrites() && it.getString("senderId") != uid &&
                                (it.getTimestamp("createdAt")?.toDate()?.time ?: 0L) > read }
                            render()
                        }
                }
                render()
            }
    }

    @com.google.android.material.badge.ExperimentalBadgeUtils
    fun stop() {
        generation++
        inbox?.remove(); inbox = null; watches.values.forEach { it.remove() }; watches.clear(); cursors.clear(); counts.clear()
        placement?.let { host?.removeOnLayoutChangeListener(it); icon?.removeOnLayoutChangeListener(it) }; placement = null
        badge?.let { host?.overlay?.remove(it) }; badge = null; host = null; icon = null
    }
}
