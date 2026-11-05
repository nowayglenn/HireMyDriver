package com.example.hiremydriver

import android.os.Bundle
import android.widget.ImageButton
import androidx.activity.OnBackPressedCallback

class AboutActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

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

        // Listeners
        btnBack.setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}
