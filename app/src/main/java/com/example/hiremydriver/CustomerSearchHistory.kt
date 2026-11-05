package com.example.hiremydriver

import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.json.JSONArray

/** Local, account-scoped history; the existing live search continues to render the results. */
class CustomerSearchHistory(private val activity: AppCompatActivity) : DefaultLifecycleObserver {
    private val field = activity.findViewById<EditText>(R.id.edtSearch)
    private val uid = CustomerAuth.currentUser(activity)?.uid
    private val prefs = activity.getSharedPreferences("customer_search_history_$uid", Context.MODE_PRIVATE)
    private val section = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(12), dp(18), dp(8))
        visibility = View.GONE
    }
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
    private fun entries(): List<String> = runCatching {
        val data = JSONArray(prefs.getString("queries", "[]"))
        (0 until data.length()).map { data.getString(it) }.take(10)
    }.getOrDefault(emptyList())
    private fun write(values: List<String>) { prefs.edit().putString("queries", JSONArray(values).toString()).apply() }
    private fun save() {
        if (uid != null && CustomerAuth.currentUser(activity)?.uid == uid && field.text.isNotBlank())
            write(SearchHistoryEntries.remember(entries(), field.text.toString()))
    }
    init {
        val results = activity.findViewById<View>(R.id.searchResultsSection)
        (results.parent as LinearLayout).apply { addView(section, indexOfChild(results)) }
        field.setOnFocusChangeListener { _, focused -> if (!focused) save(); render() }
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { render() }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        field.setOnEditorActionListener { _, action, key ->
            if (action == EditorInfo.IME_ACTION_SEARCH || (key?.keyCode == KeyEvent.KEYCODE_ENTER && key.action == KeyEvent.ACTION_UP)) {
                save()
                activity.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(field.windowToken, 0)
                field.clearFocus()
                true
            } else false
        }
        activity.lifecycle.addObserver(this)
    }
    private fun label(value: String, color: String, size: Float) = TextView(activity).apply {
        text = value; textSize = size; typeface = field.typeface; setTextColor(Color.parseColor(color))
        gravity = Gravity.CENTER_VERTICAL
        minHeight = dp(48)
    }
    private fun render() {
        val values = entries()
        section.visibility = if (field.hasFocus() && field.text.isBlank() && values.isNotEmpty() &&
            uid != null && CustomerAuth.currentUser(activity)?.uid == uid) View.VISIBLE else View.GONE
        if (section.visibility != View.VISIBLE) return
        section.removeAllViews()
        val header = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(label("Recent searches", "#273142", 16f).apply {
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(label("Clear all", "#FF7A00", 13f).apply {
            setPadding(dp(12), 0, 0, 0)
            setOnClickListener { write(emptyList()); render() }
        })
        section.addView(header)
        values.forEach { query ->
            val row = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(label(query, "#667085", 14f).apply {
                maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0)
                compoundDrawablePadding = dp(12)
                setOnClickListener { field.setText(query); field.setSelection(field.length()); save() }
            }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(ImageButton(activity).apply {
                setImageResource(R.drawable.ic_delete)
                imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#9CA3AF"))
                setBackgroundColor(Color.TRANSPARENT); setPadding(dp(14), dp(14), dp(14), dp(14))
                contentDescription = "Remove search: $query"
                setOnClickListener { write(entries().filterNot { it == query }); render() }
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
            section.addView(row)
        }
    }
    override fun onPause(owner: LifecycleOwner) { save() }
    override fun onResume(owner: LifecycleOwner) { render() }
}
