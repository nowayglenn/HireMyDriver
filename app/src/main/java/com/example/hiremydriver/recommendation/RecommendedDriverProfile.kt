package com.example.hiremydriver.recommendation

import android.content.Context
import android.content.Intent
import com.example.hiremydriver.DriverDetailsActivity
import com.example.hiremydriver.R
import java.util.Locale

/** Presentation adapter: use the existing customer-facing profile for the selected candidate. */
object RecommendedDriverProfile {
    fun intent(context: Context, driver: DriverCandidate): Intent =
        Intent(context, DriverDetailsActivity::class.java).apply {
            putExtra("DRIVER_NAME", driver.name)
            putExtra("DRIVER_RATING", driver.rating?.let { String.format(Locale.getDefault(), "★ %.1f", it) }
                ?: context.getString(R.string.recommendation_no_rating))
            putExtra("DRIVER_UID", driver.id)
            putExtra("DRIVER_STATUS", driver.profileStatus)
            putExtra("DRIVER_BIO", driver.bio)
            putExtra("DRIVER_THUMBNAIL", driver.thumbnail)
            putExtra("DRIVER_EXP", driver.experience.takeIf { it.isNotBlank() }?.let { "$it years experience" } ?: "Experience not provided")
            putExtra("DRIVER_VEHICLE", "${driver.vehicles.joinToString(", ")} (${driver.transmissions.joinToString(" / ")})")
            putExtra("DRIVER_PRICE", if (driver.rateProvided) context.getString(R.string.price_per_hour_full, driver.hourlyRate.toString()) else "Rate not set")
            putExtra("DRIVER_LOCATION", driver.serviceAreas.joinToString(", "))
            putExtra("DRIVER_IMAGE", R.drawable.no_profile)

        }
}
