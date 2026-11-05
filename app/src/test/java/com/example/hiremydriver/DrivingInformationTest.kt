package com.example.hiremydriver

import org.junit.Assert.*
import org.junit.Test

class DrivingInformationTest {
    private val data = mapOf("vehicles" to listOf("SUV"), "transmissions" to listOf("Manual"),
        "experience" to "6", "languages" to listOf("English"), "areas" to listOf("Bohol"), "rate" to 500L)
    @Test fun eachRequiredFieldMustBeSaved() {
        assertTrue(DrivingInformation.complete(data))
        data.keys.forEach { assertFalse(it, DrivingInformation.complete(data - it)) }
    }
    @Test fun permitsManualAutomaticOrBothAndRejectsInvalidValues() {
        for (types in listOf(listOf("Manual"), listOf("Automatic"), listOf("Manual", "Automatic")))
            assertTrue(DrivingInformation.complete(data + ("transmissions" to types)))
        for (years in listOf("", "-1", "81", "six", "5.5")) assertFalse(DrivingInformation.complete(data + ("experience" to years)))
        for (rate in listOf(-1, 100001, 1.5)) assertFalse(DrivingInformation.complete(data + ("rate" to rate)))
        assertFalse(DrivingInformation.complete(data + ("areas" to listOf("Select service areas"))))
        assertFalse(DrivingInformation.complete(data + ("vehicles" to emptyList<String>())))
        assertFalse(DrivingInformation.complete(data + ("transmissions" to listOf("Other"))))
        assertFalse(DrivingInformation.complete(data + ("languages" to emptyList<String>())))
        assertFalse(DrivingInformation.complete(data + ("languages" to listOf("Other"))))
        assertTrue(DrivingInformation.complete(data + ("languages" to DrivingInformation.languages)))
        assertTrue(DrivingInformation.complete(data + mapOf("experience" to "0", "rate" to 0)))
    }
}
