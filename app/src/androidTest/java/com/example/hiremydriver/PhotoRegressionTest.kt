package com.example.hiremydriver

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.widget.ImageView
import android.view.View
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PhotoRegressionTest {
    @Test fun directoryDecodesRealLargeThumbnailRatherThanDefaultAvatar() {
        val c = ApplicationProvider.getApplicationContext<Context>()
        val bitmap = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }
        val thumbnail = RegisteredDriverDirectory.thumbnail(bitmap)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val image = ImageView(c)
            RegisteredDriverDirectory.image(image, thumbnail)
            val decoded = (image.drawable as BitmapDrawable).bitmap
            assertTrue(decoded.width <= 320)
            val pixel = decoded.getPixel(decoded.width / 2, decoded.height / 2)
            assertTrue(Color.red(pixel) > 220 && Color.blue(pixel) > 220 && Color.green(pixel) < 30)
        }
        bitmap.recycle()
    }
    @Test fun cancelCropFinishesWithoutOpeningSplash() {
        val c = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(c.filesDir, "customer-profile-crops").apply { mkdirs() }
        val file = File.createTempFile("crop-regression-", ".jpg", directory)
        val photo = Bitmap.createBitmap(200, 300, Bitmap.Config.ARGB_8888)
        file.outputStream().use { photo.compress(Bitmap.CompressFormat.JPEG, 90, it) }; photo.recycle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(SplashActivity::class.java.name, null, false)
        try {
            val uri = FileProvider.getUriForFile(c, c.packageName + ".documents", file)
            ActivityScenario.launch<CustomerPhotoCropActivity>(Intent(c, CustomerPhotoCropActivity::class.java).setData(uri)).use { screen ->
                screen.onActivity { it.findViewById<View>(R.id.photoCropCancel).performClick(); assertTrue(it.isFinishing) }
            }
            instrumentation.waitForIdleSync()
            assertEquals(0, monitor.hits)
        } finally { instrumentation.removeMonitor(monitor); file.delete() }
    }
}
