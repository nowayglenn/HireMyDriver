package com.example.hiremydriver.maps

import android.content.Intent
import android.location.Location
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.view.View
import com.example.hiremydriver.R
import com.example.hiremydriver.trip.LocalTripStore
import com.example.hiremydriver.trip.TripPlace
import com.google.android.gms.maps.model.LatLng

/** The pin stays fixed; the selected coordinate is always the map camera's center. */
class LocationPickerActivity : MapScreenActivity() {
    private var selected: TripPlace? = null
    private lateinit var detailSheet: MapDetailSheet
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_location_picker)
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        val pickup = intent.getStringExtra("PICKER_TITLE").orEmpty().contains("pickup", ignoreCase = true)
        findViewById<TextView>(R.id.txtMapTitle).text = if (pickup) "Set pickup" else "Set destination"
        findViewById<TextView>(R.id.txtPickerStep).text = if (pickup) "01 · PICKUP" else "02 · DESTINATION"
        findViewById<TextView>(R.id.txtPickerHeading).text = if (pickup) "Where should we meet?" else "Where are you heading?"
        findViewById<TextView>(R.id.txtLandmarkLabel).text = if (pickup) "Pickup place or landmark" else "Destination place or landmark"
        findViewById<TextView>(R.id.btnConfirmPin).text = if (pickup) "Confirm pickup" else "Confirm destination"
        val label = findViewById<EditText>(R.id.edtPlaceLabel)
        label.setText(savedInstanceState?.getString("place_label") ?: intent.getStringExtra("PLACE_LABEL").orEmpty())
        fun showPlaceName() {
            findViewById<TextView>(R.id.txtSelectedPlace).text = label.text.toString().trim().ifBlank { "Selected map location" }
        }
        label.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { showPlaceName() }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        showPlaceName()
        selected = LocalTripStore.readPlace(savedInstanceState?.getString("selected_place") ?: intent.getStringExtra("PLACE"))
        tripMap = TripMapController(this, findViewById(R.id.mapCanvas), savedInstanceState, centerPicker = true, initialPlace = selected, bottomOverlayDp = 20).apply {
            onCenterChanged = { select(it) }
        }
        findViewById<View>(R.id.btnMyLocation).setOnClickListener { showOwnLocation(follow = true) }
        detailSheet = MapDetailSheet(this, savedInstanceState, findViewById(R.id.btnPickerExpand))
        label.setOnFocusChangeListener { _, focused -> if (focused) {
            tripMap?.freezePickerSelection()
            detailSheet.expandForInput()
        } }
        showOwnLocation(follow = pickup && selected == null && savedInstanceState == null)
        findViewById<View>(R.id.btnConfirmPin).setOnClickListener {
            val center = tripMap?.selectedCenter() ?: return@setOnClickListener
            val name = label.text.toString().trim()
            if (name.isBlank()) { label.error = "Enter a place name or landmark"; label.requestFocus(); return@setOnClickListener }
            val place = placeAt(center).copy(label = name)
            setResult(RESULT_OK, Intent().putExtra("PLACE", LocalTripStore.placeJson(place).toString()))
            finish()
        }
    }
    private fun placeAt(p: LatLng) = TripPlace("Selected location", p.latitude.coerceIn(-85.0,85.0), ((p.longitude + 180) % 360 + 360) % 360 - 180)
    private fun select(p: LatLng) {
        selected = placeAt(p)
        findViewById<View>(R.id.btnConfirmPin).isEnabled = true
    }
    override fun onLocationChanged(location: Location) {
        tripMap?.updateNavigationLocation(location)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("selected_place", selected?.let { LocalTripStore.placeJson(it).toString() })
        outState.putString("place_label", findViewById<EditText>(R.id.edtPlaceLabel).text.toString())
        detailSheet.save(outState)
    }
}
