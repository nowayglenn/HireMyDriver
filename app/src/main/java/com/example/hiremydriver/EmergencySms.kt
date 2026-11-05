package com.example.hiremydriver

import android.content.Intent
import android.net.Uri

/** Opens a draft only. The customer sends it in their chosen SMS application. */
object EmergencySms {
    fun composeIntent(phone: String, message: String): Intent {
        val number = PhilippineMobile.normalize(phone) ?: error("Please save a valid emergency contact number.")
        return Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null))
            .putExtra("sms_body", message)
    }
}
