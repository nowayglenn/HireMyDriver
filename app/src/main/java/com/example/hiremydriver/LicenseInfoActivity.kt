package com.example.hiremydriver

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.Calendar
import java.util.concurrent.Executors

class LicenseInfoActivity : RegistrationStepActivity() {
    private data class Field(val id: Int, val key: String, val label: String, val boxId: Int = id, val placeholder: String = "", val required: Boolean = true)
    private val fields = listOf(
        Field(R.id.edtLicenseNumber, "licenseNumber", "License number"),
        Field(R.id.edtLicenseExpiration, "licenseExpiration", "License expiration date", R.id.layoutExpiration, "Select expiration date"),
        Field(R.id.txtIssueDate, "issueDate", "Date of issue", R.id.layoutIssueDate, "Select date of issue"),
        Field(R.id.txtValidityDate, "validityDate", "Validity date", R.id.layoutValidityDate, "Select validity date")
    )
    private val errors = mutableMapOf<Int, RegistrationFieldError>()
    private data class Document(val fieldId: Int, val fieldKey: String, val statusId: Int,
                                val label: String, val kind: DocumentNumberExtractor.Kind)
    private val documents = mapOf(
        "licenseFront" to Document(R.id.edtLicenseNumber, "licenseNumber", R.id.licenseFrontOcrStatus, "License front", DocumentNumberExtractor.Kind.LICENSE),
        "licenseBack" to Document(R.id.edtLicenseNumber, "licenseNumber", R.id.licenseBackOcrStatus, "License back", DocumentNumberExtractor.Kind.LICENSE)
    )
    private val scanGuard = DocumentScanGuard()
    private val pendingScans = mutableSetOf<String>()
    private val imageExecutor = Executors.newSingleThreadExecutor()
    private val recognizerDelegate = lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val recognizer by recognizerDelegate
    private val policePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { saveImage("policeImage", R.id.imgPoliceClearance, it) }
    private val licenseCamera = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val front = result.data?.getStringExtra("licenseFront")
            val back = result.data?.getStringExtra("licenseBack")
            if (!front.isNullOrBlank()) draft.putString("licenseFront", front)
            if (!back.isNullOrBlank()) draft.putString("licenseBack", back)

            if (!front.isNullOrBlank()) {
                val frontUri = Uri.parse(front)
                try { grantUriPermission(packageName, frontUri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Throwable) {}
                saveImage("licenseFront", R.id.imgLicenseFront, frontUri)
            }
            if (!back.isNullOrBlank()) {
                val backUri = Uri.parse(back)
                try { grantUriPermission(packageName, backUri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Throwable) {}
                saveImage("licenseBack", R.id.imgLicenseBack, backUri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_license_info)
        fields.forEach { field ->
            errors[field.id] = RegistrationFieldError(findViewById(field.id), findViewById(field.boxId))
            if (field.placeholder.isNotEmpty()) findViewById<View>(field.boxId).setOnClickListener { chooseDate(field) }
        }
        renderDraft()
        documents.values.distinctBy { it.fieldId }.forEach { document ->
            findViewById<TextView>(document.fieldId).doAfterTextChanged { scanGuard.edited(document.fieldKey) }
        }
        findViewById<TextView>(R.id.edtLicenseExpiration).doAfterTextChanged { scanGuard.edited("licenseExpiration") }
        findViewById<View>(R.id.btnLicenseBack).setOnClickListener { backToPrevious() }
        findViewById<View>(R.id.btnLicenseBackBottom).setOnClickListener { backToPrevious() }
        findViewById<View>(R.id.btnScanLicense).setOnClickListener { openLicenseCamera("licenseFront", both = true) }
        findViewById<View>(R.id.retakeLicenseFront).setOnClickListener { openLicenseCamera("licenseFront") }
        findViewById<View>(R.id.retakeLicenseBack).setOnClickListener { openLicenseCamera("licenseBack") }
        findViewById<View>(R.id.removeLicenseFront).setOnClickListener { removeLicensePhoto("licenseFront", R.id.imgLicenseFront) }
        findViewById<View>(R.id.removeLicenseBack).setOnClickListener { removeLicensePhoto("licenseBack", R.id.imgLicenseBack) }
        findViewById<View>(R.id.btnUploadPolice).setOnClickListener { openPolicePicker() }
        findViewById<View>(R.id.replacePolicePhoto).setOnClickListener { openPolicePicker() }
        findViewById<View>(R.id.removePolicePhoto).setOnClickListener {
            draft.remove("policeImage")
            removeLegacyClearanceNumber()
            showImage("policeImage", R.id.imgPoliceClearance)
        }
        val nextButton = findViewById<MaterialButton>(R.id.btnLicenseNext)
        if (editing) nextButton.text = "Save changes"
        nextButton.setOnClickListener {
                var firstInvalid: View? = null
                fields.forEach { field ->
                    val input = findViewById<TextView>(field.id)
                    val value = input.text.toString().trim()
                    val error = if (field.required && (value.isEmpty() || value == field.placeholder)) "${field.label} is required" else null
                    errors[field.id]?.show(error)
                    if (error != null && firstInvalid == null) firstInvalid = input
                }
                val photoError = when {
                    draft.getString("policeImage").isNullOrBlank() -> R.string.police_photo_required
                    findViewById<ImageView>(R.id.imgPoliceClearance).drawable == null -> R.string.police_photo_unavailable
                    else -> null
                }
                showPolicePhotoError(photoError)
                if (photoError != null && firstInvalid == null) firstInvalid = findViewById(R.id.policePhotoError)
                if (firstInvalid != null) {
                    firstInvalid?.requestFocus()
                    firstInvalid?.let { it.parent.requestChildFocus(it, it) }
                    Toast.makeText(this@LicenseInfoActivity, "Please complete the highlighted fields", Toast.LENGTH_SHORT).show()
                } else continueTo(ReviewInfoActivity::class.java)
            }
    }

    override fun captureDraft() {
        fields.forEach { field ->
            val value = findViewById<TextView>(field.id).text.toString().trim()
            draft.putString(field.key, if (value == field.placeholder) "" else value)
        }
    }

    override fun renderDraft() {
        removeLegacyClearanceNumber()
        fields.forEach { field ->
            val value = draft.getString(field.key, "")
            findViewById<TextView>(field.id).apply {
                text = value.ifEmpty { field.placeholder }
                setTextColor(Color.parseColor(if (value.isEmpty()) "#667085" else "#273142"))
            }
        }
        showImage("licenseFront", R.id.imgLicenseFront)
        showImage("licenseBack", R.id.imgLicenseBack)
        showImage("policeImage", R.id.imgPoliceClearance)
    }

    private fun chooseDate(field: Field) {
        val calendar = Calendar.getInstance()
        val parts = findViewById<TextView>(field.id).text.toString().split("/").mapNotNull { it.toIntOrNull() }
        if (parts.size == 3) calendar.set(parts[2], parts[0] - 1, parts[1])
        DatePickerDialog(this, { _, year, month, day ->
            findViewById<TextView>(field.id).apply {
                text = "${month + 1}/$day/$year"
                setTextColor(Color.parseColor("#273142"))
            }
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun openLicenseCamera(side: String, both: Boolean = false) {
        captureDraft()
        licenseCamera.launch(Intent(this, LicenseCameraActivity::class.java)
            .putExtra(LicenseCameraActivity.SIDE, side).putExtra(LicenseCameraActivity.BOTH, both))
    }

    private fun openPolicePicker() {
        captureDraft()
        policePicker.launch(arrayOf("image/*"))
    }

    private fun saveImage(key: String, id: Int, uri: Uri?) {
        if (uri == null) return
        try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        catch (_: SecurityException) { /* Some providers grant session-only access. */ }
        // Only an accepted photo starts a new document. Cancelling the picker/camera keeps the draft.
        if (key == "policeImage") removeLegacyClearanceNumber()
        draft.putString(key, uri.toString())
        draft.remove("${key}Removed")
        showImage(key, id)
        if (key != "policeImage") readDocument(key, uri)
    }

    private fun removeLegacyClearanceNumber() {
        draft.remove("clearanceNumber")
    }
    private fun removeLicensePhoto(key: String, id: Int) {
        draft.remove(key)
        draft.putBoolean("${key}Removed", true)
        // Supersede only this photo's pending OCR; the other side remains valid.
        val document = documents.getValue(key)
        scanGuard.start(key, document.fieldKey)
        pendingScans.remove(key)
        findViewById<View>(document.statusId).visibility = View.GONE
        showImage(key, id)
        if (draft.getString("licenseFront").isNullOrBlank() && draft.getString("licenseBack").isNullOrBlank()) {
            showImage("licenseFront", R.id.imgLicenseFront)
            showImage("licenseBack", R.id.imgLicenseBack)
        }
    }

    private fun readDocument(key: String, uri: Uri) {
        val document = documents.getValue(key)
        val ticket = scanGuard.start(key, document.fieldKey)
        val expiryTicket = scanGuard.forField(ticket, "licenseExpiration")
        pendingScans.add(key)
        showScanStatus(document, R.string.document_ocr_reading)
        // Decode outside the UI thread. ML Kit reads the Uri rotation metadata.
        imageExecutor.execute {
            val image = try { InputImage.fromFilePath(applicationContext, uri) }
                        catch (_: Exception) { null }
            runOnUiThread {
                if (isDestroyed || isFinishing || !scanGuard.isCurrent(ticket)) return@runOnUiThread
                if (image == null) {
                    finishScan(ticket, document, null, R.string.document_ocr_open_error)
                } else {
                    try {
                        recognizer.process(image)
                            .addOnSuccessListener { result ->
                                val lines = result.textBlocks.flatMap { block -> block.lines }.map { line ->
                                    DocumentNumberExtractor.Line(line.text, line.boundingBox?.let {
                                        DocumentNumberExtractor.Box(it.left, it.top, it.right, it.bottom)
                                    })
                                }
                                val extracted = DocumentNumberExtractor.analyze(lines, document.kind)
                                if (document.kind == DocumentNumberExtractor.Kind.LICENSE) {
                                    val expiryLines = lines.toMutableList()
                                    result.textBlocks.flatMap { it.lines }.forEach { line ->
                                        line.elements.forEach { word ->
                                            word.boundingBox?.let { b -> expiryLines.add(DocumentNumberExtractor.Line(word.text,
                                                DocumentNumberExtractor.Box(b.left, b.top, b.right, b.bottom))) }
                                        }
                                        for (size in 2..3) line.elements.windowed(size).forEach { words ->
                                            val text = words.joinToString(" ") { it.text }
                                            val boxes = words.mapNotNull { it.boundingBox }
                                            if (DocumentExpiryExtractor.isLabel(text) && boxes.size == size) {
                                                expiryLines.add(DocumentNumberExtractor.Line(text, DocumentNumberExtractor.Box(
                                                    boxes.minOf { it.left }, boxes.minOf { it.top },
                                                    boxes.maxOf { it.right }, boxes.maxOf { it.bottom })))
                                            }
                                        }
                                    }
                                    finishExpiry(expiryTicket, DocumentExpiryExtractor.analyze(expiryLines))
                                }
                                val message = when {
                                    result.text.isBlank() -> R.string.document_ocr_no_text
                                    extracted.ambiguous -> R.string.document_ocr_ambiguous
                                    else -> R.string.document_ocr_manual
                                }
                                finishScan(ticket, document, extracted.number, message)
                            }
                            .addOnFailureListener { finishScan(ticket, document, null, R.string.document_ocr_scan_error) }
                    } catch (_: Throwable) {
                        finishScan(ticket, document, null, R.string.document_ocr_scan_error)
                    }
                }
            }
        }
    }

    private fun finishScan(ticket: DocumentScanGuard.Ticket, document: Document, number: String?, failureMessage: Int) {
        if (isDestroyed || isFinishing || !scanGuard.isCurrent(ticket)) return
        pendingScans.remove(ticket.document)
        val input = findViewById<TextView>(document.fieldId)
        val current = input.text.toString().trim()
        val message = when {
            number == null -> failureMessage
            scanGuard.canFill(ticket, current) -> {
                input.text = number
                input.setTextColor(Color.parseColor("#273142"))
                draft.putString(document.fieldKey, number)
                R.string.document_ocr_filled
            }
            current.equals(number, ignoreCase = true) -> R.string.document_ocr_match
            current.isNotEmpty() -> R.string.document_ocr_review
            else -> R.string.document_ocr_kept
        }
        showScanStatus(document, message)
    }

    private fun finishExpiry(ticket: DocumentScanGuard.Ticket, result: DocumentExpiryExtractor.Result) {
        if (isDestroyed || isFinishing || !scanGuard.isCurrent(ticket)) return
        val input = findViewById<TextView>(R.id.edtLicenseExpiration)
        val current = input.text.toString().trim().let { if (it == "Select expiration date") "" else it }
        val value = result.date?.forField()
        // A back photo without expiry should not erase a date already read from the front.
        if (value == null && current.isNotBlank()) return
        val message = when {
            result.ambiguous -> R.string.document_expiry_ambiguous
            value == null -> R.string.document_expiry_manual
            scanGuard.canFill(ticket, current) -> {
                input.text = value
                input.setTextColor(Color.parseColor("#273142"))
                draft.putString("licenseExpiration", value)
                R.string.document_expiry_filled
            }
            current == value -> R.string.document_expiry_match
            else -> R.string.document_expiry_kept
        }
        findViewById<TextView>(R.id.licenseExpiryOcrStatus).apply {
            visibility = if (message == R.string.document_expiry_manual || message == R.string.document_expiry_ambiguous)
                View.VISIBLE else View.GONE
            setText(message)
        }
    }

    private fun showScanStatus(document: Document, message: Int) {
        // Look up the input on the Activity, not inside the status TextView.
        val alreadyFilled = findViewById<TextView>(document.fieldId).text.isNotBlank()
        findViewById<TextView>(document.statusId).apply {
            val routine = message == R.string.document_ocr_reading || message == R.string.document_ocr_filled ||
                message == R.string.document_ocr_match || message == R.string.document_ocr_kept
            visibility = if (routine || (alreadyFilled && (message == R.string.document_ocr_manual ||
                    message == R.string.document_ocr_no_text))) View.GONE else View.VISIBLE
            text = "${document.label}: ${getString(message)}"
        }
    }

    override fun onStop() {
        // Never change a completed draft after navigating to Review or leaving this screen.
        scanGuard.invalidate()
        pendingScans.forEach {
            showScanStatus(documents.getValue(it), R.string.document_ocr_stopped)
        }
        pendingScans.clear()
        super.onStop()
    }

    override fun onDestroy() {
        scanGuard.invalidate()
        imageExecutor.shutdownNow()
        if (recognizerDelegate.isInitialized()) recognizer.close()
        super.onDestroy()
    }

    private fun showImage(key: String, id: Int) {
        val value = draft.getString(key)
        val target = findViewById<ImageView>(id) ?: return
        val isFront = key == "licenseFront"
        val isLicense = isFront || key == "licenseBack"
        if (isLicense) {
            val hasPhoto = !value.isNullOrBlank()
            val hasOtherPhoto = !draft.getString(if (isFront) "licenseBack" else "licenseFront").isNullOrBlank()
            findViewById<View>(if (isFront) R.id.licenseFrontPreview else R.id.licenseBackPreview).visibility =
                if (hasPhoto || (hasOtherPhoto && draft.getBoolean("${key}Removed"))) View.VISIBLE else View.GONE
            findViewById<View>(if (isFront) R.id.removeLicenseFront else R.id.removeLicenseBack).visibility =
                if (hasPhoto) View.VISIBLE else View.GONE
            findViewById<TextView>(if (isFront) R.id.retakeLicenseFront else R.id.retakeLicenseBack).apply {
                setText(if (hasPhoto) R.string.license_camera_retake
                    else if (isFront) R.string.license_scan_front else R.string.license_scan_back)
                contentDescription = getString(if (hasPhoto) {
                    if (isFront) R.string.license_retake_front else R.string.license_retake_back
                } else if (isFront) R.string.license_scan_front else R.string.license_scan_back)
            }
            target.visibility = if (hasPhoto) View.VISIBLE else View.GONE
        } else if (key == "policeImage") {
            // A removed or replaced photo must never pass validation using the old preview.
            target.setImageDrawable(null)
            showPolicePhotoError(null)
            val visible = if (value.isNullOrBlank()) View.GONE else View.VISIBLE
            findViewById<View>(R.id.policePhotoPreview).visibility = visible
            target.visibility = visible
        }
        if (value.isNullOrBlank()) {
            target.setImageDrawable(null)
            return
        }
        imageExecutor.execute {
            val bitmap = try { DocumentPhotoDecoder.decode(applicationContext, Uri.parse(value), 1400) }
                catch (_: Throwable) { null }
            runOnUiThread {
                if (isDestroyed || isFinishing || draft.getString(key) != value) {
                    bitmap?.recycle()
                    return@runOnUiThread
                }
                if (bitmap != null) {
                    target.setImageBitmap(bitmap)
                    if (key == "policeImage") showPolicePhotoError(null)
                    val container = when (key) {
                        "licenseFront" -> R.id.licenseFrontPreview
                        "licenseBack" -> R.id.licenseBackPreview
                        else -> id
                    }
                    if (container != id) findViewById<View>(container)?.visibility = View.VISIBLE
                } else {
                    target.setImageDrawable(null)
                    if (key == "policeImage") showPolicePhotoError(R.string.police_photo_unavailable)
                }
            }
        }
    }

    private fun showPolicePhotoError(message: Int?) {
        findViewById<TextView>(R.id.policePhotoError).apply {
            text = message?.let { getString(it) }.orEmpty()
            visibility = if (message == null) View.GONE else View.VISIBLE
        }
    }

}
