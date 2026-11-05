package com.example.hiremydriver

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.TextView
import java.util.concurrent.Executors

/** Full-document previews with bounded decoding and no editing actions. */
internal class DriverDocumentPreview : AutoCloseable {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val requests = mutableMapOf<ImageView, Any>()
    @Volatile private var closed = false

    fun show(image: ImageView, value: String?, status: TextView? = null, showUploadedLabel: Boolean = true) {
        val ticket = Any()
        requests[image] = ticket
        image.setImageDrawable(null)
        status?.visibility = android.view.View.VISIBLE
        if (value.isNullOrBlank()) {
            status?.setText(R.string.driver_document_not_provided)
            return
        }
        status?.setText(R.string.driver_document_loading)
        val context = image.context.applicationContext
        worker.execute {
            val bitmap = runCatching {
                val uri = Uri.parse(value)
                if (DriverCloudStorage.isReference(uri)) DriverCloudStorage.load(context, uri)
                else DocumentPhotoDecoder.decode(context, uri, 2000)
            }.getOrNull()
            main.post {
                if (closed || requests[image] !== ticket) { bitmap?.recycle(); return@post }
                image.setImageBitmap(bitmap)
                status?.setText(if (bitmap == null) R.string.driver_document_unavailable else R.string.driver_document_uploaded)
                if (bitmap != null && !showUploadedLabel) status?.visibility = android.view.View.GONE
            }
        }
    }

    override fun close() { closed = true; requests.clear(); worker.shutdownNow() }
}
