package com.example.hiremydriver

import android.app.DatePickerDialog
import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.doAfterTextChanged
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.io.ByteArrayOutputStream
import java.util.Calendar

class PersonalInfoActivity : RegistrationStepActivity() {
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) takeSelfie()
        else Toast.makeText(this, "Camera access is needed to take your selfie", Toast.LENGTH_SHORT).show()
    }
    private val fields = listOf(
        Triple(R.id.edtDateOfBirth, R.id.layoutDateOfBirth, "birthDate"),
        Triple(R.id.edtAddress, R.id.layoutAddress, "address"),
        Triple(R.id.edtExperience, R.id.layoutExperience, "experience"),
        Triple(R.id.edtBio, R.id.layoutBio, "bio")
    )
    private val requiredMessages = listOf("Date of birth is required", "Address is required",
        "Driving experience is required", "Short description / bio is required")
    private val selfieLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            @Suppress("DEPRECATION")
            val bitmap = result.data?.extras?.get("data") as? Bitmap
            if (bitmap != null) {
                val bytes = ByteArrayOutputStream().apply { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, this) }.toByteArray()
                draft.putByteArray("selfie", bytes)
                showSelfie()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_personal_info)
        fields.forEach { (inputId, layoutId, _) ->
            val layout = findViewById<TextInputLayout>(layoutId)
            val red = ColorStateList.valueOf(Color.rgb(198, 40, 40))
            layout.setErrorTextColor(red)
            layout.boxStrokeErrorColor = red
            findViewById<TextInputEditText>(inputId).doAfterTextChanged { layout.error = null }
        }
        renderDraft()
        findViewById<View>(R.id.btnPersonalBack).setOnClickListener { backToPrevious() }
        findViewById<View>(R.id.btnPersonalBackBottom).setOnClickListener { backToPrevious() }
        findViewById<View>(R.id.edtDateOfBirth).setOnClickListener {
            val calendar = Calendar.getInstance()
            DatePickerDialog(this, { _, year, month, day ->
                findViewById<TextInputEditText>(R.id.edtDateOfBirth).setText("${month + 1}/$day/$year")
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).apply {
                datePicker.maxDate = System.currentTimeMillis()
                show()
            }
        }
        findViewById<View>(R.id.btnTakeSelfie).setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) takeSelfie()
            else cameraPermission.launch(Manifest.permission.CAMERA)
        }
        val nextButton = findViewById<MaterialButton>(R.id.btnPersonalNext)
        if (editing) nextButton.text = "Save changes"
        nextButton.setOnClickListener {
                var firstInvalid: View? = null
                fields.forEachIndexed { index, (inputId, layoutId, _) ->
                    val input = findViewById<TextInputEditText>(inputId)
                    val value = input.text.toString().trim()
                    val error = when {
                        value.isEmpty() -> requiredMessages[index]
                        inputId == R.id.edtExperience && (value.toIntOrNull() == null || value.toInt() < 0) -> "Enter a valid number of years (0 or more)"
                        else -> null
                    }
                    findViewById<TextInputLayout>(layoutId).error = error
                    if (error != null && firstInvalid == null) firstInvalid = input
                }
                if (firstInvalid != null) {
                    firstInvalid?.requestFocus()
                    firstInvalid?.let { it.parent.requestChildFocus(it, it) }
                    Toast.makeText(this@PersonalInfoActivity, "Please complete the highlighted fields", Toast.LENGTH_SHORT).show()
                } else continueTo(LicenseInfoActivity::class.java)
            }
    }

    private fun takeSelfie() {
        try { selfieLauncher.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE)) }
        catch (_: Exception) { Toast.makeText(this, "Unable to open camera", Toast.LENGTH_SHORT).show() }
    }

    override fun captureDraft() {
        fields.forEach { (id, _, key) -> draft.putString(key, findViewById<TextInputEditText>(id).text.toString().trim()) }
    }

    override fun renderDraft() {
        fields.forEach { (id, _, key) -> findViewById<TextInputEditText>(id).setText(draft.getString(key, "")) }
        showSelfie()
    }

    private fun showSelfie() {
        draft.getByteArray("selfie")?.let { bytes ->
            findViewById<ImageView>(R.id.imgProfile).setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        }
    }
}
