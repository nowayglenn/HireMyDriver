package com.example.hiremydriver

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import com.example.hiremydriver.trip.LocalReviewStore
import com.google.android.material.button.MaterialButton

class DriverProfileActivity : DriverNavigationActivity() {

    private lateinit var btnSettings: ImageButton

    private lateinit var rowPersonalInfo: LinearLayout
    private lateinit var rowVehicle: LinearLayout
    private lateinit var rowDocuments: LinearLayout
    private lateinit var rowHelp: LinearLayout
    private lateinit var rowContactSupport: LinearLayout
    private lateinit var btnLogout: MaterialButton

    private lateinit var photoModel: DriverProfilePhotoModel
    private var photoFlowOpen = false

    private val cropPhoto = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        (application as HireMyDriverApplication).endPhotoSelection()
        photoFlowOpen = false
        if (result.resultCode == RESULT_OK) result.data?.data?.let(photoModel::save)
        updatePhotoButton()
    }

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            (application as HireMyDriverApplication).endPhotoSelection()
            photoFlowOpen = false
            updatePhotoButton()
        } else {
            // Session-only providers can still be read by the editor in this app.
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            catch (_: SecurityException) { }
            try {
                cropPhoto.launch(Intent(this, CustomerPhotoCropActivity::class.java).setData(uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            } catch (_: android.content.ActivityNotFoundException) {
                (application as HireMyDriverApplication).endPhotoSelection()
                photoFlowOpen = false
                updatePhotoButton()
                Toast.makeText(this, R.string.driver_photo_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_profile)
        photoFlowOpen = savedInstanceState?.getBoolean("driver_photo_flow_open") ?: false
        if (photoFlowOpen) (application as HireMyDriverApplication).beginPhotoSelection()
        photoModel = ViewModelProvider(this)[DriverProfilePhotoModel::class.java]

        // Animate active nav background
        animateNavActiveBackground(findViewById(R.id.iconProfile))

        initializeViews()
        setupClickListeners()
        setupDriverNavigation()
        LocalReviewStore(this).observe(this) { refreshReviewCount() }
        findViewById<View>(R.id.btnEditDriverPhoto).setOnClickListener {
            if (photoFlowOpen || photoModel.state.value?.busy == true) return@setOnClickListener
            photoFlowOpen = true
            updatePhotoButton()
            (application as HireMyDriverApplication).beginPhotoSelection()
            try { pickPhoto.launch(arrayOf("image/*")) }
            catch (_: android.content.ActivityNotFoundException) {
                (application as HireMyDriverApplication).endPhotoSelection()
                photoFlowOpen = false
                updatePhotoButton()
                Toast.makeText(this, R.string.driver_photo_error, Toast.LENGTH_SHORT).show()
            }
        }
        photoModel.state.observe(this) { state ->
            updatePhotoButton()
            if (state.saved) {
                DriverProfilePhoto.show(findViewById(R.id.imgDriverProfilePhoto))
                Toast.makeText(this, R.string.driver_photo_saved, Toast.LENGTH_SHORT).show()
                photoModel.clearResult()
            } else if (state.error) {
                Toast.makeText(this, R.string.driver_photo_error, Toast.LENGTH_LONG).show()
                photoModel.clearResult()
            }
        }

        // Remove opening animation
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun initializeViews() {
        btnSettings = findViewById(R.id.btnProfileSettings)

        rowPersonalInfo = findViewById(R.id.rowPersonalInfo)
        rowVehicle = findViewById(R.id.rowDrivingInfo)
        rowDocuments = findViewById(R.id.rowDocuments)
        rowHelp = findViewById(R.id.rowHelp)
        rowContactSupport = findViewById(R.id.rowContactSupport)
        btnLogout = findViewById(R.id.btnLogout)

    }

    private fun setupClickListeners() {
        findViewById<View>(R.id.rowDriverChangePassword).setOnClickListener {
            startActivity(Intent(this, ChangePasswordActivity::class.java)
                .putExtra(ChangePasswordActivity.DRIVER_SESSION, true))
        }

        findViewById<LinearLayout>(R.id.rowCustomerReviews).setOnClickListener {
            startActivity(Intent(this, CustomerReviewsActivity::class.java)
                .putExtra("DRIVER_REVIEW", true))
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, DriverSettingsActivity::class.java))
        }

        rowPersonalInfo.setOnClickListener {
            startActivity(Intent(this, DriverPersonalSettingsActivity::class.java)
                .putExtra("PROFILE_NAME", findViewById<TextView>(R.id.txtDriverName).text.toString())
                .putExtra("PROFILE_EMAIL", findViewById<TextView>(R.id.txtDriverEmail).text.toString()))
        }

        findViewById<LinearLayout>(R.id.rowDrivingInfo)?.setOnClickListener {
            startActivity(Intent(this, DriverDrivingInfoActivity::class.java))
        }

        rowDocuments.setOnClickListener {
            startActivity(Intent(this, DriverDocumentsSettingsActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.rowAvailabilitySchedule).setOnClickListener {
            startActivity(Intent(this, DriverAvailabilityScheduleActivity::class.java))
        }

        rowHelp.setOnClickListener {
            startActivity(Intent(this, DriverHelpCenterActivity::class.java))
        }

        rowContactSupport.setOnClickListener {
            startActivity(Intent(this, DriverContactSupportActivity::class.java))
        }

        btnLogout.setOnClickListener {
            com.example.hiremydriver.trip.BookingDialogs.confirm(this, "Logout", "Are you sure you want to log out?", "Logout", "Cancel") {
            LaunchSession.logout(this)
            Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
            startActivity(intent, android.app.ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
            finish()
            }
        }

    }

    private fun updatePhotoButton() {
        findViewById<View>(R.id.btnEditDriverPhoto).isEnabled = !photoFlowOpen && photoModel.state.value?.busy != true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("driver_photo_flow_open", photoFlowOpen)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        DriverProfilePhoto.show(findViewById(R.id.imgDriverProfilePhoto))
        updatePhotoButton()
        val personal = getSharedPreferences(DriverAuth.preferenceName(this, DriverPersonalSettingsActivity.PREFERENCES), MODE_PRIVATE)
        findViewById<TextView>(R.id.txtDriverName).text = personal.getString("name", null)
            ?: DriverAuth.currentUser(this)?.displayName ?: "Driver"
        findViewById<TextView>(R.id.txtDriverEmail).text = DriverAuth.currentUser(this)?.email.orEmpty()
        fun showExperience() {
            val driving = getSharedPreferences(DriverAuth.preferenceName(this, "driver_driving_info"), MODE_PRIVATE)
            findViewById<TextView>(R.id.txtExperience).text = driving.getString("experience", null)?.takeIf { it.isNotBlank() }
                ?: personal.getString("experience", null)?.takeIf { it.isNotBlank() } ?: "—"
        }
        showExperience()
        com.example.hiremydriver.recommendation.DriverMatchingSettings.hydrate(this) {
            if (!isFinishing && !isDestroyed) showExperience()
        }
        val trips = com.example.hiremydriver.trip.LocalTripStore(this).all()
        findViewById<TextView>(R.id.txtCompletedTrips).text = trips.count { it.phase == com.example.hiremydriver.trip.TripPhase.COMPLETED }.toString()
        com.example.hiremydriver.trip.RatingDisplay.bind(findViewById(R.id.txtRating), DriverAuth.currentUser(this)?.uid.orEmpty(), true, 20f)
        refreshReviewCount()
    }

    private fun refreshReviewCount() {
        val reviews = LocalReviewStore(this).all()
        findViewById<TextView>(R.id.txtCustomerReviewsSummary).text =
            if (reviews.isEmpty()) "View customer ratings and feedback"
            else "${reviews.size} ${if (reviews.size == 1) "review" else "reviews"} · View feedback"
    }
}
