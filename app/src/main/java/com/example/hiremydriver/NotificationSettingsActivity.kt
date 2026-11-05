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
import com.google.android.material.materialswitch.MaterialSwitch

class NotificationSettingsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification_settings)
        val saved = CustomerNotificationPreferences(this).load()
        val booking = findViewById<MaterialSwitch>(R.id.switchBookingUpdates)
        val chat = findViewById<MaterialSwitch>(R.id.switchChatMessages)
        booking.isChecked = savedInstanceState?.getBoolean("draft_booking") ?: saved.bookingUpdates
        chat.isChecked = savedInstanceState?.getBoolean("draft_chat") ?: saved.chatMessages
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnSaveNotifications).setOnClickListener {
            CustomerNotificationPreferences(this).save(
                CustomerNotificationPreferences.Choices(booking.isChecked, chat.isChecked))
            Toast.makeText(this, R.string.notification_saved_local, Toast.LENGTH_SHORT).show()
            finish()
        }
        findViewById<View>(R.id.btnNotificationPhoneSettings).setOnClickListener {
            val appSettings = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            val settings = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            else appSettings
            try { startActivity(settings) }
            catch (_: ActivityNotFoundException) {
                try { startActivity(appSettings) }
                catch (_: ActivityNotFoundException) {
                    Toast.makeText(this, R.string.permission_settings_unavailable, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        findViewById<TextView>(R.id.txtNotificationPermission).setText(
            if (NotificationManagerCompat.from(this).areNotificationsEnabled())
                R.string.notification_os_enabled else R.string.notification_os_disabled)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("draft_booking", findViewById<MaterialSwitch>(R.id.switchBookingUpdates).isChecked)
        outState.putBoolean("draft_chat", findViewById<MaterialSwitch>(R.id.switchChatMessages).isChecked)
        super.onSaveInstanceState(outState)
    }
}
