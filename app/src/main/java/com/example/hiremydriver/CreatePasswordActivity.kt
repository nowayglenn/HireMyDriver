package com.example.hiremydriver

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class CreatePasswordActivity : BaseActivity() {

    private lateinit var newPasswordLayout: TextInputLayout
    private lateinit var confirmPasswordLayout: TextInputLayout

    private lateinit var edtNewPassword: TextInputEditText
    private lateinit var edtConfirmPassword: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_password)

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val btnCreatePassword = findViewById<MaterialButton>(R.id.btnCreatePassword)
        val txtBackToLogin = findViewById<TextView>(R.id.txtBackToLogin)

        newPasswordLayout = findViewById(R.id.newPasswordLayout)
        confirmPasswordLayout = findViewById(R.id.confirmPasswordLayout)

        edtNewPassword = findViewById(R.id.edtNewPassword)
        edtConfirmPassword = findViewById(R.id.edtConfirmPassword)

        // Clear error on typing
        setupClearErrorOnTextChange(edtNewPassword, newPasswordLayout)
        setupClearErrorOnTextChange(edtConfirmPassword, confirmPasswordLayout)

        // Back button
        btnBack.setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        // Back to Login
        txtBackToLogin.setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        // Create Password
        btnCreatePassword.setOnClickListener {
            val newPass = edtNewPassword.text.toString().trim()
            val confirmPass = edtConfirmPassword.text.toString().trim()

            var isValid = true

            if (newPass.isEmpty()) {
                newPasswordLayout.error = "New password is required"
                isValid = false
            } else if (newPass.length < 8) {
                newPasswordLayout.error = "Password must be at least 8 characters"
                isValid = false
            } else {
                newPasswordLayout.error = null
            }

            if (confirmPass.isEmpty()) {
                confirmPasswordLayout.error = "Please confirm your new password"
                isValid = false
            } else if (newPass != confirmPass) {
                confirmPasswordLayout.error = "Passwords do not match"
                isValid = false
            } else {
                confirmPasswordLayout.error = null
            }

            if (!isValid) {
                Toast.makeText(this, "Please fill in all required fields correctly", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Toast.makeText(this, "Password updated successfully!", Toast.LENGTH_SHORT).show()
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    private fun setupClearErrorOnTextChange(editText: TextInputEditText, layout: TextInputLayout) {
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
