package com.example.hiremydriver

import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/** Refresh adapter for the existing local/demo screens; no network or booking mutations. */
class LocalScreenRefresh(
    activity: AppCompatActivity,
    refreshId: Int,
    scrollId: Int,
    private val reload: () -> Unit
) : DefaultLifecycleObserver {
    private val refresh = activity.findViewById<SwipeRefreshLayout>(refreshId)
    private val scroll = activity.findViewById<View>(scrollId)
    private var pending = false
    private val finish = Runnable { pending = false; refresh.isRefreshing = false }
    private val work = Runnable {
        try { reload() }
        catch (_: Exception) { Toast.makeText(activity, R.string.driver_refresh_failed, Toast.LENGTH_SHORT).show() }
        finally { refresh.postOnAnimation(finish) }
    }

    init {
        refresh.setColorSchemeColors(android.graphics.Color.rgb(255, 122, 0))
        refresh.setProgressBackgroundColorSchemeColor(android.graphics.Color.WHITE)
        refresh.setOnChildScrollUpCallback { _, _ -> scroll.canScrollVertically(-1) }
        refresh.setOnRefreshListener { start() }
        ViewCompat.addAccessibilityAction(refresh, activity.getString(R.string.refresh_bookings)) { _, _ -> start(); true }
        activity.lifecycle.addObserver(this)
    }

    private fun start() {
        if (pending) return
        pending = true
        refresh.isRefreshing = true
        refresh.postOnAnimation(work)
    }

    override fun onPause(owner: LifecycleOwner) {
        refresh.removeCallbacks(work)
        refresh.removeCallbacks(finish)
        pending = false
        refresh.isRefreshing = false
    }

    companion object {
        /** These two View all pages currently source their sample cards from XML. */
        fun reloadTemplate(activity: AppCompatActivity, layout: Int, scrollId: Int, preserveVisibility: List<Int> = emptyList()) {
            val scroll = activity.findViewById<NestedScrollView>(scrollId)
            val visibility = preserveVisibility.associateWith { activity.findViewById<View>(it).visibility }
            val template = activity.layoutInflater.inflate(layout, null, false)
            val templateScroll = template.findViewById<NestedScrollView>(scrollId)
            val content = templateScroll.getChildAt(0)
            (content.parent as ViewGroup).removeView(content)
            visibility.forEach { (id, state) -> content.findViewById<View>(id).visibility = state }
            scroll.removeAllViews()
            scroll.addView(content)
        }
    }
}
