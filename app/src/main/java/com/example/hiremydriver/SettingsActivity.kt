package com.example.hiremydriver

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import com.google.android.material.button.MaterialButton

class SettingsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Back Behavior
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            }
        })

        // Find Views
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        
        val menuNotifications = findViewById<LinearLayout>(R.id.menuNotifications)
        val menuChangePassword = findViewById<LinearLayout>(R.id.menuChangePassword)
        val menuLanguage = findViewById<LinearLayout>(R.id.menuLanguage)
        val menuLocation = findViewById<LinearLayout>(R.id.menuLocation)
        
        val menuTerms = findViewById<LinearLayout>(R.id.menuTerms)
        val menuPrivacy = findViewById<LinearLayout>(R.id.menuPrivacy)
        val menuHelp = findViewById<LinearLayout>(R.id.menuHelp)
        val menuAbout = findViewById<LinearLayout>(R.id.menuAbout)
        val menuDelete = findViewById<LinearLayout>(R.id.menuDelete)
        val menuLogout = findViewById<MaterialButton>(R.id.menuLogout)
        val txtVersion = findViewById<TextView>(R.id.txtVersion)

        // Set Version Text
        txtVersion?.text = getString(R.string.version_text, "1.0.0")

        // Listeners
        btnBack?.setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        menuNotifications?.setOnClickListener {
            val intent = Intent(this, NotificationSettingsActivity::class.java)
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        menuChangePassword?.setOnClickListener {
            val intent = Intent(this, ChangePasswordActivity::class.java)
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
        
        menuLanguage?.setOnClickListener {
            val intent = Intent(this, LanguageSettingsActivity::class.java)
                .putExtra(LanguageSettingsActivity.EXTRA_DRIVER, false)
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        menuLocation?.setOnClickListener {
            val intent = Intent(this, LocationSettingsActivity::class.java)
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
        
        menuTerms?.setOnClickListener {
            val intent = Intent(this, TermsConditionsActivity::class.java)
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        menuPrivacy?.setOnClickListener {
            val intent = Intent(this, PrivacyPolicyActivity::class.java)
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        menuHelp?.setOnClickListener {
            startActivity(Intent(this, CustomerHelpCenterActivity::class.java))
        }
        
        menuAbout?.setOnClickListener {
            val intent = Intent(this, AboutActivity::class.java)
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        menuDelete?.setOnClickListener {
            val intent = Intent(this, DeleteAccountActivity::class.java)
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        menuLogout?.setOnClickListener {
            com.example.hiremydriver.trip.BookingDialogs.confirm(this, "Logout", "Are you sure you want to log out?", "Logout", "Cancel") {
            LaunchSession.logout(this)
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
            startActivity(intent, android.app.ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
            finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        findViewById<TextView>(R.id.customerSettingsLanguageSummary).text = LocaleHelper.languageName(this)
    }
}
