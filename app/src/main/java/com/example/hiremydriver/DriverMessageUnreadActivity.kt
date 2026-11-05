package com.example.hiremydriver

import android.content.Intent
import android.os.Bundle

/** Legacy route kept compatible with the current shared main/unread screen. */
class DriverMessageUnreadActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, DriverMessageActivity::class.java).apply {
            putExtra("OPEN_UNREAD", true)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
        finish()
    }
}
