package com.example.hiremydriver

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.hiremydriver.trip.BookingDialogs

object NetworkStatus {
    private val dialogs = java.util.WeakHashMap<AppCompatActivity, androidx.appcompat.app.AlertDialog>()
    fun blocksBackgroundInteraction(activity: AppCompatActivity): Boolean = dialogs[activity]?.isShowing == true
    private val gracePeriods = java.util.WeakHashMap<AppCompatActivity, OfflineGracePeriod>()
    private fun remainingGrace(activity: AppCompatActivity): Long =
        gracePeriods.getOrPut(activity) { OfflineGracePeriod() }
            .remaining(connected(activity), android.os.SystemClock.elapsedRealtime())

    // During the grace period, skeletons replace data only; normal touch dispatch stays active.
    // The modal dialog itself blocks background interaction after the grace period expires.
    fun connected(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    fun requireConnection(activity: AppCompatActivity, retry: () -> Unit = {}): Boolean {
        if (connected(activity)) return true
        if (activity.isFinishing || activity.isDestroyed || dialogs[activity]?.isShowing == true) return false
        val view = activity.layoutInflater.inflate(R.layout.dialog_network_retry, null)
        val dialog = BookingDialogs.custom(activity, view)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialogs[activity] = dialog
        val retryButton = view.findViewById<android.view.View>(R.id.networkRetry)
        var replaying = false
        retryButton.setOnClickListener {
            if (!dialog.isShowing || replaying) return@setOnClickListener
            if (!connected(activity)) {
                // Replay the original disappear/reappear feedback without removing the modal
                // window. Its dim layer and background touch protection stay active throughout.
                val decor = dialog.window?.decorView ?: return@setOnClickListener
                replaying = true
                retryButton.isEnabled = false
                decor.animate().alpha(0f).setDuration(90L).withEndAction {
                    if (dialog.isShowing) {
                        decor.animate().alpha(1f).setDuration(180L).withEndAction {
                            replaying = false
                            retryButton.isEnabled = true
                        }.start()
                    }
                }.start()
                return@setOnClickListener
            }
            dialog.dismiss()
            retry()
        }
        view.findViewById<android.view.View>(R.id.networkLater).setOnClickListener {
            activity.finishAffinity()
            activity.finishAndRemoveTask()
            dialog.dismiss()
        }
        dialog.setOnDismissListener {
            dialog.window?.decorView?.let { decor ->
                decor.animate().withEndAction(null).cancel()
                decor.alpha = 1f
            }
            // A dismissed dialog must not remove a newly opened retry dialog.
            if (dialogs[activity] === dialog) dialogs.remove(activity)
        }
        return false
    }
    fun observe(activity: AppCompatActivity) {
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            private val manager = activity.getSystemService(ConnectivityManager::class.java)
            private val handler = android.os.Handler(android.os.Looper.getMainLooper())
            private var watching = false
            private var warned = false
            private val check = object : Runnable {
                override fun run() {
                    handler.removeCallbacks(this)
                    if (!activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return
                    val remaining = remainingGrace(activity)
                    if (connected(activity)) { warned = false; dialogs[activity]?.dismiss() }
                    else if (remaining > 0L) handler.postDelayed(this, remaining)
                    else if (!warned) { warned = true; requireConnection(activity) }
                }
            }
            private val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onLost(network: Network) { handler.post(check) }
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) { handler.post(check) }
            }
            override fun onResume(owner: LifecycleOwner) {
                if (!watching) { manager.registerDefaultNetworkCallback(callback); watching = true }
                check.run()
            }
            override fun onPause(owner: LifecycleOwner) {
                handler.removeCallbacksAndMessages(null)
                if (watching) { manager.unregisterNetworkCallback(callback); watching = false }
            }
            override fun onDestroy(owner: LifecycleOwner) {
                dialogs.remove(activity)?.dismiss()
                gracePeriods.remove(activity)
            }
        })
    }
}
