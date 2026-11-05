package com.example.hiremydriver

enum class LocationPermissionState {
    PRECISE, APPROXIMATE, DENIED;

    companion object {
        fun from(precise: Boolean, approximate: Boolean) = when {
            precise -> PRECISE
            approximate -> APPROXIMATE
            else -> DENIED
        }
    }
}
