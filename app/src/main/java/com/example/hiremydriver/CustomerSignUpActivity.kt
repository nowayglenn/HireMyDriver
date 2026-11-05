package com.example.hiremydriver

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.view.View
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class CustomerSignUpActivity : BaseActivity() {

    private lateinit var layoutFullName: TextInputLayout
    private lateinit var layoutPhone: TextInputLayout
    private lateinit var layoutEmail: TextInputLayout
    private lateinit var layoutPassword: TextInputLayout
    private lateinit var layoutConfirmPassword: TextInputLayout

    private lateinit var edtFullName: TextInputEditText
    private lateinit var edtPhone: TextInputEditText
    private lateinit var edtEmail: TextInputEditText
    private lateinit var edtPassword: TextInputEditText
    private lateinit var edtConfirmPassword: TextInputEditText

    private lateinit var chkTerms: CheckBox
    private lateinit var txtTermsLink: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_customer_signup)

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val btnSignUp = findViewById<MaterialButton>(R.id.btnSignUp)
        val btnGoogle = findViewById<MaterialButton>(R.id.btnGoogle)
        GoogleCustomerLogin(this, btnGoogle, listOf(btnSignUp))
        val txtLogin = findViewById<TextView>(R.id.txtLogin)
        val txtSignUpError = findViewById<TextView>(R.id.txtSignUpError)

        layoutFullName = findViewById(R.id.layoutFullName)
        layoutPhone = findViewById(R.id.layoutPhone)
        layoutEmail = findViewById(R.id.layoutEmail)
        layoutPassword = findViewById(R.id.layoutPassword)
        layoutConfirmPassword = findViewById(R.id.layoutConfirmPassword)

        edtFullName = findViewById(R.id.edtFullName)
        edtPhone = findViewById(R.id.edtPhone)
        edtEmail = findViewById(R.id.edtEmail)
        edtPassword = findViewById(R.id.edtPassword)
        edtConfirmPassword = findViewById(R.id.edtConfirmPassword)
        edtPassword.isSaveEnabled = false
        edtConfirmPassword.isSaveEnabled = false

        chkTerms = findViewById(R.id.chkTerms)
        txtTermsLink = findViewById(R.id.txtTermsLink)

        setupClearError(edtFullName, layoutFullName)
        setupClearError(edtPhone, layoutPhone)
        setupClearError(edtEmail, layoutEmail)
        setupClearError(edtPassword, layoutPassword)
        setupClearError(edtConfirmPassword, layoutConfirmPassword)

        // Open Terms & Conditions Activity
        txtTermsLink.setOnClickListener {
            val intent = Intent(this, TermsConditionsActivity::class.java)
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        btnBack.setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        txtLogin.setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        btnSignUp.setOnClickListener {
            if (!NetworkStatus.requireConnection(this) { btnSignUp.performClick() }) return@setOnClickListener
            txtSignUpError.visibility = View.GONE
            val name = edtFullName.text?.toString()?.trim() ?: ""
            val phone = edtPhone.text?.toString()?.trim() ?: ""
            val email = edtEmail.text?.toString()?.trim() ?: ""
            val password = edtPassword.text?.toString() ?: ""
            val confirmPassword = edtConfirmPassword.text?.toString() ?: ""

            var isValid = true

            if (name.isEmpty()) {
                layoutFullName.error = "Full name is required"
                isValid = false
            } else {
                layoutFullName.error = null
            }

            if (phone.isEmpty()) {
                layoutPhone.error = "Phone number is required"
                isValid = false
            } else {
                layoutPhone.error = null
            }

            if (email.isEmpty()) {
                layoutEmail.error = "Email address is required"
                isValid = false
            } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                layoutEmail.error = "Please enter a valid email address"
                isValid = false
            } else {
                layoutEmail.error = null
            }

            if (password.isEmpty()) {
                layoutPassword.error = "Password is required"
                isValid = false
            } else if (password.length < 8) {
                layoutPassword.error = "Password must be at least 8 characters"
                isValid = false
            } else {
                layoutPassword.error = null
            }

            if (confirmPassword.isEmpty()) {
                layoutConfirmPassword.error = "Please confirm your password"
                isValid = false
            } else if (password != confirmPassword) {
                layoutConfirmPassword.error = "Passwords do not match"
                isValid = false
            } else {
                layoutConfirmPassword.error = null
            }

            if (!chkTerms.isChecked) {
                Toast.makeText(this, "Please agree to the Terms & Conditions to proceed", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isValid) {
                Toast.makeText(this, "Please fill in all required fields correctly", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnSignUp.isEnabled = false
            btnGoogle.isEnabled = false
            val inputs = listOf(edtFullName, edtPhone, edtEmail, edtPassword, edtConfirmPassword)
            inputs.forEach { it.isEnabled = false }
            CustomerAuth.signUp(applicationContext, name, phone, email, password).addOnCompleteListener { task ->
                if (isDestroyed || isFinishing) return@addOnCompleteListener
                btnSignUp.isEnabled = true
                btnGoogle.isEnabled = true
                btnSignUp.text = getString(R.string.customer_sign_up)
                inputs.forEach { it.isEnabled = true }
                if (task.isSuccessful) {
                    edtPassword.text?.clear()
                    edtConfirmPassword.text?.clear()
                    Toast.makeText(this, "Account created successfully!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, HomeActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                    finish()
                } else {
                    if (CustomerAuth.isExistingAccount(task.exception)) {
                        layoutEmail.error = "An account already uses this email."
                        androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("Account already exists")
                            .setMessage("This email already has a HireMyDriver account. Sign in using its existing account type, or use a different email to create a customer account.")
                            .setPositiveButton("OK") { _, _ -> edtEmail.requestFocus() }
                            .show()
                        return@addOnCompleteListener
                    }
                    val failure = CustomerAuth.failure(task.exception)
                    when (failure.field) {
                        AuthErrorField.EMAIL -> layoutEmail.error = failure.message
                        AuthErrorField.PASSWORD -> layoutPassword.error = failure.message
                        AuthErrorField.FORM -> {
                            txtSignUpError.text = failure.message
                            txtSignUpError.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }
    }

    private fun setupClearError(editText: TextInputEditText, layout: TextInputLayout) {
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!s.isNullOrEmpty()) {
                    layout.error = null
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }
}
