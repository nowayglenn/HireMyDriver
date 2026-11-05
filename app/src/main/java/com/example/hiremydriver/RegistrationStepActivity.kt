package com.example.hiremydriver

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts

/** One draft travels through the wizard and back; editing a section returns to review. */
abstract class RegistrationStepActivity : BaseActivity() {
    protected var draft = Bundle()
    protected val editing: Boolean get() = intent.getBooleanExtra(EDIT, false)

    private val stepLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.getBundleExtra(DRAFT)?.let {
                draft = Bundle(it)
                renderDraft()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        draft = Bundle(savedInstanceState?.getBundle(DRAFT) ?: intent.getBundleExtra(DRAFT) ?: Bundle())
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = backToPrevious()
        })
    }

    protected abstract fun captureDraft()
    protected abstract fun renderDraft()

    protected fun continueTo(next: Class<*>) {
        captureDraft()
        if (editing) {
            setResult(RESULT_OK, Intent().putExtra(DRAFT, draft))
            finish()
        } else {
            stepLauncher.launch(Intent(this, next).putExtra(DRAFT, draft))
        }
    }

    protected fun editSection(screen: Class<*>) {
        stepLauncher.launch(Intent(this, screen).putExtra(DRAFT, draft).putExtra(EDIT, true))
    }

    protected fun backToPrevious() {
        // Cancel an edit without changing the reviewed values. Normal Back retains draft work.
        if (!editing) {
            captureDraft()
            setResult(RESULT_OK, Intent().putExtra(DRAFT, draft))
        }
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        captureDraft()
        outState.putBundle(DRAFT, draft)
        super.onSaveInstanceState(outState)
    }

    companion object {
        const val DRAFT = "driver_registration_draft"
        const val EDIT = "edit_registration_section"
    }
}
