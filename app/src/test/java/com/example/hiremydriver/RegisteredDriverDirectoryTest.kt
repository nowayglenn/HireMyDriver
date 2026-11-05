package com.example.hiremydriver

import org.junit.Assert.*
import org.junit.Test

class RegisteredDriverDirectoryTest {
    @Test fun publicProjectionExcludesDocumentsContactAndBirthDate() {
        val input = mapOf<String, Any>("fullName" to "Registered Driver", "experience" to "2",
            "bio" to "Careful driver", "status" to "pending", "email" to "private@example.com",
            "phoneNumber" to "09123456789", "address" to "Private home", "birthDate" to "1/1/1990",
            "licenseNumber" to "private", "policeImagePath" to "private/clearance.jpg")
        val result = RegisteredDriverDirectory.displayFields(input)
        assertEquals(setOf("fullName", "experience", "bio", "status", "address"), result.keys)
        assertEquals("pending", result["status"])
        assertEquals("Private home",result["address"])
        assertFalse(result.containsKey("rating"))
        assertFalse(result.containsKey("hourlyRate"))
    }

    @Test fun oversizedThumbnailsAreNotPublished() {
        assertFalse(RegisteredDriverDirectory.displayFields(emptyMap(), "x".repeat(60001)).containsKey("thumbnail"))
    }

    @Test fun pendingIsNeverDisplayedAsVerified() {
        assertEquals("Pending verification", RegisteredDriver("uid", "Name", "1", "", "pending", "").statusLabel)
        assertEquals("Verified", RegisteredDriver("uid", "Name", "1", "", "approved", "").statusLabel)
    }
}
