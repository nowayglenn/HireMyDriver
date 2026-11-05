package com.example.hiremydriver

import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Local editable settings, separate from the registration wizard. */
abstract class DriverProfileSettingsActivity : BaseActivity() {
    protected data class Field(val id: Int, val layout: Int, val key: String, val label: String,
        val required: Boolean = false, val date: Boolean = false)
    protected abstract val screenLayout: Int
    protected abstract val preferenceName: String
    protected abstract val fields: List<Field>
    protected lateinit var draft: Bundle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(screenLayout)
        draft = savedInstanceState?.getBundle("settings_draft") ?: Bundle().apply {
            getSharedPreferences(preferenceName, MODE_PRIVATE).all.forEach { (key, value) ->
                if (value is String) putString(key, value)
            }
        }
        initialValues()
        fields.forEach { field ->
            val input = findViewById<TextInputEditText>(field.id)
            val layout = findViewById<TextInputLayout>(field.layout)
            input.setText(draft.getString(field.key, ""))
            val red = ColorStateList.valueOf(Color.rgb(198, 40, 40))
            layout.setErrorTextColor(red)
            layout.boxStrokeErrorColor = red
            input.doAfterTextChanged { layout.error = null }
            if (field.date) input.setOnClickListener {
                val calendar = Calendar.getInstance()
                runCatching { SimpleDateFormat("M/d/yyyy", Locale.US).apply { isLenient = false }.parse(input.text.toString()) }
                    .getOrNull()?.let { calendar.time = it }
                DatePickerDialog(this, { _, year, month, day -> input.setText("${month + 1}/$day/$year") },
                    calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).apply {
                    if (field.key == "birthDate") datePicker.maxDate = System.currentTimeMillis()
                    show()
                }
            }
        }
        findViewById<View>(R.id.btnSettingsBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnSettingsSave).setOnClickListener {
            captureFields()
            var firstError: View? = null
            fields.forEach { field ->
                val value = draft.getString(field.key, "")
                val error = when {
                    field.required && value.isBlank() -> "${field.label} is required"
                    field.key == "email" && value.isNotBlank() && !android.util.Patterns.EMAIL_ADDRESS.matcher(value).matches() -> "Enter a valid email address"
                    else -> null
                }
                findViewById<TextInputLayout>(field.layout).error = error
                if (error != null && firstError == null) firstError = findViewById(field.id)
            }
            if (firstError != null) { firstError?.requestFocus(); return@setOnClickListener }
            getSharedPreferences(preferenceName, MODE_PRIVATE).edit().apply {
                draft.keySet().forEach { key -> putString(key, draft.getString(key)) }
                apply()
            }
            Toast.makeText(this, "Changes saved on this phone", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    protected open fun initialValues() = Unit

    private fun captureFields() {
        fields.forEach { draft.putString(it.key, findViewById<TextInputEditText>(it.id).text.toString().trim()) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        captureFields()
        outState.putBundle("settings_draft", draft)
        super.onSaveInstanceState(outState)
    }
}
