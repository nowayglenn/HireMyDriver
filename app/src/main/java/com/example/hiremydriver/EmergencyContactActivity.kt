package com.example.hiremydriver

import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class EmergencyContactActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_emergency_contact)

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
        val btnSave = findViewById<MaterialButton>(R.id.btnSaveContact)
        val containerRelationship = findViewById<LinearLayout>(R.id.containerRelationship)
        val txtRelationship = findViewById<TextView>(R.id.txtRelationship)
        val name = findViewById<android.widget.EditText>(R.id.edtContactName)
        val phone = findViewById<android.widget.EditText>(R.id.edtPhoneNumber)
        val details = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchTripDetails)
        btnSave.isEnabled = false
        EmergencyContactStore.load(this) { result ->
            if (isFinishing || isDestroyed) return@load
            btnSave.isEnabled = true
            result.getOrNull()?.let {
                if (savedInstanceState == null) {
                    name.setText(it.name); phone.setText(it.phone); txtRelationship.text = it.relationship
                    details.isChecked = it.tripDetails
                }
            }
            if (result.isFailure) Toast.makeText(this, "Could not load your saved contact. Check your connection.", Toast.LENGTH_LONG).show()
        }

        // Listeners
        btnBack.setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        containerRelationship.setOnClickListener {
            val options = arrayOf("Mother", "Father", "Spouse", "Sibling", "Friend", "Other")
            com.example.hiremydriver.trip.BookingDialogs.choose(this, "Select Relationship", options,
                options.indexOf(txtRelationship.text.toString())) { which ->
                txtRelationship.text = options[which]
            }
        }

        btnSave.setOnClickListener {
            val number = PhilippineMobile.normalize(phone.text.toString())
            if (number == null) { phone.error = "Enter a valid Philippine mobile number, e.g. 09171234567."; return@setOnClickListener }
            if (name.text.toString().trim().length !in 1..100) { name.error = "Enter the contact name (up to 100 characters)."; return@setOnClickListener }
            btnSave.isEnabled = false
            EmergencyContactStore.save(this, EmergencyContact(name.text.toString().trim(), number,
                txtRelationship.text.toString(), details.isChecked)) { saved ->
                if (isFinishing || isDestroyed) return@save
                btnSave.isEnabled = true
                Toast.makeText(this, if (saved) "Emergency contact saved successfully" else "Could not save your contact. Please try again.", Toast.LENGTH_LONG).show()
                if (saved) finish()
            }
        }
    }
}
