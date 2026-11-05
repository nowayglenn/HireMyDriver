package com.example.hiremydriver

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.textfield.TextInputEditText

class DriverHelpCenterActivity : BaseActivity() {
    private data class Question(val title: String, val answer: String)

    private val questions = listOf(
        Question("Where can I find my bookings?", "Open Bookings from the bottom navigation. Choose a booking to see its trip details and available actions."),
        Question("How do I update my personal information?", "Go to Profile → Account Settings → Personal Information. Edit your details, then tap Save Changes. These changes are saved on this phone."),
        Question("How do I change my document photos?", "Go to Profile → Documents. Tap Add photo or Replace photo for your license or clearance, choose an image, then tap Save Changes. Saving here keeps the documents on this phone; it does not submit a new verification request."),
        Question("Where can I read customer reviews?", "Go to Profile → Customer Reviews to view the ratings and feedback available from completed trips."),
        Question("How do I select the vehicles I can drive?", "Go to Profile → Driving Information. Tap a vehicle type to select or unselect it. A check mark shows which types are selected."),
        Question("What should I include when asking for help?", "Describe what happened, which screen you were using, and what you expected. Include the booking reference if the issue concerns a trip. Do not include passwords or verification codes.")
    )
    private val expanded = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_help_center)
        expanded.addAll(savedInstanceState?.getStringArrayList("expanded_questions").orEmpty())
        findViewById<View>(R.id.btnSupportBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnContactFromHelp).setOnClickListener {
            startActivity(Intent(this, DriverContactSupportActivity::class.java))
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
