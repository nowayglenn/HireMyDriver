package com.example.hiremydriver

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.graphics.Color
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider

class ReviewInfoActivity : RegistrationStepActivity() {
    private val previews = DriverDocumentPreview()
    private var submissionCover: View? = null
    private var previousLightStatus = false
    private var previousLightNavigation = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_review_info)
        findViewById<View>(R.id.btnLicenseBack).setOnClickListener { backToPrevious() }
        findViewById<View>(R.id.btnReviewBack).setOnClickListener { backToPrevious() }
        findViewById<View>(R.id.btnEditAccount).setOnClickListener { editSection(DriverRegistrationActivity::class.java) }
        findViewById<View>(R.id.btnEditPersonal).setOnClickListener { editSection(PersonalInfoActivity::class.java) }
        findViewById<View>(R.id.btnEditLicense).setOnClickListener { editSection(LicenseInfoActivity::class.java) }
        renderDraft()
        val submission = ViewModelProvider(this)[DriverDocumentSubmissionModel::class.java]
        val submit = findViewById<View>(R.id.btnSubmitVerification)
        submit.setOnClickListener { submission.submit(draft) }
        submission.state.observe(this) { state ->
            showSubmissionCover(state == DriverDocumentSubmissionModel.State.SAVING ||
                state == DriverDocumentSubmissionModel.State.SAVED)
            submit.isEnabled = state !in listOf(DriverDocumentSubmissionModel.State.SAVING,
                DriverDocumentSubmissionModel.State.SAVED, DriverDocumentSubmissionModel.State.ACCOUNT_EXISTS,
                DriverDocumentSubmissionModel.State.RECOVER_PASSWORD)
            listOf(R.id.btnLicenseBack, R.id.btnReviewBack, R.id.btnEditAccount,
                R.id.btnEditPersonal, R.id.btnEditLicense).forEach { findViewById<View>(it).isEnabled = submit.isEnabled }
            if (state == DriverDocumentSubmissionModel.State.SAVED) {
                draft.remove("password")
                draft.remove("confirmPassword")
                startActivity(Intent(this, DriverHomeActivity::class.java).apply {
                    putExtra(DriverHomeActivity.SHOW_PENDING_APPROVAL, !DriverAuth.approved(this@ReviewInfoActivity))
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                @Suppress("DEPRECATION")
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                finish()
            } else if (state == DriverDocumentSubmissionModel.State.ERROR) {
                Toast.makeText(this, submission.errorMessage ?: getString(R.string.driver_document_save_error), Toast.LENGTH_LONG).show()
            } else if (state == DriverDocumentSubmissionModel.State.RECOVER_PASSWORD) {
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Finish setting up your password")
                    .setMessage("Firebase already has an account for this email, possibly from an earlier registration attempt. To continue, enter that account's password or reset it by email. Your registration details and photos will stay here.")
                    .setPositiveButton("Reset password") { _, _ ->
                        submission.cancelConfirmation()
                        DriverAuth.resetPassword(this, draft.getString("email", "")).addOnCompleteListener { task ->
                            if (isFinishing || isDestroyed) return@addOnCompleteListener
                            if (task.isSuccessful) {
                                androidx.appcompat.app.AlertDialog.Builder(this)
                                    .setTitle("Check your email")
                                    .setMessage("Use the password reset link to choose your password. Then tap Edit account, enter the new password in both fields, save, and submit again.")
                                    .setPositiveButton("Edit account") { _, _ -> editSection(DriverRegistrationActivity::class.java) }
                                    .setNegativeButton("Later", null).show()
                            } else Toast.makeText(this, DriverAuth.message(task.exception), Toast.LENGTH_LONG).show()
                        }
                    }
                    .setNeutralButton("Edit account") { _, _ ->
                        submission.cancelConfirmation()
                        editSection(DriverRegistrationActivity::class.java)
                    }
                    .setNegativeButton("Cancel") { _, _ -> submission.cancelConfirmation() }
                    .setOnCancelListener { submission.cancelConfirmation() }.show()
            } else if (state == DriverDocumentSubmissionModel.State.ACCOUNT_EXISTS) {
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Account already exists")
                    .setMessage("This email already has a HireMyDriver account. A customer email cannot be used for driver registration. Use a different email, or sign in using the existing account's login.")
                    .setPositiveButton("Edit email") { _, _ ->
                        submission.cancelConfirmation()
                        editSection(DriverRegistrationActivity::class.java)
                    }
                    .setNegativeButton("Cancel") { _, _ -> submission.cancelConfirmation() }
                    .setOnCancelListener { submission.cancelConfirmation() }
                    .show()
            }
        }
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (submission.state.value != DriverDocumentSubmissionModel.State.SAVING) backToPrevious()
            }
        })
    }

    override fun captureDraft() = Unit

    private fun showSubmissionCover(show: Boolean) {
        val decor = window.decorView as ViewGroup
        val bars = androidx.core.view.WindowCompat.getInsetsController(window, decor)
        if (show && submissionCover == null) {
            previousLightStatus = bars.isAppearanceLightStatusBars
            previousLightNavigation = bars.isAppearanceLightNavigationBars
            bars.hide(androidx.core.view.WindowInsetsCompat.Type.ime())
            val cover = FrameLayout(this).apply {
                setBackgroundColor(Color.WHITE)
                isClickable = true
                isFocusable = true
                // Attach above the content's inset padding so white also fills the system bars.
                addView(ProgressBar(context).apply {
                    contentDescription = "Submitting for verification"
                }, FrameLayout.LayoutParams(
                    (40 * resources.displayMetrics.density).toInt(),
                    (40 * resources.displayMetrics.density).toInt(), Gravity.CENTER))
            }
            decor.addView(cover, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            submissionCover = cover
            bars.isAppearanceLightStatusBars = true
            bars.isAppearanceLightNavigationBars = true
        } else if (!show && submissionCover != null) {
            decor.removeView(submissionCover)
            submissionCover = null
            bars.isAppearanceLightStatusBars = previousLightStatus
            bars.isAppearanceLightNavigationBars = previousLightNavigation
        }
    }

    override fun renderDraft() {
        draft.remove("clearanceNumber")
        val fields = listOf(
            Triple(R.id.txtReviewFullName, "Full name", "name"),
            Triple(R.id.txtReviewEmail, "Email", "email"),
            Triple(R.id.txtReviewPhone, "Mobile number", "mobile"),
            Triple(R.id.txtReviewBirthDate, "Date of birth", "birthDate"),
            Triple(R.id.txtReviewAddress, "Address", "address"),
            Triple(R.id.txtReviewExperience, "Years of driving experience", "experience"),
            Triple(R.id.txtReviewBio, "Short description / bio", "bio"),
            Triple(R.id.txtReviewLicenseNumber, "License number", "licenseNumber"),
            Triple(R.id.txtReviewExpirationDate, "License expiration date", "licenseExpiration"),
            Triple(R.id.txtReviewIssueDate, "Date of issue", "issueDate"),
            Triple(R.id.txtReviewValidityDate, "Validity date", "validityDate")
        )
        fields.forEach { (id, label, key) ->
            findViewById<TextView>(id).text = "$label: ${draft.getString(key, "").ifBlank { "Not provided" }}"
        }
        draft.getByteArray("selfie")?.let { bytes ->
            findViewById<ImageView>(R.id.imgReviewProfile).setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        }
        listOf(R.id.imgReviewLicenseFront to "licenseFront", R.id.imgReviewLicenseBack to "licenseBack",
            R.id.imgReviewPoliceClearance to "policeImage").forEach { (id, key) ->
            previews.show(findViewById(id), draft.getString(key))
        }
    }

    override fun onDestroy() { previews.close(); super.onDestroy() }
}
