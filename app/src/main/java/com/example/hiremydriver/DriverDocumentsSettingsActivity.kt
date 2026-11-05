package com.example.hiremydriver

import android.os.Bundle
import android.content.Intent
import android.view.View
import android.widget.TextView
import com.google.android.material.textfield.TextInputEditText

/** Submitted documents have no picker, date dialog or save action. */
class DriverDocumentsSettingsActivity : BaseActivity() {
    private val previews = DriverDocumentPreview()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_documents_settings)
        val saved = getSharedPreferences(DriverAuth.preferenceName(this, PREFERENCES), MODE_PRIVATE)
        listOf(
            R.id.settingsLicenseNumber to "licenseNumber",
            R.id.settingsLicenseExpiry to "licenseExpiry",
            R.id.settingsClearanceIssue to "clearanceIssue",
            R.id.settingsClearanceExpiry to "clearanceExpiry"
        ).forEach { (id, key) ->
            findViewById<TextInputEditText>(id).apply {
                setText(saved.getString(key, "").orEmpty().ifBlank { getString(R.string.driver_document_not_provided) })
                keyListener = null
                isFocusable = false
                isFocusableInTouchMode = false
                isClickable = false
                isLongClickable = false
                isCursorVisible = false
                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            }
        }
        listOf(
            Triple("front", R.id.settingsLicenseFrontPreview, R.id.settingsLicenseFrontStatus),
            Triple("back", R.id.settingsLicenseBackPreview, R.id.settingsLicenseBackStatus),
            Triple("police", R.id.settingsPolicePreview, R.id.settingsPoliceStatus)
        ).forEach { (key, image, status) ->
            previews.show(findViewById(image), saved.getString(key, null), findViewById<TextView>(status))
            findViewById<View>(image).setOnClickListener {
                startActivity(Intent(this, DriverDocumentViewerActivity::class.java)
                    .putExtra(DriverDocumentViewerActivity.DOCUMENT_KEY, key))
            }
        }
        findViewById<View>(R.id.btnSettingsBack).setOnClickListener { finish() }
    }

    override fun onDestroy() { previews.close(); super.onDestroy() }

    companion object { const val PREFERENCES = DriverDocumentStore.PREFERENCES }
}
