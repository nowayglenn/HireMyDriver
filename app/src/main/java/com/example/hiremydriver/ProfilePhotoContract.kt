package com.example.hiremydriver

internal object ProfilePhotoContract {
    fun objectPath(uid: String): String {
        require(uid.matches(Regex("[A-Za-z0-9_-]{1,128}"))) { "Invalid account ID" }
        return "$uid/profile.jpg"
    }

    fun isMissingPhoto(status: Int, code: String, message: String): Boolean =
        status in setOf(400, 404) &&
            (code == "NoSuchKey" || (code.isEmpty() && message.equals("Object not found", true)))
}
