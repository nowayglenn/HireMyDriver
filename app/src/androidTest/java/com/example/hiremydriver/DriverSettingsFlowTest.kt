package com.example.hiremydriver

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DriverSettingsFlowTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private fun withRestoredSettings(name: String, test: (SharedPreferences) -> Unit) {
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val original = prefs.all.mapValues { it.value as String }
        try { test(prefs) }
        finally {
            prefs.edit().clear().apply {
                original.forEach { (key, value) -> putString(key, value) }
                commit()
            }
        }
    }

    @Test fun driverAndCustomerReviewsHideNavigation() {
        ActivityScenario.launch<CustomerReviewsActivity>(Intent(context, CustomerReviewsActivity::class.java)
            .putExtra("DRIVER_REVIEW", true)).use {
            onView(withId(R.id.bottomNavigation)).check(matches(withEffectiveVisibility(Visibility.GONE)))
        }
        ActivityScenario.launch(CustomerReviewsActivity::class.java).use {
            onView(withId(R.id.bottomNavigation)).check(matches(withEffectiveVisibility(Visibility.GONE)))
        }
    }

    @Test fun personalSettingsSaveAndReturnToProfileWithoutRegistrationSteps() {
        withRestoredSettings(DriverPersonalSettingsActivity.PREFERENCES) { prefs ->
            prefs.edit().putString("name", "Settings test driver").putString("email", "driver@example.com").commit()
            ActivityScenario.launch(DriverProfileActivity::class.java).use {
                onView(withId(R.id.rowPersonalInfo)).perform(scrollTo(), click())
                onView(withId(R.id.settingsTitle)).check(matches(withText("Personal Information")))
                onView(withId(R.id.stepContainer)).check(doesNotExist())
                onView(withId(R.id.settingsName)).perform(scrollTo(), replaceText("Updated test driver"), closeSoftKeyboard())
                onView(withId(R.id.btnSettingsSave)).perform(click())
                onView(withId(R.id.txtDriverName)).check(matches(withText("Updated test driver")))
                onView(withId(R.id.rowPersonalInfo)).perform(scrollTo(), click())
                onView(withId(R.id.settingsName)).check(matches(withText("Updated test driver")))
                onView(withId(R.id.settingsName)).perform(scrollTo(), replaceText("Discard this"), closeSoftKeyboard())
                onView(withId(R.id.btnSettingsBack)).perform(click())
                assertEquals("Updated test driver", prefs.getString("name", null))
            }
        }
    }

    @Test fun documentsAreStrictlyViewOnlyAndRetainSavedDetails() {
        withRestoredSettings(DriverDocumentsSettingsActivity.PREFERENCES) { prefs ->
            prefs.edit().clear().putString("licenseNumber", "TEST-LICENSE")
                .putString("licenseExpiry", "1/1/2030").putString("clearanceIssue", "1/1/2026")
                .putString("clearanceExpiry", "7/1/2026").commit()
            val before = prefs.all.toMap()
            ActivityScenario.launch(DriverDocumentsSettingsActivity::class.java).use { screen ->
                onView(withText("Add photo")).check(doesNotExist())
                onView(withText("Replace photo")).check(doesNotExist())
                onView(withId(R.id.btnSettingsSave)).check(doesNotExist())
                screen.onActivity { activity ->
                    listOf(R.id.settingsLicenseNumber, R.id.settingsLicenseExpiry,
                        R.id.settingsClearanceIssue, R.id.settingsClearanceExpiry).forEach { id ->
                        val field = activity.findViewById<android.widget.EditText>(id)
                        org.junit.Assert.assertNull(field.keyListener)
                        org.junit.Assert.assertFalse(field.isFocusable)
                        org.junit.Assert.assertFalse(field.hasOnClickListeners())
                        org.junit.Assert.assertFalse(field.performClick())
                    }
                }
                screen.recreate()
                onView(withId(R.id.settingsLicenseNumber)).check(matches(withText("TEST-LICENSE")))
                onView(withId(R.id.settingsClearanceIssue)).perform(scrollTo())
                    .check(matches(withText("1/1/2026")))
                onView(withId(R.id.settingsClearanceExpiry)).perform(scrollTo())
                    .check(matches(withText("7/1/2026")))
                onView(withId(R.id.btnSettingsBack)).perform(click())
                assertEquals(before, prefs.all)
            }
        }
    }
    @Test fun submittedPhotosOpenInReadOnlyViewerAndMissingDatesStayVisible() {
        withRestoredSettings(DriverDocumentsSettingsActivity.PREFERENCES) { prefs ->
            val files = mutableListOf<java.io.File>()
            try {
                val documents = listOf(
                    Triple("front", R.id.settingsLicenseFrontPreview, "License front"),
                    Triple("back", R.id.settingsLicenseBackPreview, "License back"),
                    Triple("police", R.id.settingsPolicePreview, "Police clearance")
                )
                val editor = prefs.edit().clear()
                documents.forEachIndexed { index, (key, _, _) ->
                    val file = java.io.File.createTempFile("viewer-test-", ".png", context.cacheDir)
                    files.add(file)
                    val bitmap = android.graphics.Bitmap.createBitmap(100 + index, 60, android.graphics.Bitmap.Config.ARGB_8888)
                    file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                    editor.putString(key, android.net.Uri.fromFile(file).toString())
                }
                editor.commit()
                val before = prefs.all.toMap()
                ActivityScenario.launch(DriverDocumentsSettingsActivity::class.java).use {
                    onView(withId(R.id.settingsClearanceIssue)).perform(scrollTo())
                        .check(matches(withText(R.string.driver_document_not_provided)))
                    onView(withId(R.id.settingsClearanceExpiry)).perform(scrollTo())
                        .check(matches(withText(R.string.driver_document_not_provided)))
                    documents.forEachIndexed { index, (_, image, title) ->
                        onView(withId(image)).perform(scrollTo(), click())
                        onView(withId(R.id.documentViewerTitle)).check(matches(withText(title)))
                        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                        val deadline = android.os.SystemClock.uptimeMillis() + 5000
                        var loaded = false
                        while (!loaded && android.os.SystemClock.uptimeMillis() < deadline) {
                            instrumentation.runOnMainSync {
                                val viewer = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                                    .filterIsInstance<DriverDocumentViewerActivity>().firstOrNull()
                                val photo = viewer?.findViewById<android.widget.ImageView>(R.id.documentViewerImage)
                                    ?.drawable as? android.graphics.drawable.BitmapDrawable
                                loaded = photo?.bitmap?.width == 100 + index
                            }
                            if (!loaded) Thread.sleep(50)
                        }
                        assertTrue("Viewer must show the selected document", loaded)
                        onView(withId(R.id.documentViewerClose)).perform(click())
                        assertEquals(before, prefs.all)
                    }
                }
            } finally { files.forEach { it.delete() } }
        }
    }

    @Test fun vehicleCardAndCheckboxSelectionsStayInSync() {
        ActivityScenario.launch(DriverDrivingInfoActivity::class.java).use {
            onView(withId(R.id.cardAll)).perform(scrollTo(), click())
            listOf(R.id.chkSedan, R.id.chkSuv, R.id.chkVan, R.id.chkPickup, R.id.chkTruck).forEach {
                onView(withId(it)).check(matches(isChecked()))
            }
            onView(withId(R.id.chkSedan)).perform(scrollTo(), click())
            onView(withId(R.id.chkSedan)).check(matches(isNotChecked()))
            onView(withId(R.id.chkAll)).check(matches(isNotChecked()))
            onView(withId(R.id.cardSedan)).perform(scrollTo(), click())
            onView(withId(R.id.chkSedan)).check(matches(isChecked()))
            onView(withId(R.id.chkAll)).check(matches(isChecked()))
        }
    }
}
