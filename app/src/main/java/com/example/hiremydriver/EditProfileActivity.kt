package com.example.hiremydriver

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.google.android.material.button.MaterialButton
import java.util.*

class EditProfileActivity : BaseActivity() {

    private lateinit var txtBirthDate: TextView
    private lateinit var edtFullName: EditText
    private lateinit var edtEmail: EditText
    private lateinit var edtPhone: EditText
    private lateinit var edtAddress: EditText

    private var profileImageUri: Uri? = null
    private lateinit var saveModel: EditProfileSaveModel
    private var photoLoading = false
    private var accountId: String? = null
    private var photoFlowOpen = false

    private val cropPhoto = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        (application as HireMyDriverApplication).endPhotoSelection()
        photoFlowOpen = false
        if (result.resultCode == RESULT_OK) result.data?.data?.let { previewPhoto(it, true) }
    }

    private val imagePicker = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            photoFlowOpen = false
            (application as HireMyDriverApplication).endPhotoSelection()
        }
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                cropPhoto.launch(Intent(this, CustomerPhotoCropActivity::class.java).setData(uri))
            } catch (_: SecurityException) {
                photoFlowOpen = false
                (application as HireMyDriverApplication).endPhotoSelection()
                Toast.makeText(this, R.string.customer_photo_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        photoFlowOpen = savedInstanceState?.getBoolean("customer_photo_flow_open") ?: false
        if (photoFlowOpen) (application as HireMyDriverApplication).beginPhotoSelection()
        setContentView(R.layout.activity_edit_profile)
        saveModel = androidx.lifecycle.ViewModelProvider(this)[EditProfileSaveModel::class.java]

        // Back Behavior
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            }
        })

        // Find Views
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val btnSave = findViewById<MaterialButton>(R.id.btnSave)
        val btnChangePhoto = findViewById<CardView>(R.id.btnChangePhoto)
        val layoutBirthDate = findViewById<LinearLayout>(R.id.layoutBirthDate)
        
        txtBirthDate = findViewById(R.id.txtBirthDate)
        edtFullName = findViewById(R.id.edtFullName)
        edtEmail = findViewById(R.id.edtEmail)
        edtPhone = findViewById(R.id.edtPhone)
        edtAddress = findViewById(R.id.edtAddress)

        profileImageUri = if (savedInstanceState?.containsKey("customer_photo_draft") == true)
            savedInstanceState.getString("customer_photo_draft")?.let(Uri::parse)
        else CustomerProfilePhoto.savedUri(this)
        previewPhoto(profileImageUri, false)

        val user = CustomerAuth.currentUser(this)
        if (user == null) { finish(); return }
        accountId = user.uid
        val profile = getSharedPreferences("customer_profile_${user.uid}", MODE_PRIVATE)
        edtFullName.setText(CustomerAuth.displayName(this))
        edtEmail.setText(user.email.orEmpty())
        // Changing a sign-in email requires a separate verified-email flow.
        edtEmail.isEnabled = false
        edtPhone.setText(profile.getString("phone", ""))
        txtBirthDate.text = profile.getString("birthDate", "Select birth date")
        edtAddress.setText(profile.getString("address", ""))

        // Listeners
        btnBack.setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        btnChangePhoto.setOnClickListener {
            if (photoFlowOpen) return@setOnClickListener
            photoFlowOpen = true
            (application as HireMyDriverApplication).beginPhotoSelection()
            try { imagePicker.launch(arrayOf("image/*")) }
            catch (_: android.content.ActivityNotFoundException) {
                photoFlowOpen = false
                (application as HireMyDriverApplication).endPhotoSelection()
                Toast.makeText(this, R.string.customer_photo_error, Toast.LENGTH_SHORT).show()
            }
        }

        layoutBirthDate.setOnClickListener {
            showDatePicker()
        }

        btnSave.setOnClickListener {
            val name = edtFullName.text.toString().trim()
            if (name.isBlank()) { edtFullName.error = "Full name is required"; return@setOnClickListener }
            saveModel.save(this, user.uid, profileImageUri, name, edtPhone.text.toString().trim(),
                edtAddress.text.toString().trim(), txtBirthDate.text.toString())
        }
        saveModel.state.observe(this) { state ->
            btnSave.isEnabled = !photoLoading && !state.saving
            btnSave.text = if (state.saving) "Saving…" else getString(R.string.btn_save_changes)
            btnChangePhoto.isEnabled = !state.saving
            listOf(edtFullName, edtPhone, edtAddress, layoutBirthDate).forEach { it.isEnabled = !state.saving }
            if (state.saved) {
                Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                finish()
            }
            state.error?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                saveModel.consumeError()
            }
        }
    }

    private fun previewPhoto(uri: Uri?, newlySelected: Boolean) {
        val save = findViewById<MaterialButton>(R.id.btnSave)
        photoLoading = true
        save.isEnabled = false
        CustomerProfilePhoto.show(findViewById(R.id.imgProfilePhoto), uri) { success ->
            if (isDestroyed) return@show
            photoLoading = false
            save.isEnabled = saveModel.state.value?.saving != true
            if (success) {
                profileImageUri = uri
                if (newlySelected) Toast.makeText(this, R.string.customer_photo_preview, Toast.LENGTH_SHORT).show()
            } else {
                // Keep the cloud reference on a temporary read failure; text edits must not erase it.
                if (newlySelected) CustomerProfilePhoto.show(findViewById(R.id.imgProfilePhoto), profileImageUri)
                Toast.makeText(this, if (newlySelected) getString(R.string.customer_photo_error)
                    else "Could not load your saved photo. Check your connection or photo storage setup.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (accountId != null && CustomerAuth.currentUser(this)?.uid != accountId) finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("customer_photo_flow_open", photoFlowOpen)
        outState.putString("customer_photo_draft", profileImageUri?.toString())
        super.onSaveInstanceState(outState)
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR) - 25 // Default to 25 years ago
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
            val months = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
            txtBirthDate.text = "${months[selectedMonth]} $selectedDay, $selectedYear"
        }, year, month, day).show()
    }
}
