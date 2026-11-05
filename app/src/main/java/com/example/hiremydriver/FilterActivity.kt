package com.example.hiremydriver

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

class FilterActivity : BaseActivity() {

    private lateinit var expChips: List<TextView>
    private lateinit var vehicleBtns: List<LinearLayout>
    private lateinit var ratingChips: List<TextView>
    
    private lateinit var txtServiceArea: TextView
    private lateinit var txtAvailability: TextView

    private lateinit var areaPicker: ServiceAreaPicker
    private var criteria = DriverFilterCriteria()
    private val availabilityDays = arrayOf("Any Day", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_filter)
        areaPicker = ServiceAreaPicker(this)
        criteria = DriverFilterStore.read(this)

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        btnBack.setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        txtServiceArea = findViewById(R.id.txtServiceArea)
        txtAvailability = findViewById(R.id.txtAvailability)

        // --- EXPERIENCE ---
        expChips = listOf(
            findViewById(R.id.chipExpAll),
            findViewById(R.id.chipExp1to3),
            findViewById(R.id.chipExp3to5),
            findViewById(R.id.chipExp5plus)
        )
        expChips.forEach { chip ->
            chip.setOnClickListener { selectExpChip(chip) }
        }

        // --- VEHICLE TYPE ---
        vehicleBtns = listOf(
            findViewById(R.id.btnVehicleAll),
            findViewById(R.id.btnVehicleSedan),
            findViewById(R.id.btnVehicleSUV),
            findViewById(R.id.btnVehicleVan),
            findViewById(R.id.btnVehiclePickup)
        )
        val pickup = vehicleBtns.last()
        val truck = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(pickup.layoutParams as LinearLayout.LayoutParams)
            isClickable = true; isFocusable = true; contentDescription = "Truck"
            addView(ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(pickup.getChildAt(0).layoutParams as LinearLayout.LayoutParams)
                val source = pickup.getChildAt(0)
                setPadding(source.paddingLeft, source.paddingTop, source.paddingRight, source.paddingBottom)
                setImageResource(R.drawable.ic_vehicle); setBackgroundResource(R.drawable.bg_filter_chip)
            })
            addView(TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(pickup.getChildAt(1).layoutParams as LinearLayout.LayoutParams)
                text = "Truck"; textSize = 13f
            })
        }
        (pickup.parent as LinearLayout).addView(truck)
        vehicleBtns = vehicleBtns + truck
        vehicleBtns.forEach { btn ->
            btn.setOnClickListener { selectVehicleBtn(btn) }
        }

        // --- DROPDOWNS ---
        findViewById<LinearLayout>(R.id.btnServiceArea).setOnClickListener {
            showAreaPicker()
        }
        findViewById<LinearLayout>(R.id.btnAvailability).setOnClickListener {
            showAvailabilityPicker()
        }

        // --- RATING ---
        ratingChips = listOf(
            findViewById(R.id.chipRatingAll),
            findViewById(R.id.chipRating4),
            findViewById(R.id.chipRating3),
            findViewById(R.id.chipRating2),
            findViewById(R.id.chipRating1)
        )
        ratingChips.forEach { chip ->
            chip.setOnClickListener { selectRatingChip(chip) }
        }
        val saved = criteria
        selectExpChip(expChips[saved.experience.coerceIn(0, 3)])
        selectVehicleBtn(vehicleBtns.firstOrNull { (it.getChildAt(1) as TextView).text.toString().equals(saved.vehicle, true) } ?: vehicleBtns.first())
        selectRatingChip(ratingChips[listOf(0, 4, 3, 2, 1).indexOf(saved.minimumRating).coerceAtLeast(0)])
        txtServiceArea.text = if (saved.area == "All Areas") "Select area" else saved.area
        txtAvailability.text = availabilityDays[saved.day.coerceIn(0, 7)]

        // --- BOTTOM BUTTONS ---
        val btnApplyFilters = findViewById<MaterialButton>(R.id.btnApplyFilters)
        btnApplyFilters.setOnClickListener {
            DriverFilterStore.save(this, criteria)
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        val btnClearAll = findViewById<TextView>(R.id.btnClearAll)
        btnClearAll.setOnClickListener {
            resetFilters()
        }
    }

    private fun showAreaPicker() {
        areaPicker.show(criteria.area, true) { area ->
            criteria = criteria.copy(area = area)
            txtServiceArea.text = area
            txtServiceArea.setTextColor(ColorUtils.parseColor("#061B41"))
            txtServiceArea.setTypeface(null, android.graphics.Typeface.BOLD)
        }
    }

    private fun showAvailabilityPicker() {
        com.example.hiremydriver.trip.BookingDialogs.choose(this, "Select Available Day", availabilityDays, criteria.day) { which ->
            criteria = criteria.copy(day = which)
            txtAvailability.text = availabilityDays[which]
            txtAvailability.setTextColor(ColorUtils.parseColor("#061B41"))
            txtAvailability.setTypeface(null, android.graphics.Typeface.BOLD)
        }
    }

    private fun selectExpChip(selected: TextView) {
        criteria = criteria.copy(experience = expChips.indexOf(selected))
        expChips.forEach { chip ->
            if (chip == selected) {
                chip.setBackgroundResource(R.drawable.bg_filter_chip_active)
                chip.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            } else {
                chip.setBackgroundResource(R.drawable.bg_filter_chip)
                chip.setTextColor(ColorUtils.parseColor("#4B5563"))
            }
        }
    }

    private fun selectVehicleBtn(selected: LinearLayout) {
        criteria = criteria.copy(vehicle = if (selected == vehicleBtns.first()) "All" else (selected.getChildAt(1) as TextView).text.toString())
        vehicleBtns.forEach { btn ->
            val img = btn.getChildAt(0) as ImageView
            val txt = btn.getChildAt(1) as TextView
            
            if (btn == selected) {
                img.setBackgroundResource(R.drawable.bg_filter_chip_active)
                img.imageTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, android.R.color.white))
                txt.setTextColor(ColorUtils.parseColor("#FF7900"))
                txt.setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                img.setBackgroundResource(R.drawable.bg_filter_chip)
                img.imageTintList = android.content.res.ColorStateList.valueOf(ColorUtils.parseColor("#6B7280"))
                txt.setTextColor(ColorUtils.parseColor("#4B5563"))
                txt.setTypeface(null, android.graphics.Typeface.NORMAL)
            }
        }
    }

    private fun selectRatingChip(selected: TextView) {
        criteria = criteria.copy(minimumRating = listOf(0, 4, 3, 2, 1)[ratingChips.indexOf(selected)])
        ratingChips.forEach { chip ->
            if (chip == selected) {
                chip.setBackgroundResource(R.drawable.bg_filter_chip)
                chip.setTextColor(ColorUtils.parseColor("#FF7900"))
                chip.setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                chip.setBackgroundResource(R.drawable.bg_filter_chip)
                chip.setTextColor(ColorUtils.parseColor("#4B5563"))
                chip.setTypeface(null, android.graphics.Typeface.NORMAL)
            }
        }
    }

    private fun resetFilters() {
        criteria = DriverFilterCriteria()
        selectExpChip(expChips[0])
        selectVehicleBtn(vehicleBtns[0])
        selectRatingChip(ratingChips[0])
        
        txtServiceArea.text = "Select area"
        txtServiceArea.setTextColor(ColorUtils.parseColor("#6B7280"))
        txtServiceArea.setTypeface(null, android.graphics.Typeface.NORMAL)
        
        txtAvailability.text = "Select available day"
        txtAvailability.setTextColor(ColorUtils.parseColor("#6B7280"))
        txtAvailability.setTypeface(null, android.graphics.Typeface.NORMAL)

        Toast.makeText(this, "Filters Reset", Toast.LENGTH_SHORT).show()
    }

    // Small helper for colors if they are hex strings
    object ColorUtils {
        fun parseColor(color: String): Int = android.graphics.Color.parseColor(color)
    }
}
