package com.example.hiremydriver

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.TextView
import android.widget.Toast
import android.util.Patterns
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class LoginActivity : BaseActivity() {

    override fun backgroundBehindSystemBars(): Boolean = true

    private lateinit var emailLayout: TextInputLayout
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var edtEmail: TextInputEditText
    private lateinit var edtPassword: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        emailLayout = findViewById(R.id.emailLayout)
        passwordLayout = findViewById(R.id.passwordLayout)
        edtEmail = findViewById(R.id.edtEmail)
        edtPassword = findViewById(R.id.edtPassword)
        edtPassword.isSaveEnabled = false

        val btnBack = findViewById<android.widget.ImageButton>(R.id.btnBack)
        val btnLogin = findViewById<MaterialButton>(R.id.btnLogin)
        val btnGoogle = findViewById<MaterialButton>(R.id.btnGoogle)
        val txtForgotPassword = findViewById<TextView>(R.id.txtForgotPassword)
        val txtSignUp = findViewById<TextView>(R.id.txtSignUp)

        btnBack?.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
            finish()
        }

        // Clear error as user types
        edtEmail.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                emailLayout.error = null
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        edtPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                passwordLayout.error = null
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnLogin.setOnClickListener {
            if (!NetworkStatus.requireConnection(this) { btnLogin.performClick() }) return@setOnClickListener
            val email = edtEmail.text?.toString()?.trim().orEmpty()
            val password = edtPassword.text?.toString().orEmpty()
            emailLayout.error = if (Patterns.EMAIL_ADDRESS.matcher(email).matches()) null else "Enter a valid email address"
            passwordLayout.error = if (password.isNotEmpty()) null else "Password is required"
            if (emailLayout.error != null || passwordLayout.error != null) return@setOnClickListener
            btnLogin.isEnabled = false
            btnGoogle.isEnabled = false
            edtEmail.isEnabled = false
            edtPassword.isEnabled = false
            CustomerAuth.signIn(this, email, password).addOnCompleteListener { task ->
                if (isDestroyed || isFinishing) return@addOnCompleteListener
                btnLogin.isEnabled = true
                btnGoogle.isEnabled = true
                btnLogin.text = getString(R.string.customer_sign_in)
                edtEmail.isEnabled = true
                edtPassword.isEnabled = true
                if (task.isSuccessful) {
                    edtPassword.text?.clear()
                    openCustomerHome()
                } else {
                    passwordLayout.error = CustomerAuth.message(task.exception)
                }
            }
        }

        GoogleCustomerLogin(this, btnGoogle, listOf(btnLogin, txtSignUp))

        // Forgot Password
        txtForgotPassword.setOnClickListener {
            val intent = Intent(this, ForgotPasswordActivity::class.java)
            intent.putExtra(ForgotPasswordActivity.EXTRA_EMAIL, edtEmail.text?.toString()?.trim())
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        // Sign Up
        txtSignUp.setOnClickListener {
            val intent = Intent(this, CustomerSignUpActivity::class.java)
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    private fun openCustomerHome() {
        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        finish()
    }
}
