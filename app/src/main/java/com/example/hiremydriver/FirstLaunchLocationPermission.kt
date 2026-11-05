package com.example.hiremydriver

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import java.io.File

/** One Android permission prompt per installation, shared by both role entry paths. */
class FirstLaunchLocationPermission(
    private val activity: AppCompatActivity,
    private val onFinished: () -> Unit
) {
    // This marker must not be restored by Android backup onto a fresh installation.
    private val marker get() = File(activity.noBackupFilesDir, "location_permission_presented")
    private var pending = false
    private val permission = activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        pending = false
        activity.window.decorView.post { onFinished() }
    }

    /** Returns true while the system location dialog is taking the user's decision. */
    fun requestIfNeeded(): Boolean {
        if (pending) return true
        if (marker.exists() || activity.isFinishing ||
            !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return false
        marker.parentFile?.mkdirs()
        runCatching { marker.createNewFile() }
        val allowed = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED }
        if (!allowed) {
            pending = true
            permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
        // Denial remains a valid choice. Map/recommendation screens retain their existing retry flows.
        return pending
    }
}
