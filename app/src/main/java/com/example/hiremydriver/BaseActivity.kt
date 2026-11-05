package com.example.hiremydriver

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

open class BaseActivity : AppCompatActivity() {
    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        // The dialog has its own window. Reject any queued touches to the activity beneath it,
        // but leave scrolling and navigation enabled during the offline skeleton grace period.
        if (NetworkStatus.blocksBackgroundInteraction(this)) return true
        return super.dispatchTouchEvent(event)
    }

    private var originalInsetPadding: Insets? = null
    private var unreadBadge: com.example.hiremydriver.notifications.UnreadBadge? = null
    private var bellBadge: com.example.hiremydriver.notifications.NotificationBellBadge? = null
    private val firstLocationPermission = FirstLaunchLocationPermission(this) { requestHomePermissions() }
    private val notificationPermission = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    @com.google.android.material.badge.ExperimentalBadgeUtils
    override fun onStart() {
        super.onStart()
        unreadBadge = com.example.hiremydriver.notifications.UnreadBadge(this).also { it.start() }
        bellBadge = com.example.hiremydriver.notifications.NotificationBellBadge(this).also { it.start() }
        if (this is HomeActivity || this is DriverHomeActivity) {
            com.example.hiremydriver.notifications.AppNotifications.register(this)
        }
    }
    @com.google.android.material.badge.ExperimentalBadgeUtils
    override fun onStop() {
        unreadBadge?.stop(); unreadBadge = null
        bellBadge?.stop(); bellBadge = null
        super.onStop()
    }

    override fun onPostResume() {
        super.onPostResume()
        requestHomePermissions()
    }

    private fun requestHomePermissions() {
        if ((this !is HomeActivity && this !is DriverHomeActivity) || isFinishing || isDestroyed ||
            !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return
        // Ask only after login, and finish the location choice before asking about notifications.
        if (firstLocationPermission.requestIfNeeded()) return
        val asked = getSharedPreferences("notification_permission", MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= 33 && com.example.hiremydriver.trip.TripNotifications.needsPermission(this) && !asked.getBoolean("asked", false)) {
            asked.edit().putBoolean("asked", true).apply()
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Opt in only for screens whose background should extend under the system bars.
    protected open fun backgroundBehindSystemBars(): Boolean = false

    // A fixed-height image header can absorb the top inset instead of leaving a strip above it.
    protected open fun statusBarImageHeader(): View? = null

    // Image headers can specify a solid status-bar color without changing their artwork.
    protected open fun statusBarBackgroundColor(): Int? = null

    protected open fun onKeyboardVisibilityChanged(visible: Boolean) = Unit

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        if (this !is SplashActivity && this !is MainActivity) NetworkStatus.observe(this)
    }

    override fun onContentChanged() {
        super.onContentChanged()
        val content = findViewById<ViewGroup>(android.R.id.content) ?: return
        val screen = content.getChildAt(0) ?: return
        val fullBackground = backgroundBehindSystemBars()
        val insetTarget = if (fullBackground) screen else content
        val imageHeader = if (fullBackground) statusBarImageHeader() else null
        val headerHeight = imageHeader?.layoutParams?.height ?: 0
        val headerTopPadding = imageHeader?.paddingTop ?: 0
        val padding = originalInsetPadding ?: Insets.of(
            insetTarget.paddingLeft, insetTarget.paddingTop, insetTarget.paddingRight, insetTarget.paddingBottom
        ).also { originalInsetPadding = it }

        // Padding the root keeps its background full screen while its children stay in the safe area.
        // Other screens retain the content-container insets. Never accumulate padding on redispatch.
        var header: View? = screen.findViewWithTag("screen_header")
        while (header != null && header.background !is ColorDrawable) {
            header = header.parent as? View
        }
        val screenColor = (screen.background as? ColorDrawable)?.color ?: Color.parseColor("#061B41")
        val topColor = statusBarBackgroundColor() ?: (header?.background as? ColorDrawable)?.color ?: screenColor
        val background = SystemBarBackground(topColor, screenColor)
        content.background = background
        if (fullBackground && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        WindowCompat.getInsetsController(window, content).apply {
            isAppearanceLightStatusBars = ColorUtils.calculateLuminance(topColor) > 0.5
            isAppearanceLightNavigationBars = ColorUtils.calculateLuminance(screenColor) > 0.5
        }
        ViewCompat.setOnApplyWindowInsetsListener(content) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            onKeyboardVisibilityChanged(insets.isVisible(WindowInsetsCompat.Type.ime()))
            val safe = ScreenInsets.padding(padding, bars, keyboard.bottom)
            insetTarget.setPadding(safe.left, if (imageHeader != null) padding.top else safe.top, safe.right, safe.bottom)
            imageHeader?.let {
                it.setPadding(it.paddingLeft, headerTopPadding + bars.top, it.paddingRight, it.paddingBottom)
                val height = headerHeight + bars.top
                if (it.layoutParams.height != height) {
                    it.layoutParams = it.layoutParams.apply { this.height = height }
                }
            }
            background.topInset = safe.top
            // Children must not apply the same system spacing a second time.
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(content)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    protected fun animateNavActiveBackground(view: View?) {
        view?.let {
            val anim = AlphaAnimation(0.0f, 1.0f)
            anim.duration = 400
            it.startAnimation(anim)
        }
    }
}
