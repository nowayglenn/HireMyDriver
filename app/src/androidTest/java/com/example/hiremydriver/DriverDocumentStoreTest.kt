package com.example.hiremydriver

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DriverDocumentStoreTest {
    private fun isolated(test: (Context, File) -> Unit) {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val name = "document-test-${UUID.randomUUID()}"
        val folder = File(base.cacheDir, name).apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getFilesDir() = folder
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                base.getSharedPreferences("$name-${folder.name}", mode)
        }
        try { test(context, folder) }
        finally {
            base.deleteSharedPreferences("${DriverDocumentStore.PREFERENCES}-$name")
            folder.deleteRecursively()
        }
    }

    @Test fun submittedDocumentsSurviveRemovalOfOriginalUploadsAndKeepManualDates() = isolated { context, folder ->
        val sources = DriverDocumentStore.photoKeys.keys.associateWith { key ->
            File(folder, "$key.jpg").apply { writeBytes("original-$key".toByteArray()) }
        }
        val draft = Bundle().apply {
            sources.forEach { (key, file) -> putString(key, Uri.fromFile(file).toString()) }
            putString("licenseNumber", "TEST-LICENSE")
            putString("licenseExpiration", "1/1/2030")
            putString("issueDate", "9/15/2026")
            putString("validityDate", "3/15/2027")
        }
        val prefs = context.getSharedPreferences(DriverDocumentStore.PREFERENCES, Context.MODE_PRIVATE)
        prefs.edit().putString("clearanceIssue", "old").putString("clearanceExpiry", "old").commit()
        DriverDocumentStore.save(context, draft)
        sources.values.forEach(File::delete)
        DriverDocumentStore.photoKeys.forEach { (source, key) ->
            val saved = File(Uri.parse(prefs.getString(key, null)).path!!)
            assertArrayEquals("original-$source".toByteArray(), saved.readBytes())
        }
        assertEquals("TEST-LICENSE", prefs.getString("licenseNumber", null))
        assertEquals("1/1/2030", prefs.getString("licenseExpiry", null))
        assertEquals("9/15/2026", prefs.getString("clearanceIssue", null))
        assertEquals("3/15/2027", prefs.getString("clearanceExpiry", null))
    }

    @Test fun unreadableUploadDoesNotReplacePreviouslySubmittedDocuments() = isolated { context, folder ->
        val prefs = context.getSharedPreferences(DriverDocumentStore.PREFERENCES, Context.MODE_PRIVATE)
        prefs.edit().putString("licenseNumber", "EXISTING").putString("front", "existing-photo").commit()
        val before = prefs.all.toMap()
        val draft = Bundle().apply { putString("licenseFront", Uri.fromFile(File(folder, "missing.jpg")).toString()) }
        assertTrue(runCatching { DriverDocumentStore.save(context, draft) }.isFailure)
        assertEquals(before, prefs.all)
        assertTrue(File(folder, "submitted_driver_documents").listFiles().orEmpty().isEmpty())
    }
}
