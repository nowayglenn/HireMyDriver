package com.example.hiremydriver

import android.app.TimePickerDialog
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.materialswitch.MaterialSwitch
import java.util.Calendar

class DriverEditWeeklyScheduleActivity : BaseActivity() {
    private lateinit var days: MutableList<DriverDaySchedule>
    private var showErrors = false
    private var edited = false
    private var rendering = false
    private var loadingSchedule = true
    private var savingSchedule = false
    private val switchIds = intArrayOf(R.id.scheduleDay1Part1, R.id.scheduleDay2Part1, R.id.scheduleDay3Part1, R.id.scheduleDay4Part1, R.id.scheduleDay5Part1, R.id.scheduleDay6Part1, R.id.scheduleDay7Part1)
    private val startIds = intArrayOf(R.id.scheduleDay1Part2, R.id.scheduleDay2Part2, R.id.scheduleDay3Part2, R.id.scheduleDay4Part2, R.id.scheduleDay5Part2, R.id.scheduleDay6Part2, R.id.scheduleDay7Part2)
    private val endIds = intArrayOf(R.id.scheduleDay1Part4, R.id.scheduleDay2Part4, R.id.scheduleDay3Part4, R.id.scheduleDay4Part4, R.id.scheduleDay5Part4, R.id.scheduleDay6Part4, R.id.scheduleDay7Part4)
    private val errorIds = intArrayOf(R.id.scheduleError1, R.id.scheduleError2, R.id.scheduleError3, R.id.scheduleError4, R.id.scheduleError5, R.id.scheduleError6, R.id.scheduleError7)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_edit_weekly_schedule)
        val stored = DriverScheduleStore(this).load()
        days = stored.mapIndexed { index, day ->
            val draft = savedInstanceState?.getIntArray("day_$index")
            if (draft != null && draft.size >= 3) DriverDaySchedule(draft[0] == 1, draft[1], draft[2], draft.size == 3 || draft[3] == 1) else day
        }.toMutableList()
        showErrors = savedInstanceState?.getBoolean("show_errors") ?: false
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        days.indices.forEach { index ->
            findViewById<MaterialSwitch>(switchIds[index]).apply {
                isChecked = days[index].enabled
                setOnCheckedChangeListener { _, checked ->
                    if (!rendering) {
                        edited = true
                        days[index] = days[index].copy(enabled = checked, configured = true)
                    }
                    renderDay(index)
                }
            }
            findViewById<View>(startIds[index]).setOnClickListener { pickTime(index, true) }
            findViewById<View>(endIds[index]).setOnClickListener { pickTime(index, false) }
            renderDay(index)
        }
        val saveButton = findViewById<View>(R.id.btnSaveSchedule)
        saveButton.isEnabled = false
        com.example.hiremydriver.recommendation.DriverMatchingSettings.hydrate(this) { loaded ->
            if (isFinishing || isDestroyed) return@hydrate
            loadingSchedule = !loaded
            saveButton.isEnabled = loaded
            if (!loaded) Toast.makeText(this, "Could not load your saved schedule. Check your connection and reopen this screen before saving.", Toast.LENGTH_LONG).show()
            if (!edited && savedInstanceState == null && !isFinishing && !isDestroyed) {
                days = DriverScheduleStore(this).load().toMutableList()
                rendering = true
                days.indices.forEach { findViewById<MaterialSwitch>(switchIds[it]).isChecked = days[it].enabled; renderDay(it) }
                rendering = false
            }
            days.indices.forEach(::renderDay)
        }
        findViewById<View>(R.id.btnSaveSchedule).setOnClickListener { button ->
            showErrors = true
            days.indices.forEach(::renderDay)
            val invalid = days.indexOfFirst { it.error() != null }
            if (invalid >= 0) {
                findViewById<View>(startIds[invalid]).apply {
                    requestFocus()
                    requestRectangleOnScreen(android.graphics.Rect(0, 0, width, height))
                }
                return@setOnClickListener
            }
            button.isEnabled = false
            savingSchedule = true
            days.indices.forEach(::renderDay)
            DriverScheduleStore(this).save(days) { saved ->
                if (!isFinishing && !isDestroyed) {
                    savingSchedule = false
                    days.indices.forEach(::renderDay)
                    button.isEnabled = true
                    Toast.makeText(this, if (saved) "Schedule saved" else "Changes kept on this phone. Could not save to Firebase; please retry.", Toast.LENGTH_LONG).show()
                    if (saved) finish()
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        days.forEachIndexed { index, day ->
            outState.putIntArray("day_$index", intArrayOf(if (day.enabled) 1 else 0, day.start, day.end, if (day.configured) 1 else 0))
        }
        outState.putBoolean("show_errors", showErrors)
        super.onSaveInstanceState(outState)
    }

    private fun pickTime(index: Int, start: Boolean) {
        if (loadingSchedule || savingSchedule || !days[index].enabled) return
        val minutes = if (start) days[index].start else days[index].end
        TimePickerDialog(this, { _, hour, minute ->
            edited = true
            days[index] = if (start) days[index].copy(start = hour * 60 + minute)
                else days[index].copy(end = hour * 60 + minute)
            renderDay(index)
        }, minutes / 60, minutes % 60, DateFormat.is24HourFormat(this)).apply {
            setTitle("${DriverSchedule.dayNames[index]} · ${if (start) "Start time" else "End time"}")
            show()
        }
    }

    private fun timeLabel(minutes: Int): String {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minutes / 60)
            set(Calendar.MINUTE, minutes % 60)
        }
        return DateFormat.getTimeFormat(this).format(calendar.time)
    }

    private fun renderDay(index: Int) {
        val day = days[index]
        findViewById<MaterialSwitch>(switchIds[index]).isEnabled = !loadingSchedule && !savingSchedule
        listOf(startIds[index] to day.start, endIds[index] to day.end).forEachIndexed { position, (id, minutes) ->
            findViewById<TextView>(id).apply {
                text = if (day.configured) timeLabel(minutes) else "Not set"
                isEnabled = day.enabled && !loadingSchedule && !savingSchedule
                alpha = if (day.enabled) 1f else 0.4f
                contentDescription = "${DriverSchedule.dayNames[index]} ${if (position == 0) "start" else "end"} time, $text${if (day.configured && !day.enabled) ", day off" else ""}"
            }
        }
        findViewById<TextView>(errorIds[index]).apply {
            val error = if (showErrors) day.error() else null
            text = error
            visibility = if (error == null) View.GONE else View.VISIBLE
        }
    }
}
