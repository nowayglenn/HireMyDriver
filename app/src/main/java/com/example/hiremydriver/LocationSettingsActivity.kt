package com.example.hiremydriver

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.material.materialswitch.MaterialSwitch

class LocationSettingsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_location_settings)
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnOpenSystemSettings).setOnClickListener { openAppSettings() }
        findViewById<MaterialSwitch>(R.id.switchPreciseLocation).setOnClickListener {
            // Android owns the permission. Never leave a visual toggle pretending it changed.
            updatePermissionStatus()
            openAppSettings()
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    private fun openAppSettings() {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.permission_settings_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun updatePermissionStatus() {
        val precise = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val approximate = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val state = LocationPermissionState.from(precise, approximate)
        val allowed = state != LocationPermissionState.DENIED
        findViewById<TextView>(R.id.txtPermissionStatus).setText(when (state) {
            LocationPermissionState.PRECISE -> R.string.permission_precise_granted
            LocationPermissionState.APPROXIMATE -> R.string.permission_approximate_granted
            LocationPermissionState.DENIED -> R.string.status_denied
        })
        findViewById<ImageView>(R.id.imgStatusIcon).apply {
            setImageResource(R.drawable.ic_location)
            imageTintList = ContextCompat.getColorStateList(this@LocationSettingsActivity,
                if (allowed) R.color.primary_blue else R.color.text_secondary)
            setBackgroundResource(if (allowed) R.drawable.bg_circle_blue else R.drawable.bg_circle_gray)
        }
        findViewById<MaterialSwitch>(R.id.switchPreciseLocation).isChecked = precise
        val gpsEnabled = LocationManagerCompat.isLocationEnabled(getSystemService(LOCATION_SERVICE) as LocationManager)
        findViewById<View>(R.id.txtDeviceLocationOff).visibility = if (gpsEnabled) View.GONE else View.VISIBLE
    }
}
