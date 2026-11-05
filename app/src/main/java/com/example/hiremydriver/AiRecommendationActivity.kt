package com.example.hiremydriver

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
import androidx.core.location.LocationManagerCompat
import android.location.Location
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.example.hiremydriver.recommendation.RecommendationLocation
import com.example.hiremydriver.recommendation.RecommendationArea
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.example.hiremydriver.trip.LocalTripStore
import com.example.hiremydriver.trip.TripPlace
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AiRecommendationActivity : BaseActivity() {
    private var pickup: TripPlace? = null
    private var duration = 4
    private var vehicle = "SUV"
    private var transmission = "Any"
    private val start = Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Manila"))
    private var serviceArea: String? = null
    private var origin: com.example.hiremydriver.recommendation.Coordinates? = null
    private var manualArea = false
    private var openingResults = false
    private var permissionAttempted = false
    private var fix: Location? = null
    private lateinit var locationReader: com.example.hiremydriver.recommendation.CustomerServiceArea
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasLocationPermission()) detectLocation() else showLocationFallback()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_recommendation)
        locationReader = com.example.hiremydriver.recommendation.CustomerServiceArea(this)
        val areaPicker = ServiceAreaPicker(this)
        findViewById<View>(R.id.recommendationLocationText).setOnClickListener {
            com.example.hiremydriver.trip.BookingDialogs.choose(this, "Your location",
                arrayOf("Use current location", "Select city or municipality"), if (manualArea) 1 else 0) { choice ->
                if (choice == 0) { manualArea = false; retryLocation() }
                else areaPicker.show(serviceArea.orEmpty(), true) { area ->
                    if (area != "All Areas") {
                        locationReader.stop(); manualArea = true; serviceArea = area; origin = null
                        findViewById<TextView>(R.id.recommendationLocationText).text = area
                    }
                }
            }
        }
        if (savedInstanceState != null) {
            start.timeInMillis = savedInstanceState.getLong("recommend_start")
            duration = savedInstanceState.getInt("recommend_duration", 4)
            vehicle = savedInstanceState.getString("recommend_vehicle") ?: "SUV"
            if (vehicle !in listOf("Sedan", "SUV", "Van", "Pickup", "Truck")) vehicle = "SUV"
            transmission = savedInstanceState.getString("recommend_transmission") ?: "Any"
            permissionAttempted = savedInstanceState.getBoolean("recommend_permission_attempted")
            manualArea = savedInstanceState.getBoolean("manual_area")
            serviceArea = savedInstanceState.getString("service_area")
        } else {
            start.add(Calendar.DAY_OF_MONTH, 1)
            start.set(Calendar.HOUR_OF_DAY, 9); start.set(Calendar.MINUTE, 0)
            start.set(Calendar.SECOND, 0); start.set(Calendar.MILLISECOND, 0)
        }
        render()
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        serviceArea?.let { findViewById<TextView>(R.id.recommendationLocationText).text = it }
        findViewById<View>(R.id.containerDate).setOnClickListener {
            DatePickerDialog(this, { _, year, month, day ->
                start.set(year, month, day)
                render()
            }, start.get(Calendar.YEAR), start.get(Calendar.MONTH), start.get(Calendar.DAY_OF_MONTH)).apply {
                datePicker.minDate = System.currentTimeMillis()
            }.show()
        }
        findViewById<View>(R.id.containerTime).setOnClickListener {
            TimePickerDialog(this, { _, hour, minute ->
                start.set(Calendar.HOUR_OF_DAY, hour); start.set(Calendar.MINUTE, minute)
                render()
            }, start.get(Calendar.HOUR_OF_DAY), start.get(Calendar.MINUTE), false).show()
        }
        findViewById<View>(R.id.containerDuration).setOnClickListener {
            com.example.hiremydriver.trip.BookingDialogs.choose(this, "Select duration",
                (1..24).map { "$it hour(s)" }.toTypedArray(), duration - 1) { index ->
                    duration = index + 1; render()
                }
        }
        findViewById<View>(R.id.containerVehicle).setOnClickListener {
            val options = arrayOf("Sedan", "SUV", "Van", "Pickup", "Truck")
            com.example.hiremydriver.trip.BookingDialogs.choose(this, "Select vehicle type", options, options.indexOf(vehicle)) { index ->
                vehicle = options[index]; render()
            }
        }
        findViewById<View>(R.id.containerTransmission).setOnClickListener {
            val options = arrayOf("Any", "Manual", "Automatic")
            com.example.hiremydriver.trip.BookingDialogs.choose(this, getString(R.string.recommendation_transmission), options, options.indexOf(transmission)) { index ->
                transmission = options[index]; render()
            }
        }
        findViewById<View>(R.id.btnGetRecommendations).setOnClickListener {
            if (openingResults) return@setOnClickListener
            val area = serviceArea
            if (area.isNullOrBlank()) {
                Toast.makeText(this, "Check your saved profile address or allow location to find drivers in your area.", Toast.LENGTH_LONG).show()
                retryLocation()
                return@setOnClickListener
            }
            val now = System.currentTimeMillis()
            if (start.timeInMillis <= now) {
                Toast.makeText(this, R.string.recommendation_future_required, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            openingResults = true
            startActivity(Intent(this, AiResultsActivity::class.java).apply {
                putExtra("recommend_pickup", pickup?.let { LocalTripStore.placeJson(it).toString() })
                putExtra("recommend_area", area)
                origin?.let { putExtra("origin_lat", it.latitude); putExtra("origin_lng", it.longitude) }
                putExtra("recommend_start", start.timeInMillis)
                putExtra("recommend_duration", duration)
                putExtra("recommend_vehicle", vehicle)
                putExtra("recommend_transmission", transmission)
                putExtra("recommend_snapshot", now)
            })
        }
    }

    private fun retryLocation() {
        if (!hasLocationPermission() && permissionAttempted) {
            AlertDialog.Builder(this).setTitle(R.string.recommendation_your_area)
                .setMessage(R.string.recommendation_permission_help)
                .setPositiveButton("Allow location") { _, _ -> requestCurrentLocation() }
                .setNeutralButton("Open settings") { _, _ ->
                    openLocationSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", packageName, null)))
                }
                .setNegativeButton("Cancel", null).show()
        } else if (hasLocationPermission() && !LocationManagerCompat.isLocationEnabled(getSystemService(LOCATION_SERVICE) as LocationManager)) {
            openLocationSettings(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        } else requestCurrentLocation()
    }

    private fun openLocationSettings(settings: Intent) {
        try { startActivity(settings) }
        catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(this, R.string.permission_settings_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestCurrentLocation() {
        permissionAttempted = true
        if (hasLocationPermission()) detectLocation()
        else locationPermission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
    }

    private fun detectLocation() {
        if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) return
        if (manualArea) return
        findViewById<TextView>(R.id.recommendationLocationText).setText(R.string.recommendation_locating)
        locationReader.resolve { area, coords ->
            if (!isFinishing && !isDestroyed) {
                serviceArea = area; origin = coords
                findViewById<TextView>(R.id.recommendationLocationText).text = area ?: getString(R.string.recommendation_choose_area)
            }
        }
    }

    private fun showLocationFallback() {
        serviceArea = null
        findViewById<TextView>(R.id.recommendationLocationText).setText(R.string.recommendation_choose_area)
    }

    override fun onResume() {
        super.onResume()
        openingResults = false
        detectLocation()
    }

    override fun onStop() {
        locationReader.stop()
        super.onStop()
    }

    override fun onDestroy() {
        locationReader.close()
        super.onDestroy()
    }

    private fun render() {
        fun format(pattern: String) = SimpleDateFormat(pattern, Locale.getDefault()).apply {
            timeZone = java.util.TimeZone.getTimeZone("Asia/Manila")
        }.format(start.time)
        findViewById<TextView>(R.id.txtDate).text = format("MMM d, yyyy")
        findViewById<TextView>(R.id.txtTime).text = format("hh:mm a")
        findViewById<TextView>(R.id.txtDuration).text = if (duration == 1) "1 hour" else "$duration hours"
        findViewById<TextView>(R.id.txtVehicle).text = vehicle
        findViewById<TextView>(R.id.txtTransmission).text = transmission
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong("recommend_start", start.timeInMillis)
        outState.putInt("recommend_duration", duration)
        outState.putString("recommend_vehicle", vehicle)
        outState.putString("recommend_transmission", transmission)
        outState.putBoolean("recommend_permission_attempted", permissionAttempted)
        outState.putBoolean("manual_area", manualArea)
        outState.putString("service_area", serviceArea)
    }
}
