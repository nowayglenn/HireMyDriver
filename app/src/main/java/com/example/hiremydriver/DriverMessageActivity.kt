package com.example.hiremydriver

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.example.hiremydriver.messaging.ChatInbox
import com.example.hiremydriver.messaging.ChatRole

class DriverMessageActivity : DriverNavigationActivity() {
    private var unreadOnly = false
    private lateinit var inbox: ChatInbox


    // Tabs
    private lateinit var tabMain: LinearLayout
    private lateinit var tabUnread: LinearLayout
    private lateinit var txtMain: TextView
    private lateinit var txtUnread: TextView
    private lateinit var mainIndicator: View
    private lateinit var unreadIndicator: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_message)

        // Animate active nav background
        animateNavActiveBackground(findViewById(R.id.iconMessage))

        initializeViews()
        inbox = ChatInbox(this, ChatRole.DRIVER) { count -> txtUnread.text = if (count == 0) "Unread" else "Unread ($count)" }
        setupTabListeners()
        setupClickListeners()
        selectTab(savedInstanceState?.getBoolean("unread_only") ?: intent.getBooleanExtra("OPEN_UNREAD", false))
    }

    private fun initializeViews() {

        tabMain = findViewById(R.id.tabMain)
        tabUnread = findViewById(R.id.tabUnread)
        txtMain = findViewById(R.id.txtMain)
        txtUnread = findViewById(R.id.txtUnread)
        mainIndicator = findViewById(R.id.mainIndicator)
        unreadIndicator = findViewById(R.id.unreadIndicator)
    }

    private fun setupTabListeners() {
        tabMain.setOnClickListener { selectTab(isUnreadOnly = false) }
        tabUnread.setOnClickListener { selectTab(isUnreadOnly = true) }
    }

    private fun selectTab(isUnreadOnly: Boolean) {
        unreadOnly = isUnreadOnly
        inbox.filter(unreadOnly)
        if (isUnreadOnly) {
            // Highlight Unread Tab
            txtUnread.setTextColor(Color.parseColor("#061B41"))
            txtUnread.typeface = android.graphics.Typeface.DEFAULT_BOLD
            unreadIndicator.setBackgroundColor(Color.parseColor("#FF7900"))

            txtMain.setTextColor(Color.parseColor("#667085"))
            txtMain.typeface = android.graphics.Typeface.DEFAULT
            mainIndicator.setBackgroundColor(Color.TRANSPARENT)

        } else {
            // Highlight Main / View All Tab
            txtMain.setTextColor(Color.parseColor("#061B41"))
            txtMain.typeface = android.graphics.Typeface.DEFAULT_BOLD
            mainIndicator.setBackgroundColor(Color.parseColor("#FF7900"))

            txtUnread.setTextColor(Color.parseColor("#667085"))
            txtUnread.typeface = android.graphics.Typeface.DEFAULT
            unreadIndicator.setBackgroundColor(Color.TRANSPARENT)

        }
    }

    private fun setupClickListeners() {
        setupDriverNavigation()
    }

    override fun onResume() {
        super.onResume()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        selectTab(intent.getBooleanExtra("OPEN_UNREAD", false))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("unread_only", unreadOnly)
        super.onSaveInstanceState(outState)
    }
}
