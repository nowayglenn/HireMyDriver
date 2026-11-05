package com.example.hiremydriver

import android.os.Bundle
import com.example.hiremydriver.messaging.ChatInbox
import com.example.hiremydriver.messaging.ChatRole

class MessagesActivity : CustomerNavigationActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_customer_messages)

        // Animate active nav background
        animateNavActiveBackground(findViewById(R.id.iconMessages))

        ChatInbox(this, ChatRole.CUSTOMER)

        // Bottom navigation
        setupCustomerNavigation()

    }
}
