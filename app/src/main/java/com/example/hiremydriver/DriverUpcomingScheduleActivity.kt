package com.example.hiremydriver
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
class DriverUpcomingScheduleActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_upcoming_schedule)
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        val container = findViewById<View>(R.id.cardSchedule1).parent as LinearLayout
        container.removeAllViews()
        AccountDisplay.empty(container, "No upcoming trips.")
        findViewById<View>(R.id.btnMarkRead)?.isEnabled = false
    }
}