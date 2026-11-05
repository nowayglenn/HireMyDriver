package com.example.hiremydriver

import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.activity.OnBackPressedCallback
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw

class SplashActivity : BaseActivity() {
    override fun backgroundBehindSystemBars() = true
    private val handler = Handler(Looper.getMainLooper())
    private var deadline = 0L
    private val navigate = Runnable {
        if (!isFinishing && !isDestroyed) {
            if (!intent.getBooleanExtra(RESUME_SCREEN, false)) {
                val target = when (LaunchSession.role(this)) {
                    LaunchRole.CUSTOMER -> HomeActivity::class.java
                    LaunchRole.DRIVER -> DriverHomeActivity::class.java
                    LaunchRole.NONE -> if (getSharedPreferences(OnboardingActivity.PREFS_NAME, MODE_PRIVATE)
                        .getBoolean(OnboardingActivity.KEY_ONBOARDING_COMPLETED, false)) MainActivity::class.java
                        else OnboardingActivity::class.java
                }
                startActivity(Intent(this, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            }
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        val content = findViewById<ViewGroup>(android.R.id.content)
        val artwork = content.getChildAt(0)
        // This decorative screen needs no safe-area padding. Keep other screens' inset handling intact.
        ViewCompat.setOnApplyWindowInsetsListener(content) { _, _ ->
            artwork.setPadding(0, 0, 0, 0)
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(content)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { it.remove() }
        }
        deadline = savedInstanceState?.getLong("deadline") ?: 0L
        artwork.doOnPreDraw {
            // Start the display interval when both the background and branding are ready to draw.
            if (deadline == 0L) deadline = SystemClock.elapsedRealtime() + 900L
            handler.removeCallbacks(navigate)
            handler.postDelayed(navigate, (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { moveTaskToBack(true) }
        })
    }
    override fun onResume() {
        super.onResume()
        if (deadline != 0L) {
            handler.postDelayed(navigate, (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
        }
    }
    override fun onPause() { handler.removeCallbacks(navigate); super.onPause() }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong("deadline", deadline)
        super.onSaveInstanceState(outState)
    }
    companion object { const val RESUME_SCREEN = "resume_existing_screen" }
}
