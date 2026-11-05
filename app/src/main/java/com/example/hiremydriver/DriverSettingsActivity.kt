package com.example.hiremydriver

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat

class DriverSettingsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_settings)
        findViewById<View>(R.id.btnDriverSettingsBack).setOnClickListener { finish() }
        findViewById<View>(R.id.driverSettingsLanguage).setOnClickListener {
            startActivity(Intent(this, LanguageSettingsActivity::class.java)
                .putExtra(LanguageSettingsActivity.EXTRA_DRIVER, true))
        }
        val destinations = mapOf(
            R.id.driverSettingsLocation to LocationSettingsActivity::class.java,
            R.id.driverSettingsTerms to TermsConditionsActivity::class.java,
            R.id.driverSettingsPrivacy to PrivacyPolicyActivity::class.java,
            R.id.driverSettingsAbout to AboutActivity::class.java
        )
        destinations.forEach { (id, activity) ->
            findViewById<View>(id).setOnClickListener { startActivity(Intent(this, activity)) }
        }
        findViewById<View>(R.id.driverSettingsNotifications).setOnClickListener {
            val settings = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            } else appSettingsIntent()
            try {
                startActivity(settings)
            } catch (_: ActivityNotFoundException) {
                try { startActivity(appSettingsIntent()) }
                catch (_: ActivityNotFoundException) {
                    Toast.makeText(this, "Open your phone settings to manage app notifications.", Toast.LENGTH_LONG).show()
                }
            }
        }
        @Suppress("DEPRECATION")
        val version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        findViewById<TextView>(R.id.driverSettingsVersion).text = "HireMyDriver · Version $version"
    }

    override fun onResume() {
        super.onResume()
        findViewById<TextView>(R.id.driverSettingsLanguageSummary).text = LocaleHelper.languageName(this)
        findViewById<TextView>(R.id.driverSettingsNotificationSummary).text =
            if (NotificationManagerCompat.from(this).areNotificationsEnabled()) getString(R.string.driver_alerts_enabled)
            else getString(R.string.driver_alerts_disabled)
    }

    private fun appSettingsIntent() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
}
