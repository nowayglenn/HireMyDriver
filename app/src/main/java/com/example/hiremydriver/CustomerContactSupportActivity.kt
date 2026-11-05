package com.example.hiremydriver

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import androidx.core.widget.doAfterTextChanged

class CustomerContactSupportActivity : BaseActivity() {
    private val topics = arrayOf("Booking or trip", "Account and profile", "Map and location", "Messages and notifications", "Trip review", "Something else")
    private var selectedTopic = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_contact_support)
        selectedTopic = (savedInstanceState?.getInt("support_topic") ?: 0).coerceIn(topics.indices)
        findViewById<View>(R.id.btnSupportBack).setOnClickListener { finish() }
        val topic = findViewById<MaterialButton>(R.id.supportTopic)
        topic.text = topics[selectedTopic]
        topic.setOnClickListener {
            MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_HireMyDriver_SupportDialog)
                .setBackground(androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_booking_dialog))
                .setTitle("Choose a topic")
                .setSingleChoiceItems(topics, selectedTopic) { dialog, index ->
                    selectedTopic = index
                    topic.text = topics[index]
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        findViewById<TextInputEditText>(R.id.supportMessage).doAfterTextChanged {
            if (!it.isNullOrBlank()) findViewById<TextInputLayout>(R.id.supportMessageLayout).error = null
        }
        findViewById<View>(R.id.btnShareSupport).setOnClickListener { shareMessage() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("support_topic", selectedTopic)
        super.onSaveInstanceState(outState)
    }

    private fun shareMessage() {
        val messageField = findViewById<TextInputEditText>(R.id.supportMessage)
        val message = messageField.text?.toString().orEmpty().trim()
        if (message.isBlank()) {
            findViewById<TextInputLayout>(R.id.supportMessageLayout).error = "Please describe what you need help with"
            messageField.requestFocus()
            return
        }
        val reference = findViewById<TextInputEditText>(R.id.supportBooking).text?.toString().orEmpty().trim()
        val body = buildString {
            appendLine("HireMyDriver — Customer support")
            appendLine("Topic: ${topics[selectedTopic]}")
            if (reference.isNotEmpty()) appendLine("Booking reference: $reference")
            appendLine()
            append(message)
        }
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "HireMyDriver support: ${topics[selectedTopic]}")
            putExtra(Intent.EXTRA_TEXT, body)
        }
        try {
            startActivity(Intent.createChooser(share, "Share support message"))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No sharing app is available. Your message is still here.", Toast.LENGTH_LONG).show()
        }
        // A chooser opening does not mean the message was sent. Keep the form intact.
    }
}
