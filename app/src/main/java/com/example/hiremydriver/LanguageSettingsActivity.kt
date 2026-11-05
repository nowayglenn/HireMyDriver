package com.example.hiremydriver

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.activity.OnBackPressedCallback
import com.google.android.material.button.MaterialButton

class LanguageSettingsActivity : BaseActivity() {
    companion object { const val EXTRA_DRIVER = "language_settings_driver" }

    private var rbEnglish: RadioButton? = null
    private var rbTagalog: RadioButton? = null
    private var rbIlocano: RadioButton? = null
    private var selectedLanguage = "English"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_language_settings)

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
        val btnSave = findViewById<MaterialButton>(R.id.btnSaveLanguage)
        
        val radioGroup = findViewById<RadioGroup>(R.id.radioGroupLanguage)
        
        rbEnglish = findViewById(R.id.rbEnglish)
        rbTagalog = findViewById(R.id.rbTagalog)
        rbIlocano = findViewById(R.id.rbIlocano)

        // Initialize selection from current locale AFTER views are found
        val currentLang = savedInstanceState?.getString("selected_language") ?: LocaleHelper.getLanguage(this)
        selectedLanguage = when (currentLang) {
            "tl" -> "Tagalog"
            "ilo" -> "Ilocano"
            else -> "English"
        }
        when(currentLang) {
            "tl" -> rbTagalog?.isChecked = true
            "ilo" -> rbIlocano?.isChecked = true
            else -> rbEnglish?.isChecked = true
        }

        // Listeners
        btnBack?.setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        radioGroup?.setOnCheckedChangeListener { _, checkedId ->
            selectedLanguage = when(checkedId) {
                R.id.rbTagalog -> "Tagalog"
                R.id.rbIlocano -> "Ilocano"
                else -> "English"
            }
        }

        btnSave?.setOnClickListener {
            val langCode = when(selectedLanguage) {
                "Tagalog" -> "tl"
                "Ilocano" -> "ilo"
                else -> "en"
            }
            
            LocaleHelper.setLocale(this, langCode)
            
            val driver = intent.getBooleanExtra(EXTRA_DRIVER, LaunchSession.role(this) == LaunchRole.DRIVER)
            val intent = Intent(this, if (driver) DriverHomeActivity::class.java else HomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()

            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("selected_language", when (selectedLanguage) {
            "Tagalog" -> "tl"
            "Ilocano" -> "ilo"
            else -> "en"
        })
        super.onSaveInstanceState(outState)
    }
}
