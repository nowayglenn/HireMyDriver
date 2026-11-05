package com.example.hiremydriver

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LicenseScanReturnTest {
    @Test fun removingAndRetakingOneSideKeepsTheOtherPhoto() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(context.filesDir, "registration_documents").apply { mkdirs() }
        val files = mutableListOf<File>()
        fun photo(color: Int): String {
            val file = File.createTempFile("retake-regression-", ".jpg", directory).also { files.add(it) }
            val bitmap = Bitmap.createBitmap(400, 250, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bitmap.recycle()
            return FileProvider.getUriForFile(context, "${context.packageName}.documents", file).toString()
        }
        val front = photo(Color.WHITE)
        val back = photo(Color.LTGRAY)
        val replacement = photo(Color.DKGRAY)
        var requestedSide: String? = null
        var requestedBoth = true
        var nextResult = Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.component?.className != LicenseCameraActivity::class.java.name) return null
                requestedSide = intent.getStringExtra(LicenseCameraActivity.SIDE)
                requestedBoth = intent.getBooleanExtra(LicenseCameraActivity.BOTH, true)
                return nextResult
            }
        }
        instrumentation.addMonitor(monitor)
        val draft = Bundle().apply {
            putString("licenseFront", front); putString("licenseBack", back)
            putString("licenseNumber", "MANUAL-NUMBER"); putString("licenseExpiration", "1/1/2030")
        }
        try {
            ActivityScenario.launch<LicenseInfoActivity>(Intent(context, LicenseInfoActivity::class.java)
                .putExtra(RegistrationStepActivity.DRAFT, draft)).use { scenario ->
                fun awaitPhotos() {
                    val deadline = SystemClock.uptimeMillis() + 10000
                    var ready = false
                    while (!ready && SystemClock.uptimeMillis() < deadline) {
                        scenario.onActivity { screen ->
                            ready = screen.findViewById<ImageView>(R.id.imgLicenseFront).drawable != null &&
                                screen.findViewById<ImageView>(R.id.imgLicenseBack).drawable != null
                        }
                        if (!ready) SystemClock.sleep(100)
                    }
                    assertTrue("Both saved photos should be displayed", ready)
                }
                awaitPhotos()
                for (isFront in listOf(true, false)) {
                    val key = if (isFront) "licenseFront" else "licenseBack"
                    val photoId = if (isFront) R.id.imgLicenseFront else R.id.imgLicenseBack
                    val otherId = if (isFront) R.id.imgLicenseBack else R.id.imgLicenseFront
                    val retakeId = if (isFront) R.id.retakeLicenseFront else R.id.retakeLicenseBack
                    val removeId = if (isFront) R.id.removeLicenseFront else R.id.removeLicenseBack
                    // Cancelling a retake retains both previously accepted images.
                    var original: android.graphics.drawable.Drawable? = null
                    var other: android.graphics.drawable.Drawable? = null
                    scenario.onActivity {
                        original = it.findViewById<ImageView>(photoId).drawable
                        other = it.findViewById<ImageView>(otherId).drawable
                    }
                    nextResult = Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
                    onView(withId(retakeId)).perform(scrollTo(), click())
                    assertEquals(key, requestedSide)
                    assertFalse(requestedBoth)
                    scenario.onActivity {
                        assertSame(original, it.findViewById<ImageView>(photoId).drawable)
                        assertSame(other, it.findViewById<ImageView>(otherId).drawable)
                    }
                    onView(withId(removeId)).perform(scrollTo(), click())
                    onView(withId(photoId)).check(matches(withEffectiveVisibility(Visibility.GONE)))
                    onView(withId(removeId)).check(matches(withEffectiveVisibility(Visibility.GONE)))
                    scenario.onActivity {
                        assertNull(it.findViewById<ImageView>(photoId).drawable)
                        assertSame(other, it.findViewById<ImageView>(otherId).drawable)
                    }
                    // A removed side stays removed across recreation and offers its own scan button.
                    scenario.recreate()
                    onView(withId(photoId)).check(matches(withEffectiveVisibility(Visibility.GONE)))
                    onView(withId(retakeId)).check(matches(withText(
                        if (isFront) R.string.license_scan_front else R.string.license_scan_back)))
                    nextResult = Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().putExtra(key, replacement))
                    onView(withId(retakeId)).perform(scrollTo(), click())
                    assertEquals(key, requestedSide)
                    assertFalse(requestedBoth)
                    awaitPhotos()
                    onView(withId(removeId)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
                    onView(withId(R.id.edtLicenseNumber)).check(matches(withText("MANUAL-NUMBER")))
                    onView(withId(R.id.edtLicenseExpiration)).check(matches(withText("1/1/2030")))
                }
                onView(withId(R.id.removeLicenseFront)).perform(scrollTo(), click())
                onView(withId(R.id.removeLicenseBack)).perform(scrollTo(), click())
                scenario.recreate()
                onView(withId(R.id.licenseFrontPreview)).check(matches(withEffectiveVisibility(Visibility.GONE)))
                onView(withId(R.id.licenseBackPreview)).check(matches(withEffectiveVisibility(Visibility.GONE)))
                onView(withId(R.id.retakeLicenseFront)).check(matches(withEffectiveVisibility(Visibility.GONE)))
                onView(withId(R.id.retakeLicenseBack)).check(matches(withEffectiveVisibility(Visibility.GONE)))
                nextResult = Instrumentation.ActivityResult(Activity.RESULT_OK, Intent()
                    .putExtra("licenseFront", front).putExtra("licenseBack", back))
                onView(withId(R.id.btnScanLicense)).perform(scrollTo(), click())
                assertTrue(requestedBoth)
                awaitPhotos()
            }
        } finally {
            instrumentation.removeMonitor(monitor)
            files.forEach { it.delete() }
        }
    }

    @Test fun acceptingBothPhotosStaysOnRequirementsAndKeepsEnteredFields() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(context.filesDir, "registration_documents").apply { mkdirs() }
        val file = File.createTempFile("scan-regression-", ".jpg", directory)
        val bitmap = Bitmap.createBitmap(800, 500, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.documents", file).toString()
        val monitor = instrumentation.addMonitor(LicenseCameraActivity::class.java.name,
            Instrumentation.ActivityResult(Activity.RESULT_OK, Intent()
                .putExtra("licenseFront", uri).putExtra("licenseBack", uri)), true)
        val draft = Bundle().apply {
            putString("birthDate", "1/1/1990"); putString("address", "Test address")
            putString("experience", "3"); putString("bio", "Test driver")
            putString("licenseNumber", "MANUAL-NUMBER"); putString("licenseExpiration", "1/1/2030")
        }
        try {
            ActivityScenario.launch<PersonalInfoActivity>(Intent(context, PersonalInfoActivity::class.java)
                .putExtra(RegistrationStepActivity.DRAFT, draft)).use {
                onView(withId(R.id.btnPersonalNext)).perform(scrollTo(), click())
                onView(withId(R.id.licenseFrontPreview)).check(matches(withEffectiveVisibility(Visibility.GONE)))
                onView(withId(R.id.btnScanLicense)).perform(scrollTo(), click())
                assertEquals(1, monitor.hits)
                val deadline = SystemClock.uptimeMillis() + 10000
                var complete = false
                while (!complete && SystemClock.uptimeMillis() < deadline) {
                    instrumentation.runOnMainSync {
                        val screen = ActivityLifecycleMonitorRegistry.getInstance()
                            .getActivitiesInStage(Stage.RESUMED).filterIsInstance<LicenseInfoActivity>().singleOrNull()
                        complete = screen != null && !screen.isFinishing &&
                            screen.findViewById<ImageView>(R.id.imgLicenseFront).drawable != null &&
                            screen.findViewById<ImageView>(R.id.imgLicenseBack).drawable != null
                    }
                    if (!complete) SystemClock.sleep(100)
                }
                assertTrue("Both photos should appear on Requirements after accepting the scan", complete)
                onView(withId(R.id.edtLicenseNumber)).check(matches(withText("MANUAL-NUMBER")))
                onView(withId(R.id.edtLicenseExpiration)).check(matches(withText("1/1/2030")))
                // Going back while OCR is pending must also avoid the former status lookup crash.
                onView(withId(R.id.btnLicenseBack)).perform(click())
                onView(withId(R.id.edtAddress)).check(matches(withText("Test address")))
            }
        } finally {
            instrumentation.removeMonitor(monitor)
            instrumentation.runOnMainSync {
                Stage.values().flatMap { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it).toList() }
                    .filter { it is LicenseInfoActivity || it is PersonalInfoActivity }.distinct().forEach { it.finish() }
            }
            file.delete()
        }
    }

    @Test fun cameraFrameMasksCornersWithoutCoveringPreviewCenter() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val frame = LicenseCameraFrame(context, null)
            frame.addView(View(context).apply { setBackgroundColor(Color.WHITE) })
            val spec = View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY)
            frame.measure(spec, spec); frame.layout(0, 0, 300, 300)
            val image = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
            frame.draw(Canvas(image))
            assertEquals(0xff07162b.toInt(), image.getPixel(0, 0))
            assertEquals(Color.WHITE, image.getPixel(150, 150))
            image.recycle()
        }
    }
}
