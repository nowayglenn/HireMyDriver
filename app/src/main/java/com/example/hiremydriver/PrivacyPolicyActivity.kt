package com.example.hiremydriver

import android.os.Bundle
import android.widget.ImageButton
import androidx.activity.OnBackPressedCallback
import com.google.android.material.button.MaterialButton

class PrivacyPolicyActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy_policy)

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
        val btnAccept = findViewById<MaterialButton>(R.id.btnAccept)

        // Listeners
        btnBack.setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        btnAccept.setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}
