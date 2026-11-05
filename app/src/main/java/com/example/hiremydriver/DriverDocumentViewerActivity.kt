package com.example.hiremydriver

import android.os.Bundle
import android.view.View
import android.widget.TextView

/** Read-only viewer; only accepts a submitted document key, never an arbitrary file. */
class DriverDocumentViewerActivity : BaseActivity() {
    private val preview = DriverDocumentPreview()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val key = intent.getStringExtra(DOCUMENT_KEY)
        val title = when (key) {
            "front" -> R.string.driver_document_front
            "back" -> R.string.driver_document_back
            "police" -> R.string.driver_document_police
            else -> { finish(); return }
        }
        setContentView(R.layout.activity_driver_document_viewer)
        findViewById<TextView>(R.id.documentViewerTitle).setText(title)
        val image = findViewById<DocumentZoomView>(R.id.documentViewerImage)
        image.contentDescription = getString(title)
        val saved = getSharedPreferences(DriverAuth.preferenceName(this, DriverDocumentStore.PREFERENCES), MODE_PRIVATE)
        preview.show(image, saved.getString(key, null), findViewById(R.id.documentViewerStatus), showUploadedLabel = false)
        findViewById<View>(R.id.documentViewerClose).setOnClickListener { finish() }
    }

    override fun onDestroy() { preview.close(); super.onDestroy() }

    companion object { const val DOCUMENT_KEY = "submitted_document_key" }
}
