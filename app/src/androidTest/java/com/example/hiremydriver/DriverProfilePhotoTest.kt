package com.example.hiremydriver

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.util.AtomicFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DriverProfilePhotoTest {
    @Test fun savedPhotoRemainsReadableAfterTheCropSourceIsRemoved() = withPhotoDirectory { context, directory ->
        val source = createPhoto(directory, "crop.jpg", Color.GREEN)
        DriverProfilePhoto.saveToFile(context, Uri.fromFile(source)).recycle()
        assertTrue(source.delete())

        val restored = requireNotNull(DriverProfilePhoto.readStored(context))
        try {
            assertEquals(64, restored.width)
            assertEquals(64, restored.height)
            assertTrue(Color.green(restored.getPixel(32, 32)) > 240)
            assertTrue(Color.red(restored.getPixel(32, 32)) < 15)
        } finally { restored.recycle() }
    }

    @Test fun invalidAndMissingImagesLeaveThePreviousPhotoUnchanged() = withPhotoDirectory { context, directory ->
        val original = createPhoto(directory, "original.jpg", Color.GREEN)
        DriverProfilePhoto.saveToFile(context, Uri.fromFile(original)).recycle()
        val saved = File(directory, "driver-profile-photo.jpg")
        val previous = saved.readBytes()
        val invalid = File(directory, "invalid.jpg").apply { writeText("This is not an image") }

        for (source in listOf(invalid, File(directory, "missing.jpg"))) {
            assertTrue(runCatching { DriverProfilePhoto.saveToFile(context, Uri.fromFile(source)) }.isFailure)
            assertArrayEquals(previous, AtomicFile(saved).readFully())
        }
    }

    @Test fun interruptedWriteKeepsThePreviousConfirmedPhoto() = withPhotoDirectory { context, directory ->
        val original = createPhoto(directory, "original.jpg", Color.GREEN)
        DriverProfilePhoto.saveToFile(context, Uri.fromFile(original)).recycle()
        val saved = File(directory, "driver-profile-photo.jpg")
        val previous = saved.readBytes()
        val replacement = createPhoto(directory, "replacement.jpg", Color.BLUE)
        val failingDestination = object : AtomicFile(saved) {
            private var stream: FileOutputStream? = null
            override fun startWrite(): FileOutputStream {
                val real = super.startWrite()
                stream = real
                return object : FileOutputStream(real.fd) {
                    override fun write(bytes: ByteArray) {
                        super.write(bytes, 0, bytes.size / 2)
                        throw IOException("Simulated interrupted photo write")
                    }
                }
            }
            override fun failWrite(output: FileOutputStream?) {
                super.failWrite(output)
                stream?.close()
                stream = null
            }
        }

        assertTrue(runCatching {
            DriverProfilePhoto.saveToFile(context, Uri.fromFile(replacement), failingDestination)
        }.isFailure)
        assertArrayEquals(previous, AtomicFile(saved).readFully())
        requireNotNull(DriverProfilePhoto.readStored(context)).recycle()
    }

    private fun createPhoto(directory: File, name: String, color: Int): File {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        return File(directory, name).also { file ->
            try {
                file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it)) }
            } finally { bitmap.recycle() }
        }
    }

    private fun withPhotoDirectory(test: (Context, File) -> Unit) {
        val application = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(application.cacheDir, "driver-photo-test-${UUID.randomUUID()}")
        assertTrue(directory.mkdirs())
        val context = object : ContextWrapper(application) {
            override fun getFilesDir(): File = directory
        }
        try { test(context, directory) }
        finally { directory.deleteRecursively() }
    }
}
