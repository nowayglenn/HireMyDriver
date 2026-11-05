package com.example.hiremydriver
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
class DriverNotificationsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_notifications)
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        com.example.hiremydriver.notifications.NotificationFeed.bind(this, true)
    }
}