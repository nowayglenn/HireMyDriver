package com.example.hiremydriver

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class DriverForgotPasswordActivity : BaseActivity() {

    private lateinit var emailLayout: TextInputLayout
    private lateinit var edtEmail: TextInputEditText
    private lateinit var btnSendResetLink: MaterialButton

    private lateinit var emailFormSection: LinearLayout
    private lateinit var emailSentSection: LinearLayout
    private lateinit var txtSentConfirmation: TextView

    private lateinit var btnOpenGmail: MaterialButton
    private lateinit var btnProceedToCreatePassword: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_forgot_password)

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val txtBackToLogin = findViewById<TextView>(R.id.txtBackToLogin)

        emailLayout = findViewById(R.id.emailLayout)
        edtEmail = findViewById(R.id.edtEmail)
        btnSendResetLink = findViewById(R.id.btnSendResetLink)

        emailFormSection = findViewById(R.id.emailFormSection)
        emailSentSection = findViewById(R.id.emailSentSection)
        txtSentConfirmation = findViewById(R.id.txtSentConfirmation)

        btnOpenGmail = findViewById(R.id.btnOpenGmail)
        btnProceedToCreatePassword = findViewById(R.id.btnProceedToCreatePassword)

        // Clear error on typing
        edtEmail.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!s.isNullOrEmpty()) {
                    emailLayout.error = null
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Back button
        btnBack.setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        txtBackToLogin.setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        // Send reset link
        btnSendResetLink.setOnClickListener {
            val email = edtEmail.text?.toString()?.trim() ?: ""

            if (email.isEmpty()) {
                emailLayout.error = "Registered driver email is required"
                return@setOnClickListener
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                emailLayout.error = "Please enter a valid email address"
                return@setOnClickListener
            }

            emailLayout.error = null

            btnSendResetLink.isEnabled = false
            DriverAuth.resetPassword(this, email).addOnCompleteListener { task ->
                if (isDestroyed || isFinishing) return@addOnCompleteListener
                btnSendResetLink.isEnabled = true
                if (task.isSuccessful) {
                    txtSentConfirmation.text = "If an account exists for $email, a password reset link has been sent. Open the email to set your new password."
                    emailFormSection.visibility = View.GONE
                    emailSentSection.visibility = View.VISIBLE
                } else emailLayout.error = DriverAuth.message(task.exception)
            }
        }

        // Open Gmail App
        btnOpenGmail.setOnClickListener {
            val intent = packageManager.getLaunchIntentForPackage("com.google.android.gm")
            if (intent != null) {
                startActivity(intent)
            } else {
                val emailIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_EMAIL)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    startActivity(emailIntent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Could not find an email app", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Password changes happen through Firebase's verified email link.
        btnProceedToCreatePassword.text = "Back to login"
        btnProceedToCreatePassword.setOnClickListener {
            finish()
        }
    }
}
