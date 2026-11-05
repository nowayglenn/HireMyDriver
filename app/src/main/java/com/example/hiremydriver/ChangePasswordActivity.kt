package com.example.hiremydriver

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class ChangePasswordActivity : BaseActivity() {
    companion object { const val DRIVER_SESSION = "driver_password_session" }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val driver = intent.getBooleanExtra(DRIVER_SESSION, false)
        val user = if (driver) DriverAuth.currentUser(this) else CustomerAuth.currentUser(this)
        if (user == null) {
            Toast.makeText(this, "Please sign in before changing your password.", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, if (driver) DriverLoginActivity::class.java else LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish()
            return
        }
        setContentView(R.layout.activity_change_password)

        val model = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                ChangePasswordViewModel(if (driver) DriverAuth::changePassword else CustomerAuth::changePassword) as T
        })[ChangePasswordViewModel::class.java]
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val btnSave = findViewById<MaterialButton>(R.id.btnSavePassword)
        val status = findViewById<TextView>(R.id.txtPasswordChangeError)
        val currentLayout = findViewById<TextInputLayout>(R.id.currentPasswordLayout)
        val newLayout = findViewById<TextInputLayout>(R.id.newPasswordLayout)
        val confirmLayout = findViewById<TextInputLayout>(R.id.confirmPasswordLayout)
        val current = findViewById<TextInputEditText>(R.id.edtCurrentPassword)
        val next = findViewById<TextInputEditText>(R.id.edtNewPassword)
        val confirmation = findViewById<TextInputEditText>(R.id.edtConfirmPassword)
        val inputs = listOf(current, next, confirmation)
        val layouts = listOf(currentLayout, newLayout, confirmLayout)

        inputs.zip(layouts).forEach { (input, layout) ->
            input.isSaveEnabled = false
            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    layout.error = null
                    status.visibility = View.GONE
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }

        fun closeScreen() {
            if (model.state.value?.saving == true) {
                Toast.makeText(this, "Please wait while your password is updated.", Toast.LENGTH_SHORT).show()
                return
            }
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
        btnBack.setOnClickListener { closeScreen() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = closeScreen()
        })

        model.state.observe(this) { state ->
            btnSave.isEnabled = !state.saving && !state.saved
            btnSave.setText(if (state.saving) R.string.password_change_saving else R.string.password_change_save)
            btnBack.isEnabled = !state.saving
            inputs.forEach { it.isEnabled = !state.saving }
            layouts.forEach { it.error = null }
            status.visibility = View.GONE
            state.failure?.let { failure ->
                when (failure.field) {
                    PasswordChangeField.CURRENT -> currentLayout.error = failure.message
                    PasswordChangeField.NEW -> newLayout.error = failure.message
                    PasswordChangeField.CONFIRM -> confirmLayout.error = failure.message
                    PasswordChangeField.FORM -> {
                        status.text = failure.message
                        status.visibility = View.VISIBLE
                    }
                }
            }
            if (state.saved) {
                inputs.forEach { it.text?.clear() }
                Toast.makeText(this, R.string.password_change_success, Toast.LENGTH_LONG).show()
                finish()
            }
        }
        btnSave.setOnClickListener {
            model.save(applicationContext, current.text?.toString().orEmpty(),
                next.text?.toString().orEmpty(), confirmation.text?.toString().orEmpty())
        }
    }
}
