package com.example.hiremydriver.maps

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.GeomagneticField
import android.location.Location
import android.view.Surface

/** Foreground compass heading, corrected from magnetic north to map (true) north. */
internal class PhoneHeading(private val activity: Activity, private val changed: (Float?) -> Unit) : SensorEventListener {
    private val manager = activity.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotation = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: manager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
    private val gravity = FloatArray(3)
    private val magnetic = FloatArray(3)
    private val matrix = FloatArray(9)
    private val remapped = FloatArray(9)
    private val angles = FloatArray(3)
    private var gravityReady = false
    private var magneticReady = false
    private var declination = 0f
    private var filtered: Float? = null
    private var started = false

    fun locate(location: Location) {
        declination = GeomagneticField(location.latitude.toFloat(), location.longitude.toFloat(),
            location.altitude.toFloat(), location.time).declination
    }

    fun start() {
        if (started) return
        started = true
        if (rotation != null) manager.registerListener(this, rotation, SensorManager.SENSOR_DELAY_UI)
        else {
            val accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val magnetometer = manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            if (accelerometer != null && magnetometer != null) {
                manager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
                manager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI)
            }
        }
    }

    fun stop() {
        manager.unregisterListener(this)
        started = false
        gravityReady = false
        magneticReady = false
        filtered = null
        changed(null)
    }

    @Suppress("DEPRECATION")
    override fun onSensorChanged(event: SensorEvent) {
        if (event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            filtered = null
            changed(null)
            return
        }
        if (rotation != null) SensorManager.getRotationMatrixFromVector(matrix, event.values)
        else {
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                event.values.copyInto(gravity, endIndex = 3); gravityReady = true
            } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                event.values.copyInto(magnetic, endIndex = 3); magneticReady = true
            }
            if (!gravityReady || !magneticReady || !SensorManager.getRotationMatrix(matrix, null, gravity, magnetic)) return
        }
        val axes = when (activity.windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        SensorManager.remapCoordinateSystem(matrix, axes.first, axes.second, remapped)
        SensorManager.getOrientation(remapped, angles)
        val heading = NavigationFix.normalize(Math.toDegrees(angles[0].toDouble()).toFloat() + declination)
        filtered = filtered?.let { NavigationFix.interpolateHeading(it, heading, 0.2f) } ?: heading
        changed(filtered)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) { filtered = null; changed(null) }
    }
}
