package com.example.hiremydriver

import android.app.Application

/** The launcher owns the splash; returning to an existing screen never launches another. */
class HireMyDriverApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
    }
    fun beginPhotoSelection() = Unit
    fun endPhotoSelection() = Unit
}
