package com.example.hiremydriver

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.card.MaterialCardView
import com.google.android.material.button.MaterialButton

class DriverDrivingInfoActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_driving_info)

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val btnSaveChanges = findViewById<MaterialButton>(R.id.btnSaveChanges)
        val btnServiceArea = findViewById<View>(R.id.btnServiceArea)
        val txtServiceArea = findViewById<TextView>(R.id.txtServiceArea)
        val prefs = getSharedPreferences(DriverAuth.preferenceName(this, "driver_driving_info"), MODE_PRIVATE)
        val languageChoices = listOf("Filipino" to R.id.chkFilipino, "English" to R.id.chkEnglish,
            "Cebuano/Bisaya" to R.id.chkCebuano).map { (name, id) -> name to findViewById<CheckBox>(id) }
        languageChoices.forEach { (name, checkbox) -> checkbox.isChecked = name in prefs.getStringSet("languages", emptySet()).orEmpty() }
        val experience = findViewById<android.widget.EditText>(R.id.edtExperience)
        val rate = findViewById<android.widget.EditText>(R.id.edtHourlyRate)
        experience.setText(prefs.getString("experience", null) ?: getSharedPreferences(
            DriverAuth.preferenceName(this, DriverPersonalSettingsActivity.PREFERENCES), MODE_PRIVATE).getString("experience", ""))
        rate.setText(prefs.getString("rate", ""))
        txtServiceArea.text = savedInstanceState?.getString("selected_service_areas")
            ?: prefs.getString("area", "Select service areas")

        val cardAll = findViewById<MaterialCardView>(R.id.cardAll)
        val chkAll = findViewById<CheckBox>(R.id.chkAll)

        val cardSedan = findViewById<MaterialCardView>(R.id.cardSedan)
        val chkSedan = findViewById<CheckBox>(R.id.chkSedan)

        val cardSuv = findViewById<MaterialCardView>(R.id.cardSuv)
        val chkSuv = findViewById<CheckBox>(R.id.chkSuv)

        val cardVan = findViewById<MaterialCardView>(R.id.cardVan)
        val chkVan = findViewById<CheckBox>(R.id.chkVan)

        val cardPickup = findViewById<MaterialCardView>(R.id.cardPickup)
        val chkPickup = findViewById<CheckBox>(R.id.chkPickup)

        val cardTruck = findViewById<MaterialCardView>(R.id.cardTruck)
        val chkTruck = findViewById<CheckBox>(R.id.chkTruck)

        val cardAutomatic = findViewById<MaterialCardView>(R.id.cardAutomatic)
        val chkAutomatic = findViewById<CheckBox>(R.id.chkAutomatic)

        val cardManual = findViewById<MaterialCardView>(R.id.cardManual)
        val chkManual = findViewById<CheckBox>(R.id.chkManual)
        val choices = listOf(chkSedan, chkSuv, chkVan, chkPickup, chkTruck, chkAutomatic, chkManual)
        choices.forEach { it.isChecked = prefs.getBoolean(resources.getResourceEntryName(it.id), false) }

        btnBack.setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        fun updateCardStyle(card: MaterialCardView, chk: CheckBox) {
            if (chk.isChecked) {
                card.setCardBackgroundColor(Color.parseColor("#FFF3E0"))
                card.strokeColor = Color.parseColor("#FF7900")
                card.strokeWidth = (1.5 * resources.displayMetrics.density).toInt()
            } else {
                card.setCardBackgroundColor(Color.WHITE)
                card.strokeColor = Color.parseColor("#E5E7EB")
                card.strokeWidth = (1 * resources.displayMetrics.density).toInt()
            }
        }

        val vehicleCards = listOf(cardSedan to chkSedan, cardSuv to chkSuv,
            cardVan to chkVan, cardPickup to chkPickup, cardTruck to chkTruck)
        var syncingAll = false
        fun refreshAll() {
            syncingAll = true
            chkAll.isChecked = vehicleCards.all { it.second.isChecked }
            updateCardStyle(cardAll, chkAll)
            syncingAll = false
        }
        // Checkbox taps and card taps must update the same selection and visual state.
        for ((card, checkbox) in vehicleCards + listOf(cardAutomatic to chkAutomatic, cardManual to chkManual)) {
            card.setOnClickListener { checkbox.isChecked = !checkbox.isChecked }
            checkbox.setOnCheckedChangeListener { _, _ ->
                updateCardStyle(card, checkbox)
                if (!syncingAll) refreshAll()
            }
            updateCardStyle(card, checkbox)
        }
        cardAll.setOnClickListener { chkAll.isChecked = !chkAll.isChecked }
        chkAll.setOnCheckedChangeListener { _, checked ->
            if (!syncingAll) {
                syncingAll = true
                vehicleCards.forEach { (_, checkbox) -> checkbox.isChecked = checked }
                syncingAll = false
                refreshAll()
            }
        }
        refreshAll()

        val areaPicker = ServiceAreaPicker(this)
        btnServiceArea.setOnClickListener {
            areaPicker.show(txtServiceArea.text.toString()) { txtServiceArea.text = it }
        }

        val allChoices = choices + languageChoices.map { it.second }
        val initial = listOf(experience.text.toString(), rate.text.toString(), txtServiceArea.text.toString()) + allChoices.map { it.isChecked.toString() }
        com.example.hiremydriver.recommendation.DriverMatchingSettings.hydrate(this) {
            val current = listOf(experience.text.toString(), rate.text.toString(), txtServiceArea.text.toString()) + allChoices.map { it.isChecked.toString() }
            if (!isFinishing && !isDestroyed && current == initial && savedInstanceState == null) {
                experience.setText(prefs.getString("experience", experience.text.toString()))
                rate.setText(prefs.getString("rate", ""))
                txtServiceArea.text = prefs.getString("area", "Select service areas")
                choices.forEach { it.isChecked = prefs.getBoolean(resources.getResourceEntryName(it.id), false) }
                languageChoices.forEach { (name, checkbox) -> checkbox.isChecked = name in prefs.getStringSet("languages", emptySet()).orEmpty() }
                refreshAll()
            }
        }
        btnSaveChanges.setOnClickListener {
            val expText = experience.text.toString().trim()
            val rateText = rate.text.toString().trim()
            val areaText = txtServiceArea.text.toString().trim()

            val data = mapOf("experience" to expText, "rate" to rateText.toLongOrNull(),
                "languages" to languageChoices.filter { it.second.isChecked }.map { it.first },
                "areas" to areaText.split(',').map(String::trim).filter(String::isNotBlank),
                "vehicles" to vehicleCards.filter { it.second.isChecked }.map { (_, checkbox) ->
                    when (checkbox.id) { R.id.chkSuv -> "SUV"; else -> resources.getResourceEntryName(checkbox.id).removePrefix("chk") }
                }, "transmissions" to listOf("Automatic" to chkAutomatic, "Manual" to chkManual).filter { it.second.isChecked }.map { it.first })
            val validation = DrivingInformation.error(data)
            if (validation != null) {
                if (expText.toIntOrNull() !in 0..80) experience.error = "Enter years from 0 to 80"
                if (rateText.toLongOrNull() !in 0..100000L) rate.error = "Enter a valid hourly rate"
                Toast.makeText(this, validation, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            prefs.edit().apply {
                putString("experience", expText)
                putString("rate", rateText)
                putString("area", areaText)
                putStringSet("languages", languageChoices.filter { it.second.isChecked }.map { it.first }.toSet())
                choices.forEach { putBoolean(resources.getResourceEntryName(it.id), it.isChecked) }
                apply()
            }

            // Keep experience synchronized in personal settings shared preferences as well
            getSharedPreferences(DriverAuth.preferenceName(this, DriverPersonalSettingsActivity.PREFERENCES), MODE_PRIVATE)
                .edit()
                .putString("experience", expText)
                .apply()

            com.example.hiremydriver.recommendation.DriverMatchingSettings.markDriving(this)
            btnSaveChanges.isEnabled = false
            com.example.hiremydriver.recommendation.DriverMatchingSettings.sync(this) { saved ->
                if (isFinishing || isDestroyed) return@sync
                btnSaveChanges.isEnabled = true
                if (saved) {
                    Toast.makeText(this, "Driving information saved", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this, "Changes kept on this phone. Could not save to Firebase; check your connection and try again.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("selected_service_areas", findViewById<TextView>(R.id.txtServiceArea).text.toString())
        super.onSaveInstanceState(outState)
    }
}
