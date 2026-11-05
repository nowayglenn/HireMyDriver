package com.example.hiremydriver

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CustomerPhotoCropTest {
    @Test fun zoomAndExtremeDraggingKeepPhotoCoveringEveryCropEdge() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = ApplicationProvider.getApplicationContext<Context>()
            for ((width, height) in listOf(800 to 400, 400 to 800)) {
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.GREEN)
                val view = CustomerPhotoCropView(context)
                view.layout(0, 0, 500, 650)
                view.setPhoto(bitmap)
                for (zoom in listOf(1f, 5f, 1f)) {
                    view.setZoom(zoom)
                    for (offset in listOf(-10000f, 10000f)) {
                        touch(view, MotionEvent.ACTION_DOWN, 250f, 325f)
                        touch(view, MotionEvent.ACTION_MOVE, offset, offset)
                        touch(view, MotionEvent.ACTION_UP, offset, offset)
                        val result = requireNotNull(view.croppedBitmap())
                        for ((x, y) in listOf(1 to 1, 766 to 1, 1 to 766, 766 to 766, 384 to 384)) {
                            assertEquals(Color.GREEN, result.getPixel(x, y))
                        }
                        result.recycle()
                    }
                }
            }
        }
    }

    @Test fun draggingSelectsTheIntendedPartAndSurvivesViewportResize() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val bitmap = Bitmap.createBitmap(800, 400, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).apply {
                drawColor(Color.RED)
                drawRect(400f, 0f, 800f, 400f, Paint().apply { color = Color.BLUE })
            }
            val view = CustomerPhotoCropView(context)
            view.layout(0, 0, 500, 650)
            view.setPhoto(bitmap)
            touch(view, MotionEvent.ACTION_DOWN, 250f, 325f)
            touch(view, MotionEvent.ACTION_MOVE, -10000f, 325f)
            touch(view, MotionEvent.ACTION_UP, -10000f, 325f)
            assertEquals(Color.BLUE, requireNotNull(view.croppedBitmap()).getPixel(384, 384))
            val recreated = CustomerPhotoCropView(context)
            recreated.setPhoto(bitmap, view.cropState())
            recreated.layout(0, 0, 650, 400)
            assertEquals(Color.BLUE, requireNotNull(recreated.croppedBitmap()).getPixel(384, 384))
        }
    }

    @Test fun confirmedCropUriCanBeDecodedByExistingProfilePhotoLoader() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.filesDir, "customer-profile-crops").apply { mkdirs() }
        val file = File.createTempFile("crop-test-", ".jpg", directory)
        try {
            val bitmap = Bitmap.createBitmap(768, 768, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.GREEN)
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it)) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.documents", file)
            assertEquals("content", uri.scheme)
            val decoded = requireNotNull(CustomerProfilePhoto.decode(context, uri))
            assertEquals(768, decoded.width)
            assertEquals(768, decoded.height)
        } finally { file.delete() }
    }

    private fun touch(view: CustomerPhotoCropView, action: Int, x: Float, y: Float) {
        MotionEvent.obtain(0, 0, action, x, y, 0).also {
            view.onTouchEvent(it)
            it.recycle()
        }
    }
}
