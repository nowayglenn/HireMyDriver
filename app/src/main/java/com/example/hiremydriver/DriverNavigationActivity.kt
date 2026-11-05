package com.example.hiremydriver

import android.app.ActivityOptions
import android.content.Intent
import android.os.SystemClock
import android.view.View
import androidx.lifecycle.Lifecycle

/** One navigation path for the four driver tabs, including rapid taps across tabs. */
open class DriverNavigationActivity : BaseActivity() {
    private var navigationInProgress = false
    private val navigationIds = intArrayOf(R.id.navHome, R.id.navBooking, R.id.navMessage, R.id.navProfile)

    protected fun setupDriverNavigation() {
        val targets = arrayOf(DriverHomeActivity::class.java, DriverBookingActivity::class.java,
            DriverMessageActivity::class.java, DriverProfileActivity::class.java)
        navigationIds.zip(targets).forEach { (id, target) ->
            findViewById<View>(id).setOnClickListener {
                if (navigationInProgress || isFinishing || isDestroyed ||
                    !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@setOnClickListener
                if (!gate.tryNavigate(javaClass.name, target.name, SystemClock.elapsedRealtime())) return@setOnClickListener

                navigationInProgress = true
                setNavigationEnabled(false)
                val intent = Intent(this, target).addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
                startActivity(intent, ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
                finish()
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        navigationInProgress = false
        setNavigationEnabled(true)
    }

    private fun setNavigationEnabled(enabled: Boolean) {
        navigationIds.forEach { findViewById<View>(it)?.isEnabled = enabled }
    }

    private companion object {
        // Main-thread click handlers share this across activity instances.
        val gate = DriverNavigationGate()
    }
}
