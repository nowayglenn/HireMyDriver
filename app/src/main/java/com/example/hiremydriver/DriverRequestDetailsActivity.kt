package com.example.hiremydriver

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton

class DriverRequestDetailsActivity : BaseActivity() {
    private var savedDetails: com.example.hiremydriver.trip.SavedBookingDetails? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_request_details)
        if (intent.hasExtra(com.example.hiremydriver.trip.LocalTripStore.ID)) {
            savedDetails = com.example.hiremydriver.trip.SavedBookingDetails(this)
            savedDetails?.request()
            return
        }

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val btnViewMap = findViewById<View>(R.id.btnViewMap)

        val txtReqMonth = findViewById<TextView>(R.id.txtReqMonth)
        val txtReqDay = findViewById<TextView>(R.id.txtReqDay)
        val txtReqTime = findViewById<TextView>(R.id.txtReqTime)

        val imgCustomerProfile = findViewById<ImageView>(R.id.imgCustomerProfile)
        val txtCustomerName = findViewById<TextView>(R.id.txtCustomerName)
        val txtCustomerPhone = findViewById<TextView>(R.id.txtCustomerPhone)
        val txtCustomerEmail = findViewById<TextView>(R.id.txtCustomerEmail)

        val txtPickupLocation = findViewById<TextView>(R.id.txtPickupLocation)
        val txtDropoffLocation = findViewById<TextView>(R.id.txtDropoffLocation)

        val txtBookingType = findViewById<TextView>(R.id.txtBookingType)
        val txtDateTime = findViewById<TextView>(R.id.txtDateTime)
        val txtTotalAmount = findViewById<TextView>(R.id.txtTotalAmount)

        val btnAcceptBooking = findViewById<MaterialButton>(R.id.btnAcceptBooking)
        val btnDeclineBooking = findViewById<MaterialButton>(R.id.btnDeclineBooking)

        // Read intent data
        val name = intent.getStringExtra("CUSTOMER_NAME") ?: "Customer"
        val email = intent.getStringExtra("CUSTOMER_EMAIL") ?: "maria@gmail.com"
        val phone = intent.getStringExtra("CUSTOMER_PHONE") ?: "0917 123 4567"
        val pickup = intent.getStringExtra("PICKUP_LOCATION") ?: "Cebu City"
        val dropoff = intent.getStringExtra("DROPOFF_LOCATION") ?: "Mandaue City"
        val pkg = intent.getStringExtra("BOOKING_PACKAGE") ?: "By the Day (1 day)"
        val amount = intent.getStringExtra("TOTAL_AMOUNT") ?: "₱2,500.00"
        val imgRes = intent.getIntExtra("CUSTOMER_IMAGE", R.drawable.no_profile)

        txtCustomerName.text = name
        txtCustomerEmail.text = email
        txtCustomerPhone.text = phone
        txtPickupLocation.text = pickup
        txtDropoffLocation.text = dropoff
        txtBookingType.text = pkg
        txtTotalAmount.text = amount
        imgCustomerProfile.setImageResource(imgRes)

        btnBack.setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        btnViewMap.setOnClickListener {
            val intent = Intent(this, LiveTrackingActivity::class.java)
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        btnAcceptBooking.setOnClickListener {
            Toast.makeText(this, "Accepted booking request from $name", Toast.LENGTH_SHORT).show()
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        btnDeclineBooking.setOnClickListener {
            showDeclineDialog(name)
        }
    }

    private fun showDeclineDialog(customerName: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_decline_booking, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val btnConfirmDecline = dialogView.findViewById<MaterialButton>(R.id.btnConfirmDecline)
        val btnCancelDecline = dialogView.findViewById<MaterialButton>(R.id.btnCancelDecline)

        btnConfirmDecline.setOnClickListener {
            dialog.dismiss()
            Toast.makeText(this, "Declined request from $customerName", Toast.LENGTH_SHORT).show()
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        btnCancelDecline.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
    override fun onResume() {
        super.onResume()
        savedDetails?.request()
    }
}
