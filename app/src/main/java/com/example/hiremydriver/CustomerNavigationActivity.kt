package com.example.hiremydriver

import android.app.ActivityOptions
import android.content.Intent
import android.os.SystemClock
import android.view.View
import androidx.lifecycle.Lifecycle

/** One navigation path for the four customer tabs, including rapid taps across tabs. */
open class CustomerNavigationActivity : BaseActivity() {
    private var navigationInProgress = false
    private val navigationIds = intArrayOf(R.id.navHome, R.id.navBookings, R.id.navMessages, R.id.navProfile)

    protected fun setupCustomerNavigation() {
        val targets = arrayOf(HomeActivity::class.java, MyBookingActivity::class.java,
            MessagesActivity::class.java, ProfileActivity::class.java)
        navigationIds.zip(targets).forEach { (id, target) ->
            findViewById<View>(id).setOnClickListener {
                if (navigationInProgress || isFinishing || isDestroyed ||
                    !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@setOnClickListener
                if (!gate.tryNavigate(javaClass.name, target.name, SystemClock.elapsedRealtime())) return@setOnClickListener

                navigationInProgress = true
                onLeavingCustomerTab()
                setNavigationEnabled(false)
                val intent = Intent(this, target).addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
                startActivity(intent, ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
                // Keep Home and its rendered lists while visiting another tab.
                if (this !is HomeActivity) finish()
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            }
        }
    }

    protected open fun onLeavingCustomerTab() = Unit

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
        val gate = CustomerNavigationGate()
    }
}
