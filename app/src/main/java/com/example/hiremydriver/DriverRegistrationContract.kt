package com.example.hiremydriver

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

/** Shared registration payload contract; no passwords or local file URIs reach Firestore. */
internal object DriverRegistrationContract {
    val fields = linkedMapOf(
        "name" to "fullName", "email" to "email", "mobile" to "phoneNumber",
        "birthDate" to "birthDate", "address" to "address", "experience" to "experience",
        "bio" to "bio", "licenseNumber" to "licenseNumber", "licenseExpiration" to "licenseExpiration",
        "issueDate" to "issueDate", "validityDate" to "validityDate"
    )
    val documents = linkedMapOf("licenseFront" to "license-front.jpg", "licenseBack" to "license-back.jpg",
        "policeImage" to "police-clearance.jpg")
    fun validDate(value: String): Boolean {
        if (!Regex("(0?[1-9]|1[0-2])/(0?[1-9]|[12][0-9]|3[01])/[0-9]{4}").matches(value)) return false
        val position = ParsePosition(0)
        val date = SimpleDateFormat("M/d/yyyy", Locale.US).apply { isLenient = false }.parse(value, position)
        return date != null && position.index == value.length
    }
    fun validate(values: Map<String, String>, terms: Boolean) {
        require(terms) { "Please accept the Terms & Conditions." }
        fields.keys.forEach { require(!values[it].isNullOrBlank()) { "Please complete all registration fields." } }
        listOf("birthDate", "licenseExpiration", "issueDate", "validityDate").forEach {
            require(validDate(values.getValue(it))) { "Please select valid dates in your registration." }
        }
        mapOf("name" to 200, "mobile" to 32, "address" to 1000, "experience" to 100,
            "bio" to 2000, "licenseNumber" to 100).forEach { (key, limit) ->
            require(values.getValue(key).length <= limit) { "The $key field is too long (maximum $limit characters)." }
        }
    }
    fun objectPath(uid: String, submission: String, filename: String): String {
        require(Regex("[A-Za-z0-9_-]{1,128}").matches(uid))
        require(Regex("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}").matches(submission))
        require(filename in documents.values)
        return "$uid/$submission/$filename"
    }
    fun application(values: Map<String, String>, uid: String, submission: String): Map<String, Any> =
        fields.map { (draft, cloud) -> cloud to values.getValue(draft) }.toMap() + mapOf(
            "submissionId" to submission,
            "licenseFrontPath" to objectPath(uid, submission, "license-front.jpg"),
            "licenseBackPath" to objectPath(uid, submission, "license-back.jpg"),
            "policeImagePath" to objectPath(uid, submission, "police-clearance.jpg"),
            "profilePhotoPath" to "$uid/profile.jpg", "termsAccepted" to true, "status" to "pending"
        )
}
