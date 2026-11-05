package com.example.hiremydriver

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.button.MaterialButton

class DriverLoginActivity : BaseActivity() {

    override fun backgroundBehindSystemBars(): Boolean = true

    private lateinit var btnBack: ImageButton
    private lateinit var btnLogin: MaterialButton
    private lateinit var txtRegister: TextView
    private lateinit var txtForgotPassword: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_login)

        // Find Views
        btnBack = findViewById(R.id.btnBack)
        btnLogin = findViewById(R.id.btnLogin)
        txtRegister = findViewById(R.id.txtRegister)
        txtForgotPassword = findViewById(R.id.txtForgotPassword)

        // Back Navigation
        btnBack.setOnClickListener { returnToMain() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = returnToMain()
        })

        val email = findViewById<TextInputEditText>(R.id.edtEmail)
        val password = findViewById<TextInputEditText>(R.id.edtPassword)
        val emailLayout = findViewById<TextInputLayout>(R.id.emailLayout)
        val passwordLayout = findViewById<TextInputLayout>(R.id.passwordLayout)
        password.isSaveEnabled = false
        val model = ViewModelProvider(this)[DriverLoginModel::class.java]
        val originalLabel = btnLogin.text
        btnLogin.setOnClickListener {
            val address = email.text.toString().trim()
            emailLayout.error = if (android.util.Patterns.EMAIL_ADDRESS.matcher(address).matches()) null else "Enter a valid email address"
            passwordLayout.error = if (password.text.isNullOrEmpty()) "Password is required" else null
            if (emailLayout.error == null && passwordLayout.error == null) model.signIn(address, password.text.toString())
        }
        model.state.observe(this) { state ->
            btnLogin.isEnabled = !state.busy
            btnLogin.text = originalLabel
            email.isEnabled = !state.busy; password.isEnabled = !state.busy
            txtRegister.isEnabled = !state.busy; txtForgotPassword.isEnabled = !state.busy
            btnBack.isEnabled = !state.busy
            state.error?.let { passwordLayout.error = it; model.consume() }
            state.status?.let { status ->
                password.text?.clear()
                model.consume()
                when (status) {
                    "approved", "pending" -> {
                        startActivity(Intent(this, DriverHomeActivity::class.java).apply {
                            putExtra(DriverHomeActivity.SHOW_PENDING_APPROVAL, status == "pending")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        })
                        finish()
                    }
                    else -> passwordLayout.error = "Your driver application is $status. Please contact support."
                }
            }
        }
        if (savedInstanceState == null && intent.getBooleanExtra(DriverHomeActivity.SHOW_PENDING_APPROVAL, false)) showPending()
        intent.removeExtra(DriverHomeActivity.SHOW_PENDING_APPROVAL)

        // Register Navigation
        txtRegister.setOnClickListener {
            val intent = Intent(this, DriverRegistrationActivity::class.java)
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        // Forgot Password → Driver Forgot Password Activity
        txtForgotPassword.setOnClickListener {
            val intent = Intent(this, DriverForgotPasswordActivity::class.java)
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    private fun returnToMain() {
        if (::btnLogin.isInitialized && !btnLogin.isEnabled) return
        // Logout can leave this login screen as the only activity in the task.
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun showPending() {
        if (supportFragmentManager.findFragmentByTag(PendingApprovalDialog.TAG) == null)
            PendingApprovalDialog().show(supportFragmentManager, PendingApprovalDialog.TAG)
    }

}

class DriverLoginModel(application: android.app.Application) : androidx.lifecycle.AndroidViewModel(application) {
    data class State(val busy: Boolean = false, val status: String? = null, val error: String? = null)
    private val mutable = androidx.lifecycle.MutableLiveData(State())
    val state: androidx.lifecycle.LiveData<State> = mutable
    fun signIn(email: String, password: String) {
        if (mutable.value?.busy == true) return
        mutable.value = State(busy = true)
        DriverAuth.signIn(getApplication(), email, password).addOnCompleteListener {
            mutable.value = if (it.isSuccessful) State(status = it.result) else State(error = DriverAuth.message(it.exception))
        }
    }
    fun consume() { mutable.value = State() }
}
