package com.example.hiremydriver

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.util.*
import androidx.activity.result.contract.ActivityResultContracts
import com.example.hiremydriver.maps.LocationPickerActivity
import com.example.hiremydriver.trip.LocalTripStore
import com.example.hiremydriver.trip.TripPlace

class BookDriverActivity : BaseActivity() {

    private var hourlyRate: java.math.BigDecimal? = null
    private var supportedVehicles: List<String> = emptyList()
    private var selectedDuration: Int = 4
    private var scheduleData: Map<String, Any?> = emptyMap()
    private var driverAcceptingBookings = false
    private var selectedDate: Long? = null
    private var selectedHour: Int? = null
    private var selectedMinute: Int? = null
    private var pickupPin: TripPlace? = null
    private var destinationPin: TripPlace? = null
    private val extraDestinations = mutableListOf<TripPlace>()
    private var picking = "pickup"
    private var applyingPin = false
    private val pinPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            LocalTripStore.readPlace(result.data?.getStringExtra("PLACE"))?.let { point ->
                applyingPin = true
                when (picking) {
                    "pickup" -> { pickupPin = point; findViewById<EditText>(R.id.edtPickup).setText(point.label) }
                    "destination" -> { destinationPin = point; findViewById<EditText>(R.id.edtDestination).setText(point.label) }
                    else -> extraDestinations.add(point)
                }
                applyingPin = false
                renderPins()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_book_driver)
        pickupPin = LocalTripStore.readPlace(savedInstanceState?.getString("pickup_pin"))
        destinationPin = LocalTripStore.readPlace(savedInstanceState?.getString("destination_pin"))
        extraDestinations.addAll(LocalTripStore.readPlaces(savedInstanceState?.getString("extra_pins")))
        picking = savedInstanceState?.getString("picking") ?: "pickup"
        findViewById<android.view.View>(R.id.btnPinPickup).setOnClickListener { pickLocation("pickup") }
        findViewById<android.view.View>(R.id.btnPinDestination).setOnClickListener { pickLocation("destination") }
        findViewById<android.view.View>(R.id.btnAddBookingDestination).setOnClickListener {
            if (pickupPin == null) Toast.makeText(this, "Choose your pickup location first", Toast.LENGTH_SHORT).show()
            else if (destinationPin == null) Toast.makeText(this, "Pin your first destination first", Toast.LENGTH_SHORT).show()
            else if (extraDestinations.size >= 9) Toast.makeText(this, "You can add up to 10 destinations", Toast.LENGTH_SHORT).show()
            else pickLocation("extra")
        }
        findViewById<android.view.View>(R.id.txtExtraDestinations).setOnClickListener {
            if (extraDestinations.isNotEmpty()) androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Remove a destination")
                .setItems(extraDestinations.map { it.label }.toTypedArray()) { _, index -> extraDestinations.removeAt(index); renderPins() }
                .setNegativeButton("Keep all", null).show()
        }
        renderPins()

        // Back Behavior
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
                overridePendingTransition(0, 0)
            }
        })

        // Find Views
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val btnConfirm = findViewById<MaterialButton>(R.id.btnConfirmBooking)
        val confirmLabel = btnConfirm.text
        btnConfirm.isEnabled = false
        
        val txtDriverName = findViewById<TextView>(R.id.txtDriverName)
        val txtRating = findViewById<TextView>(R.id.txtRating)
        val txtPriceHeader = findViewById<TextView>(R.id.txtPriceHeader)
        val imgDriverSmall = findViewById<android.widget.ImageView>(R.id.imgDriverSmall)
        
        val containerDate = findViewById<LinearLayout>(R.id.containerDate)
        val txtDate = findViewById<TextView>(R.id.txtDate)
        
        val containerTime = findViewById<LinearLayout>(R.id.containerTime)
        val txtTime = findViewById<TextView>(R.id.txtTime)
        
        val containerDuration = findViewById<LinearLayout>(R.id.containerDuration)
        val txtDuration = findViewById<TextView>(R.id.txtDuration)
        
        val edtPickup = findViewById<EditText>(R.id.edtPickup)
        val edtDestination = findViewById<EditText>(R.id.edtDestination)
        
        val containerVehicle = findViewById<LinearLayout>(R.id.containerVehicle)
        val txtVehicle = findViewById<TextView>(R.id.txtVehicle)
        
        val containerPickup = findViewById<LinearLayout>(R.id.containerPickup)
        val containerDestination = findViewById<LinearLayout>(R.id.containerDestination)
        
        val txtTotalAmount = findViewById<TextView>(R.id.txtTotalAmount)
        val txtCalculation = findViewById<TextView>(R.id.txtCalculation)

        // Get Data from Intent
        val name = intent.getStringExtra("DRIVER_NAME") ?: "Selected driver"
        val rating = intent.getStringExtra("DRIVER_RATING") ?: "No ratings yet"
        val priceStr = intent.getStringExtra("DRIVER_PRICE") ?: "Rate not set"
        val imageResId = intent.getIntExtra("DRIVER_IMAGE", R.drawable.no_profile).takeIf { it != 0 } ?: R.drawable.no_profile
        
        // Keep decimal prices intact (650.00 must not become 65000).
        hourlyRate = BookingInput.hourlyRate(priceStr)
        selectedDuration = savedInstanceState?.getInt("duration", 4) ?: 4
        if (savedInstanceState?.containsKey("date") == true) selectedDate = savedInstanceState.getLong("date")
        if (savedInstanceState?.containsKey("hour") == true) {
            selectedHour = savedInstanceState.getInt("hour")
            selectedMinute = savedInstanceState.getInt("minute")
        }
        txtDuration.text = if (selectedDuration == 1) "1 hour" else "$selectedDuration hours"
        txtDate.text = selectedDate?.let { java.text.SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone("Asia/Manila")
        }.format(Date(it)) } ?: "Select date"
        txtTime.text = savedInstanceState?.getString("time_label") ?: "Select time"
        txtVehicle.text = savedInstanceState?.getString("vehicle") ?: txtVehicle.text

        txtDriverName.text = name
        txtRating.text = rating
        com.example.hiremydriver.trip.RatingDisplay.bind(txtRating, intent.getStringExtra("DRIVER_UID").orEmpty())
        txtPriceHeader.text = priceStr
        imgDriverSmall.setImageResource(imageResId)
        PeerProfilePhoto.bind(imgDriverSmall, intent.getStringExtra("DRIVER_UID").orEmpty(), true,
            initialThumbnail = intent.getStringExtra("DRIVER_THUMBNAIL"))

        updatePrice(txtTotalAmount, txtCalculation)
        PublicDriverInfo.observe(this, intent.getStringExtra("DRIVER_UID").orEmpty()) { info ->
            driverAcceptingBookings = info.canRequestBooking
            btnConfirm.isEnabled = driverAcceptingBookings
            btnConfirm.text = if (driverAcceptingBookings) confirmLabel else getString(R.string.driver_not_accepting_bookings)
            scheduleData = info.scheduleData
            txtDriverName.text = info.name
            txtPriceHeader.text = info.price
            hourlyRate = BookingInput.hourlyRate(info.price)
            supportedVehicles = info.vehicles.filter { it in DrivingInformation.vehicles }
            if (txtVehicle.text.toString() !in supportedVehicles) txtVehicle.text = "Select vehicle type"
            updatePrice(txtTotalAmount, txtCalculation)
        }

        // Listeners
        btnBack.setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        containerDate.setOnClickListener {
            if (scheduleData.isEmpty()) {
                Toast.makeText(this, "Driver schedule is still loading. Please try again.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Manila")).apply { selectedDate?.let { timeInMillis = it } }
            val picker = DatePickerDialog(this, null, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
            picker.datePicker.minDate = System.currentTimeMillis()
            picker.setOnShowListener {
                picker.getButton(android.app.Dialog.BUTTON_POSITIVE).setOnClickListener {
                    val year = picker.datePicker.year
                    val month = picker.datePicker.month
                    val day = picker.datePicker.dayOfMonth
                    val date = Calendar.getInstance(TimeZone.getTimeZone("Asia/Manila")).apply {
                        set(year, month, day, 0, 0, 0); set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    if (com.example.hiremydriver.recommendation.RegisteredSchedule.workingWindow(scheduleData, date) == null) {
                        Toast.makeText(this, "This driver is not scheduled to work on this day. Please choose an available day.", Toast.LENGTH_LONG).show()
                    } else {
                        selectedDate = date
                        txtDate.text = String.format(Locale.ENGLISH, "%s %d, %d", getMonthName(month), day, year)
                        picker.dismiss()
                    }
                }
            }
            picker.show()
        }

        containerTime.setOnClickListener {
            val c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Manila"))
            TimePickerDialog(this, { _, hour, minute ->
                selectedHour = hour
                selectedMinute = minute
                val ampm = if (hour < 12) "AM" else "PM"
                val h = if (hour % 12 == 0) 12 else hour % 12
                txtTime.text = String.format(Locale.ENGLISH, "%02d:%02d %s", h, minute, ampm)
            }, selectedHour ?: c.get(Calendar.HOUR_OF_DAY), selectedMinute ?: c.get(Calendar.MINUTE), false).show()
        }

        containerDuration.setOnClickListener {
            val durations = (1..24).map { if (it == 1) "$it hour" else "$it hours" }.toTypedArray()
            com.example.hiremydriver.trip.BookingDialogs.choose(this, "Select duration", durations, selectedDuration - 1) { which ->
                selectedDuration = which + 1
                txtDuration.text = durations[which]
                updatePrice(txtTotalAmount, txtCalculation)
            }
        }

        containerVehicle.setOnClickListener {
            val vehicles = supportedVehicles.toTypedArray()
            if (vehicles.isEmpty()) {
                Toast.makeText(this, "This driver has not saved vehicle types yet. Please try another driver.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            com.example.hiremydriver.trip.BookingDialogs.choose(this, "Select vehicle type", vehicles, vehicles.indexOf(txtVehicle.text.toString())) { which ->
                txtVehicle.text = vehicles[which]
            }
        }

        // Real-time validation reset
        edtPickup.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                containerPickup.setBackgroundResource(R.drawable.bg_trip_card)
                if (!applyingPin && pickupPin != null && s.toString() != pickupPin?.label) { pickupPin = null; renderPins() }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        edtDestination.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                containerDestination.setBackgroundResource(R.drawable.bg_trip_card)
                if (!applyingPin && destinationPin != null && s.toString() != destinationPin?.label) { destinationPin = null; renderPins() }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        btnConfirm.setOnClickListener {
            if (!driverAcceptingBookings) return@setOnClickListener
            if (txtVehicle.text.toString() !in supportedVehicles) {
                Toast.makeText(this, "Select a vehicle type this driver can operate.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if ((chosenStart() ?: 0L) <= System.currentTimeMillis()) {
                Toast.makeText(this, "Please select a future booking date and time", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val start = chosenStart()
            if (start != null && com.example.hiremydriver.recommendation.RegisteredSchedule.workingWindow(scheduleData, start) == null) {
                Toast.makeText(this, "This driver is not scheduled to work on this day. Please choose an available day.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (start == null || !com.example.hiremydriver.recommendation.RegisteredSchedule.withinHours(scheduleData,start,start + selectedDuration * 3600000L)) {
                Toast.makeText(this,"Your selected booking time exceeds the driver's available schedule. Please choose another time or duration.",Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val pickup = edtPickup.text.toString().trim()
            val destination = edtDestination.text.toString().trim()

            var hasError = false
            if (pickup.isEmpty()) {
                containerPickup.setBackgroundResource(R.drawable.bg_input_field_error)
                hasError = true
            }
            if (destination.isEmpty()) {
                containerDestination.setBackgroundResource(R.drawable.bg_input_field_error)
                hasError = true
            }

            if (hasError) {
                Toast.makeText(this, "Please enter pickup and destination", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (pickupPin == null || destinationPin == null) {
                Toast.makeText(this, "Pin the exact pickup and destination on the map", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val intent = Intent(this, ConfirmBookingActivity::class.java)
            intent.putExtra(LocalTripStore.ID, UUID.randomUUID().toString())
            intent.putExtra("TRIP_PICKUP", LocalTripStore.placeJson(pickupPin!!).toString())
            intent.putExtra("TRIP_DESTINATIONS", LocalTripStore.placesJson(listOf(destinationPin!!) + extraDestinations))
            intent.putExtra("DURATION_HOURS", selectedDuration)
            intent.putExtra("ADDITIONAL_NOTES", findViewById<EditText>(R.id.edtNotes).text.toString().trim())
            intent.putExtra("DRIVER_UID", this.intent.getStringExtra("DRIVER_UID"))
            intent.putExtra("DRIVER_NAME", txtDriverName.text.toString())
            intent.putExtra("DRIVER_RATING", txtRating.text.toString())
            intent.putExtra("DRIVER_PRICE", txtPriceHeader.text.toString())
            intent.putExtra("DRIVER_IMAGE", imageResId)
            
            intent.putExtra("BOOKING_DATE", txtDate.text.toString())
            intent.putExtra("BOOKING_TIME", txtTime.text.toString())
            intent.putExtra("BOOKING_DURATION", txtDuration.text.toString())
            intent.putExtra("PICKUP_LOCATION", pickup)
            intent.putExtra("DESTINATION", destination)
            intent.putExtra("VEHICLE_TYPE", txtVehicle.text.toString())
            
            intent.putExtra("TOTAL_AMOUNT", txtTotalAmount.text.toString())
            intent.putExtra("CALCULATION", txtCalculation.text.toString())
            
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    private fun chosenStart(): Long? {
        val date = selectedDate ?: return null
        val hour = selectedHour ?: return null
        val minute = selectedMinute ?: return null
        return Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Manila")).apply {
            timeInMillis = date; set(Calendar.HOUR_OF_DAY,hour); set(Calendar.MINUTE,minute); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0)
        }.timeInMillis
    }
    private fun updatePrice(totalTv: TextView, calcTv: TextView) {
        val rate = hourlyRate
        if (rate == null) {
            totalTv.text = "Rate not set"
            calcTv.text = ""
            calcTv.visibility = android.view.View.GONE
            return
        }
        val total = rate.multiply(selectedDuration.toBigDecimal())
        calcTv.visibility = android.view.View.VISIBLE
        totalTv.text = String.format("₱%,.2f", total)
        val unit = if (selectedDuration == 1) "hour" else "hours"
        calcTv.text = "₱${rate.stripTrailingZeros().toPlainString()} x $selectedDuration $unit"
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("pickup_pin", pickupPin?.let { LocalTripStore.placeJson(it).toString() })
        outState.putString("destination_pin", destinationPin?.let { LocalTripStore.placeJson(it).toString() })
        outState.putString("extra_pins", LocalTripStore.placesJson(extraDestinations))
        outState.putString("picking", picking)
        outState.putInt("duration", selectedDuration)
        selectedDate?.let { outState.putLong("date", it) }
        selectedHour?.let { outState.putInt("hour", it) }
        selectedMinute?.let { outState.putInt("minute", it) }
        outState.putString("time_label", findViewById<TextView>(R.id.txtTime).text.toString())
        outState.putString("vehicle", findViewById<TextView>(R.id.txtVehicle).text.toString())
        super.onSaveInstanceState(outState)
    }

    private fun getMonthName(month: Int): String {
        return arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")[month]
    }

    private fun pickLocation(kind: String) {
        if (kind != "pickup" && pickupPin == null) {
            Toast.makeText(this, "Choose your pickup location first", Toast.LENGTH_SHORT).show()
            return
        }
        picking = kind
        val point = if (kind == "pickup") pickupPin else if (kind == "destination") destinationPin else null
        val label = when (kind) { "pickup" -> findViewById<EditText>(R.id.edtPickup).text.toString(); "destination" -> findViewById<EditText>(R.id.edtDestination).text.toString(); else -> "" }
        pinPicker.launch(Intent(this, LocationPickerActivity::class.java)
            .putExtra("PICKER_TITLE", if (kind == "pickup") "Pin pickup location" else "Pin destination")
            .putExtra("PLACE_LABEL", label).putExtra("PLACE", point?.let { LocalTripStore.placeJson(it).toString() }))
    }
    private fun renderPins() {
        findViewById<MaterialButton>(R.id.btnPinPickup).text = if (pickupPin == null) "Choose pickup on map" else "Pickup pinned · Edit map pin"
        findViewById<MaterialButton>(R.id.btnPinDestination).text = if (destinationPin == null) "Choose destination on map" else "Destination pinned · Edit map pin"
        findViewById<TextView>(R.id.txtExtraDestinations).apply {
            visibility = if (extraDestinations.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
            text = extraDestinations.mapIndexed { i, p -> "${i+2}. ${p.label}" }.joinToString("\n") + "\nTap to remove a destination"
        }
    }
}
