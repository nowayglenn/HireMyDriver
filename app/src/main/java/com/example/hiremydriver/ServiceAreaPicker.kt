package com.example.hiremydriver

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.hiremydriver.recommendation.RecommendationLocation
import org.json.JSONArray
import java.util.concurrent.Executors

/** Keeps the existing multi-choice dialog; GPS only changes suggestions, never selections. */
class ServiceAreaPicker(private val activity: AppCompatActivity) : DefaultLifecycleObserver {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var directory: ServiceAreaDirectory? = null
    private var dialog: AlertDialog? = null
    private var generation = 0
    private var province: String? = null
    private var onLocation: ((String?) -> Unit)? = null
    private val reader = RecommendationLocation(activity) {
        directory?.province(it.countryCode, it.adminArea, it.subAdminArea, it.locality)
    }
    private val permission = activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (dialog?.isShowing == true) locate()
    }

    init { activity.lifecycle.addObserver(this) }

    fun show(current: String, singleSelection: Boolean = false, selected: (String) -> Unit) {
        if (dialog?.isShowing == true) return
        val token = ++generation
        province = null
        val chosen = current.split(',').map(String::trim)
            .filter { it.isNotBlank() && it !in listOf("Select service areas", "Select area", "All Areas") }.toMutableSet()
        val padding = (24 * activity.resources.displayMetrics.density).toInt()
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            setBackgroundResource(R.drawable.bg_booking_dialog)
        }
        body.addView(TextView(activity).apply {
            text = if (singleSelection) "Select Service Area" else "Preferred Service Areas"
            textSize = 22f; setTextColor(android.graphics.Color.parseColor("#061B41"))
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setPadding(0, 0, 0, padding / 2)
        })
        val search = EditText(activity).apply {
            hint = "Search city, municipality or province"
            isSingleLine = true
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        val status = TextView(activity).apply { text = "Finding your current province…"; setPadding(0, padding / 2, 0, padding / 2) }
        val list = ListView(activity).apply {
            choiceMode = if (singleSelection) ListView.CHOICE_MODE_SINGLE else ListView.CHOICE_MODE_MULTIPLE
            divider = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
            dividerHeight = (6 * activity.resources.displayMetrics.density).toInt()
            selector = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        }
        body.addView(search); body.addView(status)
        body.addView(list, LinearLayout.LayoutParams(-1, (minOf(300, (activity.resources.configuration.screenHeightDp - 350).coerceAtLeast(100)) * activity.resources.displayMetrics.density).toInt()))
        var visible = emptyList<String>()
        fun render() {
            val query = search.text.toString().trim()
            val suggestions = directory?.suggestions(province, query).orEmpty().map { it.label }
            // Previously saved areas stay available to uncheck, including selections from another province.
            visible = (chosen.filter { query.isBlank() || ServiceAreaNames.contains(it, query) } + suggestions).distinct()
            list.adapter = ArrayAdapter(activity, if (singleSelection) R.layout.item_booking_choice else R.layout.item_service_area_choice, visible)
            visible.forEachIndexed { index, label -> list.setItemChecked(index, label in chosen) }
        }
        list.setOnItemClickListener { _, _, index, _ ->
            val label = visible[index]
            if (singleSelection) { chosen.clear(); chosen.add(label); return@setOnItemClickListener }
            if (list.isItemChecked(index)) {
                if (chosen.size >= 30) {
                    list.setItemChecked(index, false)
                    Toast.makeText(activity, "Select up to 30 preferred service areas.", Toast.LENGTH_SHORT).show()
                } else chosen.add(label)
            } else chosen.remove(label)
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { render() }
            override fun afterTextChanged(s: Editable?) {}
        })
        val buttons = activity.layoutInflater.inflate(R.layout.service_area_actions, body, false)
        body.addView(buttons)
        val window = com.example.hiremydriver.trip.BookingDialogs.custom(activity, body)
        dialog = window
        buttons.findViewById<android.view.View>(R.id.areaCancel).setOnClickListener { window.dismiss() }
        buttons.findViewById<android.view.View>(R.id.areaApply).setOnClickListener {
            val result = chosen.joinToString(", ").ifBlank { if (singleSelection) "All Areas" else "Select service areas" }
            window.dismiss(); selected(result)
        }
        window.setOnDismissListener { generation++; reader.stop(); onLocation = null; dialog = null }
        window.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
            android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        render()
        onLocation = { code ->
            province = code
            val provinceName = directory?.areas?.firstOrNull { it.provinceCode == code }?.province
            status.text = if (provinceName == null) {
                if (singleSelection) "Search for the area where you need a driver."
                else "Location unavailable. Search for your service areas above."
            } else {
                val instruction = if (singleSelection) "Select the area where you need a driver."
                    else "Select the areas you serve."
                "Suggested areas in $provinceName. $instruction"
            }
            render()
        }
        worker.execute {
            val loaded = directory ?: runCatching {
                val json = JSONArray(activity.assets.open("service_areas.json").bufferedReader().use { it.readText() })
                ServiceAreaDirectory((0 until json.length()).map { index -> json.getJSONObject(index).let {
                    ServiceArea(it.getString("code"), it.getString("name"), it.getString("provinceCode"), it.getString("province"))
                } })
            }.getOrNull()
            main.post {
                if (token != generation || activity.isDestroyed) return@post
                directory = loaded
                render()
                if (loaded == null) {
                    status.text = "Could not load service areas. Close and try again."
                } else if (hasPermission()) locate()
                else permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
            }
        }
    }

    private fun hasPermission() = listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
        .any { ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED }

    private fun locate() {
        if (!hasPermission()) { onLocation?.invoke(null); return }
        reader.start { _, code -> onLocation?.invoke(code) }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        dialog?.dismiss(); generation++; reader.close(); worker.shutdownNow(); main.removeCallbacksAndMessages(null)
    }
}
