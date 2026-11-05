package com.example.hiremydriver.notifications

import android.graphics.drawable.GradientDrawable
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.hiremydriver.R
import com.google.firebase.firestore.ListenerRegistration

/** The unread indicator belongs to the bell, so scrolling/clipping moves them together. */
class NotificationBellBadge(private val activity: AppCompatActivity) {
    private var watch: ListenerRegistration? = null
    private var anchor: View? = null
    private var dot: GradientDrawable? = null
    private var placement: View.OnLayoutChangeListener? = null
    private var active = false
    fun start() {
        stop()
        val driver = activity.findViewById<View>(R.id.btnDriverNotification) != null
        val icon = activity.findViewById<View>(if (driver) R.id.btnDriverNotification else R.id.btnNotification) ?: return
        val uid = AppNotifications.currentUid(activity, driver) ?: return
        val size = (10 * activity.resources.displayMetrics.density).toInt()
        val display = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(android.graphics.Color.rgb(220,38,38))
            alpha = 0
        }
        active = true; anchor = icon; dot = display
        icon.overlay.add(display)
        fun place() {
            if (!active || anchor !== icon || icon.width == 0) return
            val x = (icon.width/2+size/2).coerceIn(0,(icon.width-size).coerceAtLeast(0))
            val y = (icon.height/2-size*3/2).coerceIn(0,(icon.height-size).coerceAtLeast(0))
            display.setBounds(x,y,x+size,y+size)
        }
        placement = View.OnLayoutChangeListener { _,_,_,_,_,_,_,_,_ -> place() }.also {
            icon.addOnLayoutChangeListener(it)
        }
        icon.post { place() }
        watch = AppNotifications.db(activity,driver).collection("users").document(uid).collection("notifications")
            .whereIn("kind", NotificationFeedPolicy.kinds(driver)).addSnapshotListener { result,error ->
                if (!active || dot !== display) return@addSnapshotListener
                val unread = error == null && AppNotifications.currentUid(activity,driver) == uid &&
                    result?.documents?.any { it.getBoolean("read") != true } == true
                display.alpha = if (unread) 255 else 0
                icon.contentDescription = if (unread) "Notifications, unread updates" else "Notifications"
                place()
            }
    }
    fun stop() {
        active = false; watch?.remove(); watch = null
        placement?.let { anchor?.removeOnLayoutChangeListener(it) }
        dot?.let { anchor?.overlay?.remove(it) }
        anchor?.contentDescription = "Notifications"
        dot = null; anchor = null; placement = null
    }
}
