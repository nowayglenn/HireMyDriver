package com.example.hiremydriver

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.button.MaterialButton
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

class CustomerPhotoCropActivity : BaseActivity() {
    private lateinit var model: CustomerPhotoCropModel
    private lateinit var crop: CustomerPhotoCropView
    private var displayedBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_customer_photo_crop)
        model = ViewModelProvider(this)[CustomerPhotoCropModel::class.java]
        crop = findViewById(R.id.photoCropView)
        val use = findViewById<MaterialButton>(R.id.photoCropUse)
        val rotate = findViewById<MaterialButton>(R.id.photoCropRotate)
        val cancel = findViewById<MaterialButton>(R.id.photoCropCancel)
        val progress = findViewById<View>(R.id.photoCropProgress)
        cancel.setOnClickListener { finish() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (!model.saving) finish() }
        })
        rotate.setOnClickListener { model.rotate() }
        use.setOnClickListener {
            if (!model.saving) crop.croppedBitmap()?.let(model::save)
        }
        model.state.observe(this) { state ->
            val editable = state.bitmap != null && !state.busy
            use.isEnabled = editable
            rotate.isEnabled = editable
            crop.isEnabled = editable
            cancel.isEnabled = !model.saving
            progress.visibility = if (state.busy) View.VISIBLE else View.GONE
            state.bitmap?.let { bitmap ->
                if (bitmap !== displayedBitmap) {
                    displayedBitmap = bitmap
                    crop.setPhoto(bitmap, model.cropPosition)
                }
            }
            if (state.error) {
                Toast.makeText(this, R.string.customer_photo_error, Toast.LENGTH_LONG).show()
                model.clearError()
                if (state.bitmap == null) finish()
            }
            state.output?.let { uri ->
                setResult(RESULT_OK, Intent().setData(uri))
                finish()
            }
        }
        val source = intent.data
        if (source == null) { finish(); return }
        model.load(source, savedInstanceState?.getInt("crop_turns") ?: 0,
            savedInstanceState?.getFloatArray("crop_position"))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        model.cropPosition = crop.cropState()
        outState.putFloatArray("crop_position", model.cropPosition)
        outState.putInt("crop_turns", model.turns)
        super.onSaveInstanceState(outState)
    }
}

data class CustomerCropState(
    val bitmap: Bitmap? = null, val busy: Boolean = true,
    val output: Uri? = null, val error: Boolean = false
)

/** Keeps loading/saving across rotation. Only the confirmed crop becomes an Edit Profile draft. */
class CustomerPhotoCropModel(application: Application) : AndroidViewModel(application) {
    val state = MutableLiveData(CustomerCropState())
    private val worker = Executors.newSingleThreadExecutor()
    private var loaded = false
    var saving = false
        private set
    var turns = 0
        private set
    var cropPosition: FloatArray? = null

    fun load(uri: Uri, restoredTurns: Int, position: FloatArray?) {
        if (loaded) return
        loaded = true
        turns = restoredTurns
        cropPosition = position
        worker.execute {
            val bitmap = runCatching {
                val original = CustomerProfilePhoto.decode(getApplication(), uri) ?: error("Unreadable image")
                if (turns == 0) original else rotated(original, turns * 90f)
            }.getOrNull()
            state.postValue(CustomerCropState(bitmap = bitmap, busy = false, error = bitmap == null))
        }
    }

    fun rotate() {
        val current = state.value ?: return
        val bitmap = current.bitmap ?: return
        if (current.busy) return
        cropPosition = null
        turns = (turns + 1) % 4
        state.value = current.copy(bitmap = rotated(bitmap, 90f))
    }

    private fun rotated(bitmap: Bitmap, degrees: Float) = Bitmap.createBitmap(
        bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)

    fun save(bitmap: Bitmap) {
        val current = state.value ?: return
        if (saving || current.busy) { bitmap.recycle(); return }
        saving = true
        state.value = current.copy(busy = true)
        worker.execute {
            var output: File? = null
            val uri = runCatching {
                val directory = File(getApplication<Application>().filesDir, "customer-profile-crops")
                check(directory.isDirectory || directory.mkdirs())
                val file = File(directory, "${UUID.randomUUID()}.jpg")
                output = file
                file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it)) }
                val context = getApplication<Application>()
                FileProvider.getUriForFile(context, "${context.packageName}.documents", file)
            }.getOrNull()
            bitmap.recycle()
            if (uri == null) output?.delete()
            // Deliver on the main thread before accepting another save attempt.
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                saving = false
                state.value = current.copy(busy = false, output = uri, error = uri == null)
            }
        }
    }

    fun clearError() { state.value = state.value?.copy(error = false) }
    override fun onCleared() { worker.shutdown() }
}
