package com.example.hiremydriver

import org.junit.Assert.*
import org.junit.Test

class DriverRegistrationContractTest {
    private val submission = "a4c13015-2a21-388b-a083-558a661912c2"
    private fun values() = mapOf(
        "name" to "Test Driver", "email" to "driver@example.com", "mobile" to "09123456789",
        "birthDate" to "12/24/1991", "address" to "Test address", "experience" to "2",
        "bio" to "Test driver bio", "licenseNumber" to "TEST-LICENSE", "licenseExpiration" to "1/1/2030",
        "issueDate" to "5/9/2025", "validityDate" to "11/15/2025"
    )
    @Test fun payloadKeepsManualDatesAndExcludesSecrets() {
        val input = values() + mapOf("password" to "secret", "confirmPassword" to "secret", "policeImage" to "file:///private.jpg")
        DriverRegistrationContract.validate(input, true)
        val data = DriverRegistrationContract.application(input, "driver_uid", submission)
        assertEquals("5/9/2025", data["issueDate"])
        assertEquals("11/15/2025", data["validityDate"])
        assertEquals("pending", data["status"])
        assertEquals("driver_uid/$submission/license-front.jpg", data["licenseFrontPath"])
        assertEquals("driver_uid/$submission/police-clearance.jpg", data["policeImagePath"])
        assertFalse(data.containsKey("password"))
        assertFalse(data.containsKey("confirmPassword"))
        assertFalse(data.containsKey("policeImage"))
        assertEquals(18, data.size) // createdAt is added as a server timestamp at commit.
    }
    @Test fun missingOrImpossibleDatesNeverAcquireDefaults() {
        listOf("", "Select validity date", "2/30/2026", "13/1/2026", "12/24/2025extra").forEach {
            assertFalse(DriverRegistrationContract.validDate(it))
            assertTrue(runCatching { DriverRegistrationContract.validate(values() + ("validityDate" to it), true) }.isFailure)
        }
        assertTrue(DriverRegistrationContract.validDate("2/29/2024"))
        assertFalse(DriverRegistrationContract.validDate("2/29/2025"))
    }
    @Test fun termsAreRequired() {
        assertTrue(runCatching { DriverRegistrationContract.validate(values(), false) }.isFailure)
    }
    @Test fun documentPathsCannotEscapeTheirAccountOrUseArbitraryNames() {
        listOf("../other", "user/another", "").forEach { uid ->
            assertTrue(runCatching { DriverRegistrationContract.objectPath(uid, submission, "license-front.jpg") }.isFailure)
        }
        assertTrue(runCatching { DriverRegistrationContract.objectPath("user", "../", "license-front.jpg") }.isFailure)
        assertTrue(runCatching { DriverRegistrationContract.objectPath("user", submission, "../../private.jpg") }.isFailure)
    }
}
