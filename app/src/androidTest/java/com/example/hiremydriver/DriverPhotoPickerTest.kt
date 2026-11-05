package com.example.hiremydriver

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DriverPhotoPickerTest {
    @Test fun onlyPencilOpensPickerThenSameCustomerEditorAndCancelKeepsStoredPhoto() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val saved = File(context.filesDir, "driver-profile-photo.jpg")
        val before = saved.takeIf(File::exists)?.readBytes()
        val folder = File(context.filesDir, "customer-profile-crops").apply { mkdirs() }
        val source = File.createTempFile("driver-picker-test-", ".jpg", folder)
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        bitmap.recycle()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.documents", source)
        var picks = 0
        var edits = 0
        var cancelPicker = false
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? = when {
                intent.action == Intent.ACTION_OPEN_DOCUMENT -> {
                    picks++
                    assertTrue(intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.contains("image/*") == true)
                    if (cancelPicker) Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
                    else Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri))
                }
                intent.component?.className == CustomerPhotoCropActivity::class.java.name -> {
                    edits++
                    assertEquals(uri, intent.data)
                    Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
                }
                else -> null
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            ActivityScenario.launch(DriverProfileActivity::class.java).use { scenario ->
                scenario.onActivity {
                    val avatar = it.findViewById<View>(R.id.imgDriverProfilePhoto)
                    assertFalse(avatar.isClickable)
                    avatar.performClick()
                    assertEquals(0, picks)
                }
                onView(withId(R.id.btnEditDriverPhoto)).perform(scrollTo(), click())
                instrumentation.waitForIdleSync()
                assertEquals(1, picks)
                assertEquals(1, edits)
                onView(withId(R.id.btnEditDriverPhoto)).check(matches(isEnabled()))
                cancelPicker = true
                onView(withId(R.id.btnEditDriverPhoto)).perform(click())
                instrumentation.waitForIdleSync()
                assertEquals(2, picks)
                assertEquals(1, edits)
                scenario.recreate()
                onView(withId(R.id.btnEditDriverPhoto)).check(matches(isEnabled()))
                assertArrayEquals(before, saved.takeIf(File::exists)?.readBytes())
            }
        } finally {
            instrumentation.removeMonitor(monitor)
            source.delete()
        }
    }
}
