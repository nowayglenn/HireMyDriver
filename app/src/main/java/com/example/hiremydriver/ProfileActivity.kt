package com.example.hiremydriver

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class ProfileActivity : CustomerNavigationActivity() {


    private lateinit var menuEditProfile: LinearLayout
    private lateinit var menuReviews: LinearLayout
    private lateinit var menuEmergencyContact: LinearLayout
    private lateinit var menuSettings: LinearLayout


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_customer_profile)

        // Animate active nav background
        animateNavActiveBackground(findViewById(R.id.iconProfile))

        // =====================================================
        // BACK BEHAVIOR
        // =====================================================

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val intent = Intent(this@ProfileActivity, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        })

        // =====================================================
        // FIND VIEWS
        // =====================================================


        menuEditProfile = findViewById(R.id.menuEditProfile)
        menuReviews = findViewById(R.id.menuReviews)
        menuEmergencyContact = findViewById(R.id.menuEmergencyContact)
        menuSettings = findViewById(R.id.menuSettings)


        // =====================================================
        // EDIT PROFILE
        // =====================================================

        val openEditProfile = {
            val intent = Intent(this, EditProfileActivity::class.java)
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        menuEditProfile.setOnClickListener { openEditProfile() }


        // =====================================================
        // MY REVIEWS
        // =====================================================

        menuReviews.setOnClickListener {
            val intent = Intent(this, CustomerReviewsActivity::class.java)
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }


        // =====================================================
        // EMERGENCY CONTACT
        // =====================================================

        menuEmergencyContact.setOnClickListener {
            val intent = Intent(this, EmergencyContactActivity::class.java)
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }


        // =====================================================
        // SETTINGS
        // =====================================================

        menuSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }


        // =====================================================
        // PROFILE PHOTO
        // =====================================================
        
        // Display only. Photo selection belongs to Edit Profile.
        findViewById<android.view.View>(R.id.imgProfilePhoto).apply {
            isClickable = false
            isFocusable = false
        }


        // =====================================================
        // BOTTOM NAVIGATION
        // =====================================================

        setupCustomerNavigation()

    }
    override fun onResume() {
        super.onResume()
        findViewById<android.widget.TextView>(R.id.txtProfileName).text = CustomerAuth.displayName(this)
        findViewById<android.widget.TextView>(R.id.txtProfileEmail).text = CustomerAuth.currentUser(this)?.email.orEmpty()
        CustomerProfilePhoto.show(findViewById(R.id.imgProfilePhoto), CustomerProfilePhoto.savedUri(this))
    }
}
