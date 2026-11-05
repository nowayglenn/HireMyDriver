package com.example.hiremydriver

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.textfield.TextInputEditText

class CustomerHelpCenterActivity : BaseActivity() {
    private data class Question(val title: String, val answer: String)

    private val questions = listOf(
        Question("Where can I find my bookings?", "Open Bookings from the bottom navigation. Select a booking to view its status, pickup, destinations, and available actions."),
        Question("How do I choose a pickup location?", "When booking a driver, open the pickup map and move the pin to your meeting point. Add a place or landmark, then confirm the pickup."),
        Question("What happens if I leave the map during a trip?", "Your saved trip and start time are kept on this phone. Open the same active booking to continue. Location updates pause when you leave the map or minimize the app."),
        Question("Why is my location not showing?", "Open Settings → Location Permissions to check access. Turn on Location in your phone settings. Precise access gives a more accurate position than approximate access."),
        Question("How do I manage notifications?", "Open Settings → Notifications. Save your booking and message preferences or use Manage phone notifications to open Android settings. Live booking and message alerts are not available yet."),
        Question("What should I include when asking for help?", "Choose a topic, describe what happened, and include the booking reference if available. Do not include passwords or verification codes. Contact Support lets you share the message through another app.")
    )
    private val expanded = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_customer_help_center)
        expanded.addAll(savedInstanceState?.getStringArrayList("expanded_questions").orEmpty())
        findViewById<View>(R.id.btnSupportBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnContactFromHelp).setOnClickListener {
            startActivity(Intent(this, CustomerContactSupportActivity::class.java))
        }
        val search = findViewById<TextInputEditText>(R.id.helpSearch)
        search.doAfterTextChanged { renderQuestions(it?.toString().orEmpty()) }
        renderQuestions(search.text?.toString().orEmpty())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList("expanded_questions", ArrayList(expanded))
        super.onSaveInstanceState(outState)
    }

    private fun renderQuestions(query: String) {
        val container = findViewById<LinearLayout>(R.id.helpQuestions)
        container.removeAllViews()
        val matches = questions.filter {
            it.title.contains(query.trim(), ignoreCase = true) || it.answer.contains(query.trim(), ignoreCase = true)
        }
        findViewById<View>(R.id.helpNoResults).visibility = if (matches.isEmpty()) View.VISIBLE else View.GONE
        matches.forEach { question ->
            val card = layoutInflater.inflate(R.layout.item_driver_help_question, container, false)
            val toggle = card.findViewById<View>(R.id.helpQuestionToggle)
            val title = card.findViewById<TextView>(R.id.helpQuestionTitle)
            val answer = card.findViewById<TextView>(R.id.helpQuestionAnswer)
            val indicator = card.findViewById<TextView>(R.id.helpQuestionIndicator)
            title.text = question.title
            answer.text = question.answer
            fun refresh() {
                val isOpen = question.title in expanded
                answer.visibility = if (isOpen) View.VISIBLE else View.GONE
                indicator.text = if (isOpen) "−" else "+"
                toggle.contentDescription = "${question.title}, ${if (isOpen) "collapse answer" else "expand answer"}"
            }
            refresh()
            toggle.setOnClickListener {
                if (!expanded.add(question.title)) expanded.remove(question.title)
                refresh()
            }
            container.addView(card)
        }
    }
}
