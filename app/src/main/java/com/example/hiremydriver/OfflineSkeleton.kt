package com.example.hiremydriver

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/** Keeps cached cards intact behind a continuously animated offline placeholder. */
class OfflineSkeleton private constructor(activity: AppCompatActivity, content: View, layout: Int, count: Int) {
    init {
        val parent = content.parent as ViewGroup
        val index = parent.indexOfChild(content)
        val params = content.layoutParams
        val wrapper = FrameLayout(activity)
        val placeholders = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        parent.removeView(content)
        wrapper.addView(content, FrameLayout.LayoutParams(-1, -2))
        wrapper.addView(placeholders, FrameLayout.LayoutParams(-1, -2))
        parent.addView(wrapper, index, params)
        watch(activity) { offline ->
            if (offline) CardSkeleton.show(placeholders, layout, count)
            content.visibility = if (offline) View.GONE else View.VISIBLE
            placeholders.visibility = if (offline) View.VISIBLE else View.GONE
        }
    }
    companion object {
        @JvmStatic fun bind(activity: AppCompatActivity, content: View, layout: Int, count: Int) {
            OfflineSkeleton(activity, content, layout, count)
        }

        fun watch(activity: AppCompatActivity, onChanged: (Boolean) -> Unit) {
            val handler = Handler(Looper.getMainLooper())
            val manager = activity.getSystemService(ConnectivityManager::class.java)
            val update = Runnable {
                if (!activity.isDestroyed && !activity.isFinishing) onChanged(!NetworkStatus.connected(activity))
            }
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onLost(network: Network) { handler.post(update) }
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) { handler.post(update) }
            }
            activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
                private var registered = false
                override fun onStart(owner: LifecycleOwner) {
                    if (!registered) { manager.registerDefaultNetworkCallback(callback); registered = true }
                    update.run()
                }
                override fun onStop(owner: LifecycleOwner) {
                    if (registered) { manager.unregisterNetworkCallback(callback); registered = false }
                    handler.removeCallbacks(update)
                }
                override fun onDestroy(owner: LifecycleOwner) { handler.removeCallbacks(update) }
            })
            update.run()
        }
    }
}
