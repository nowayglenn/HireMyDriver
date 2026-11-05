package com.example.hiremydriver

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.doOnLayout
import com.google.android.material.button.MaterialButton
import java.io.File
import java.util.concurrent.Executors

class LicenseCameraActivity : BaseActivity() {
    private lateinit var preview: PreviewView
    private lateinit var guide: LicenseGuideView
    private lateinit var store: DocumentPhotoStore
    private lateinit var session: LicenseCaptureSession
    private lateinit var shutter: MaterialButton
    private val worker = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val captureGate = LicenseCaptureGate()
    private var boundWidth = 0
    private var boundHeight = 0
    private var alignmentGeneration = 0
    private var resumed = false
    private var provider: ProcessCameraProvider? = null
    private var capture: ImageCapture? = null
    private var camera: Camera? = null
    private var photo: File? = null
    private var pairReview = false
    private var starting = false
    private var waitingForLayout = false
    private var busy = false
    private var askedPermission = false
    private var permissionPending = false
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionPending = false
        if (granted) startCamera() else showPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_license_camera)
        store = DocumentPhotoStore(cacheDir, filesDir)
        session = LicenseCaptureSession(if (intent.getBooleanExtra(BOTH, false)) null
            else intent.getStringExtra(SIDE)?.takeIf { it in listOf(FRONT, BACK) } ?: FRONT)
        listOf(FRONT, BACK).forEach { side ->
            store.restore(savedInstanceState?.getString(side))?.let { session.photos[side] = it.name }
        }
        session.retake(savedInstanceState?.getString(SIDE) ?: session.side)
        photo = store.restore(savedInstanceState?.getString("photo"))
        if (photo == null && session.singleSide != null && session.complete) photo = store.restore(session.photos[session.side])
        pairReview = savedInstanceState?.getBoolean("pairReview") == true && session.complete
        askedPermission = savedInstanceState?.getBoolean("askedPermission") ?: false
        permissionPending = savedInstanceState?.getBoolean("permissionPending") ?: false
        preview = findViewById(R.id.licenseCameraPreview)
        preview.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        guide = findViewById(R.id.licenseCameraGuide)
        findViewById<View>(R.id.licenseAlignmentLabel).addOnLayoutChangeListener { label, _, _, _, _, _, _, _, _ ->
            guide.reserveStatusSpace(label.bottom + 16 * resources.displayMetrics.density)
        }
        shutter = findViewById(R.id.captureLicensePhoto)
        findViewById<View>(R.id.closeLicenseCamera).setOnClickListener { finish() }
        findViewById<View>(R.id.retakeLicensePhoto).setOnClickListener {
            if (!busy) { photo?.let(store::discard); photo = null; showLive(); startCamera() }
        }
        findViewById<View>(R.id.acceptLicensePhoto).setOnClickListener {
            if (!busy) { if (pairReview) savePhotos() else confirmShot() }
        }
        findViewById<View>(R.id.retakePairFront).setOnClickListener { retakeSide(FRONT) }
        findViewById<View>(R.id.retakePairBack).setOnClickListener { retakeSide(BACK) }
        preview.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_UP && photo == null && !busy && !pairReview) {
                camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(
                    preview.meteringPointFactory.createPoint(event.x, event.y)).build())
                view.performClick()
            }
            true
        }
        when {
            pairReview -> showPair()
            photo != null -> showReview(photo!!)
            else -> {
                showLive()
                if (hasPermission()) startCamera()
                else {
                    showPermission()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (::preview.isInitialized && photo == null && !pairReview) {
            if (hasPermission()) {
                if (capture == null) startCamera() else startAlignment()
            } else if (!permissionPending) {
                showPermission()
                if (!askedPermission) requestCameraPermission()
            }
        }
    }

    override fun onPause() { resumed = false; stopAlignment(); super.onPause() }
    private fun hasPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun requestCameraPermission() {
        if (permissionPending) return
        askedPermission = true
        permissionPending = true
        shutter.isEnabled = false
        permission.launch(Manifest.permission.CAMERA)
    }

    private fun showPermission() {
        status(R.string.license_camera_permission)
        shutter.visibility = View.VISIBLE
        val settings = askedPermission && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
        shutter.isEnabled = true
        shutter.setText(if (settings) R.string.license_camera_settings else R.string.license_camera_allow)
        shutter.setOnClickListener {
            if (settings) startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            else requestCameraPermission()
        }
    }

    private fun startCamera() {
        if (!resumed || busy || starting || waitingForLayout || isDestroyed || isFinishing || photo != null || pairReview) return
        if (!hasPermission()) { showPermission(); return }
        // This action is only for permission/retry. Live capture is automatic.
        shutter.visibility = View.GONE
        if (preview.width == 0 || preview.height == 0 || preview.isLayoutRequested) {
            waitingForLayout = true
            // Android clears isLayoutRequested AFTER invoking layout listeners. Re-entering
            // here directly registers another listener which may never receive a layout.
            preview.doOnLayout {
                preview.post {
                    waitingForLayout = false
                    startCamera()
                }
            }
            return
        }
        if (capture != null && preview.width == boundWidth && preview.height == boundHeight) return
        starting = true
        shutter.isEnabled = false
        status(R.string.license_camera_starting)
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            starting = false
            if (!resumed || isDestroyed || isFinishing || photo != null || pairReview) return@addListener
            try {
                val available = future.get()
                provider = available
                available.unbindAll()
                val live = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
                val shot = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setFlashMode(ImageCapture.FLASH_MODE_OFF).build()
                val viewport = preview.viewPort ?: error("Preview not ready")
                val group = UseCaseGroup.Builder().addUseCase(live).addUseCase(shot).setViewPort(viewport).build()
                camera = available.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, group)
                boundWidth = preview.width; boundHeight = preview.height
                capture = shot
                shutter.isEnabled = false
                shutter.setOnClickListener(null)
                status(R.string.license_camera_ready)
                startAlignment()
            } catch (_: Exception) {
                capture = null
                stopAlignment()
                status(R.string.license_camera_error)
                shutter.visibility = View.VISIBLE
                shutter.isEnabled = true
                shutter.setText(R.string.license_camera_retry)
                shutter.setOnClickListener { startCamera() }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // Sample the transformed preview so detection and the visible frame share coordinates.
    // Processing is throttled and off the UI thread; it never logs or uploads camera pixels.
    private val alignmentTick = object : Runnable {
        override fun run() {
            if (!resumed || busy || photo != null || pairReview || capture == null) return
            if (preview.width != boundWidth || preview.height != boundHeight) {
                stopAlignment(); capture = null; startCamera(); return
            }
            val generation = alignmentGeneration
            val sampleTime = SystemClock.elapsedRealtime()
            val bitmap = try { preview.bitmap } catch (_: Exception) { null }
            if (bitmap == null) {
                captureGate.reset()
                shutter.isEnabled = false
                guide.update(false, false)
                status(R.string.license_camera_ready)
                alignmentLabel(null, false)
                handler.postDelayed(this, 250)
                return
            }
            val scale = 360f / maxOf(bitmap.width, bitmap.height)
            val small = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1), true)
            if (small !== bitmap) bitmap.recycle()
            worker.execute {
                val pixels = IntArray(small.width * small.height)
                small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
                val detected = LicenseAlignment.detect(pixels, small.width, small.height)
                small.recycle()
                runOnUiThread {
                    if (generation != alignmentGeneration || !resumed || isDestroyed || isFinishing) return@runOnUiThread
                    captureGate.update(detected, sampleTime)
                    val now = SystemClock.elapsedRealtime()
                    val aligned = captureGate.canCapture(now)
                    shutter.isEnabled = aligned
                    guide.update(aligned, true, detected)
                    alignmentLabel(detected, aligned)
                    status(if (aligned) R.string.license_camera_aligned else R.string.license_camera_ready)
                    handler.removeCallbacks(expireAlignment)
                    handler.postDelayed(expireAlignment, 710)
                    if (captureGate.autoReady(now)) { takePhoto(); return@runOnUiThread }
                    handler.postDelayed(this, 250)
                }
            }
        }
    }

    private val expireAlignment = Runnable {
        if (!busy && !captureGate.canCapture(SystemClock.elapsedRealtime())) {
            captureGate.reset()
            shutter.isEnabled = false
            guide.update(false, false)
            alignmentLabel(null, false)
            if (capture != null && photo == null && !pairReview) status(R.string.license_camera_ready)
        }
    }

    private fun startAlignment() {
        stopAlignment()
        if (resumed && !busy && photo == null && !pairReview && capture != null) handler.postDelayed(alignmentTick, 250)
    }
    private fun stopAlignment() {
        alignmentGeneration++
        handler.removeCallbacks(alignmentTick)
        handler.removeCallbacks(expireAlignment)
        captureGate.reset()
        if (::shutter.isInitialized && capture != null) shutter.isEnabled = false
        if (::guide.isInitialized) guide.update(false, false)
    }

    private fun takePhoto() {
        val shot = capture ?: return
        if (busy || !resumed || pairReview || photo != null || captureGate.consume(SystemClock.elapsedRealtime()) == null) return
        val aspect = boundWidth.toFloat() / boundHeight
        busy = true
        stopAlignment()
        shutter.isEnabled = false
        status(R.string.license_camera_saving)
        shot.targetRotation = preview.display.rotation
        val file = store.newShot()
        shot.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                    if (isDestroyed || isFinishing) { store.discard(file); return }
                    cropPhoto(file, aspect)
                }
                override fun onError(error: ImageCaptureException) {
                    store.discard(file)
                    if (isDestroyed || isFinishing) return
                    busy = false
                    shutter.isEnabled = false
                    status(R.string.license_camera_save_error)
                    startAlignment()
                }
            })
    }

    private fun cropPhoto(raw: File, aspect: Float) {
        status(R.string.license_camera_cropping)
        buttons(false)
        val destination = store.newShot()
        worker.execute {
            val cropped = try { LicensePhotoCrop.create(this, raw, destination, aspect) }
                catch (_: Exception) { null }
                finally { store.discard(raw) }
            runOnUiThread {
                if (isDestroyed || isFinishing) {
                    cropped?.preview?.recycle(); store.discard(destination); return@runOnUiThread
                }
                if (cropped == null) {
                    store.discard(destination)
                    busy = false
                    buttons(true)
                    status(R.string.license_camera_crop_retry)
                    startAlignment()
                    return@runOnUiThread
                }
                photo = cropped.file
                guide.visibility = View.GONE
                shutter.visibility = View.GONE
                val animation = findViewById<LicenseCropAnimationView>(R.id.licenseCropAnimation)
                animation.play(cropped.preview, cropped.frame) {
                    if (!isDestroyed && !isFinishing) {
                        provider?.unbindAll(); capture = null
                        busy = false
                        showReview(cropped.file)
                        // Keep the cropped image visible until review decoding completes.
                    }
                }
            }
        }
    }

    private fun title() {
        findViewById<TextView>(R.id.licenseCameraTitle).text =
            if (session.singleSide != null) getString(if (session.side == BACK) R.string.license_camera_back else R.string.license_camera_front)
            else if (session.side == FRONT) "1 / 2  ·  ${getString(R.string.license_front_short)}"
            else "2 / 2  ·  ${getString(R.string.license_back_short)}"
    }

    private fun showLive() {
        findViewById<LicenseCropAnimationView>(R.id.licenseCropAnimation).clear()
        pairReview = false
        findViewById<View>(R.id.licenseAlignmentLabel).visibility = View.VISIBLE
        alignmentLabel(null, false)
        title()
        findViewById<ImageView>(R.id.licensePhotoReview).apply { setImageDrawable(null); visibility = View.GONE }
        findViewById<View>(R.id.licensePairReview).visibility = View.GONE
        guide.visibility = View.VISIBLE
        findViewById<View>(R.id.licensePhotoReviewActions).visibility = View.GONE
        findViewById<TextView>(R.id.licenseCameraHint).setText(
            if (session.side == BACK && session.singleSide == null) R.string.license_camera_flip else R.string.license_camera_guide)
        shutter.visibility = View.GONE
        buttons(true)
    }

    private fun showReview(file: File) {
        stopAlignment()
        findViewById<View>(R.id.licenseAlignmentLabel).visibility = View.INVISIBLE
        title()
        busy = true
        pairReview = false
        shutter.visibility = View.GONE
        guide.visibility = View.GONE
        findViewById<View>(R.id.licensePairReview).visibility = View.GONE
        findViewById<View>(R.id.licensePhotoReviewActions).visibility = View.VISIBLE
        findViewById<View>(R.id.retakeLicensePhoto).visibility = View.VISIBLE
        findViewById<MaterialButton>(R.id.acceptLicensePhoto).setText(
            if (session.singleSide == null && session.side == FRONT && !session.photos.containsKey(BACK))
                R.string.license_camera_next else R.string.license_camera_use)
        findViewById<TextView>(R.id.licenseCameraHint).setText(R.string.license_camera_review)
        buttons(false)
        status(R.string.license_camera_saving)
        worker.execute {
            val bitmap = decode(file)
            runOnUiThread {
                if (isDestroyed || isFinishing || photo != file) { bitmap?.recycle(); return@runOnUiThread }
                busy = false
                findViewById<ImageView>(R.id.licensePhotoReview).apply { setImageBitmap(bitmap); visibility = View.VISIBLE }
                findViewById<LicenseCropAnimationView>(R.id.licenseCropAnimation).clear()
                buttons(true)
                findViewById<View>(R.id.acceptLicensePhoto).isEnabled = bitmap != null
                status(if (bitmap == null) R.string.license_camera_save_error else null)
            }
        }
    }

    private fun confirmShot() {
        val selected = photo ?: return
        session.confirm(selected.name)?.takeIf { it != selected.name }?.let { store.restore(it)?.let(store::discard) }
        photo = null
        if (session.singleSide != null) savePhotos()
        else if (session.complete) showPair()
        else { showLive(); startCamera() }
    }

    private fun retakeSide(side: String) {
        if (busy) return
        session.retake(side)
        photo = null
        showLive()
        startCamera()
    }

    private fun showPair() {
        stopAlignment()
        findViewById<View>(R.id.licenseAlignmentLabel).visibility = View.INVISIBLE
        pairReview = true
        busy = true
        shutter.visibility = View.GONE
        guide.visibility = View.GONE
        findViewById<View>(R.id.licensePhotoReview).visibility = View.GONE
        findViewById<View>(R.id.licensePairReview).visibility = View.VISIBLE
        findViewById<View>(R.id.licensePhotoReviewActions).visibility = View.VISIBLE
        findViewById<View>(R.id.retakeLicensePhoto).visibility = View.GONE
        findViewById<TextView>(R.id.licenseCameraTitle).setText(R.string.license_camera_pair_title)
        findViewById<TextView>(R.id.licenseCameraHint).setText(R.string.license_camera_pair_hint)
        findViewById<MaterialButton>(R.id.acceptLicensePhoto).setText(R.string.license_camera_use_both)
        buttons(false)
        status(R.string.license_camera_saving)
        val front = store.restore(session.photos[FRONT]); val back = store.restore(session.photos[BACK])
        worker.execute {
            val a = front?.let(::decode); val b = back?.let(::decode)
            runOnUiThread {
                if (isDestroyed || isFinishing || !pairReview) { a?.recycle(); b?.recycle(); return@runOnUiThread }
                busy = false
                findViewById<ImageView>(R.id.pairFrontPhoto).setImageBitmap(a)
                findViewById<ImageView>(R.id.pairBackPhoto).setImageBitmap(b)
                buttons(true)
                findViewById<View>(R.id.acceptLicensePhoto).isEnabled = a != null && b != null
                status(if (a == null || b == null) R.string.license_camera_save_error else null)
            }
        }
    }

    private fun savePhotos() {
        if (!session.complete || busy) return
        busy = true
        buttons(false)
        status(R.string.license_camera_saving)
        val names = session.photos.toMap()
        worker.execute {
            val saved = try { store.acceptAll(names.mapValues { store.restore(it.value) ?: error("Missing photo") }) }
                catch (_: Exception) { null }
            runOnUiThread {
                if (isDestroyed || isFinishing) { saved?.values?.forEach { it.delete() }; return@runOnUiThread }
                busy = false
                if (saved == null) {
                    // Keep originals for retry, including a single-side retake.
                    if (session.singleSide != null) {
                        photo = store.restore(session.photos[session.side])
                        photo?.let(::showReview)
                    }
                    buttons(true); status(R.string.license_camera_save_error); return@runOnUiThread
                }
                val result = Intent()
                saved.forEach { (side, file) ->
                    val uri = FileProvider.getUriForFile(this, "$packageName.documents", file)
                    result.putExtra(side, uri.toString())
                    try {
                        grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (_: Exception) {}
                }
                result.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                names.values.forEach { store.restore(it)?.let(store::discard) }
                session.photos.clear()
                setResult(RESULT_OK, result)
                finish()
            }
        }
    }

    private fun decode(file: File): Bitmap? = try { DocumentPhotoDecoder.decode(this, Uri.fromFile(file)) } catch (_: Exception) { null }

    private fun buttons(enabled: Boolean) {
        listOf(R.id.retakeLicensePhoto, R.id.acceptLicensePhoto, R.id.closeLicenseCamera,
            R.id.retakePairFront, R.id.retakePairBack).forEach { findViewById<View>(it).isEnabled = enabled }
    }
    private fun status(message: Int?) {
        findViewById<TextView>(R.id.licenseCameraStatus).apply {
            visibility = if (message == null) View.INVISIBLE else View.VISIBLE
            if (message != null && text != getString(message)) setText(message)
        }
    }

    private fun alignmentLabel(frame: LicenseAlignment.Frame?, ready: Boolean) {
        findViewById<TextView>(R.id.licenseAlignmentLabel).apply {
            setText(when {
                ready -> R.string.license_alignment_perfect
                frame != null -> R.string.license_alignment_steady
                else -> R.string.license_alignment_position
            })
            setTextColor(if (ready) 0xff35d68f.toInt() else 0xffffffff.toInt())
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("photo", photo?.name)
        session.photos.forEach { (side, name) -> outState.putString(side, name) }
        outState.putString(SIDE, session.side)
        outState.putBoolean("pairReview", pairReview)
        outState.putBoolean("askedPermission", askedPermission)
        outState.putBoolean("permissionPending", permissionPending)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        stopAlignment()
        findViewById<LicenseCropAnimationView>(R.id.licenseCropAnimation).clear()
        provider?.unbindAll()
        if (!isChangingConfigurations) {
            photo?.let(store::discard)
            session.photos.values.forEach { store.restore(it)?.let(store::discard) }
        }
        worker.shutdown()
        super.onDestroy()
    }
    companion object {
        const val SIDE = "license_side"
        const val BOTH = "capture_both"
        const val FRONT = LicenseCaptureSession.FRONT
        const val BACK = LicenseCaptureSession.BACK
    }
}
