package com.example.hiremydriver

import org.junit.Assert.*
import org.junit.Test

class PhilippineMobileTest {
    @Test fun normalizesSupportedFormats() {
        listOf("0917 123 4567", "+63 917 123 4567", "639171234567", "0917-123-4567").forEach {
            assertEquals("+639171234567", PhilippineMobile.normalize(it))
        }
    }
    @Test fun rejectsOtherCountriesLandlinesAndIncompleteNumbers() {
        listOf("", "+19171234567", "0212345678", "0917123456", "091712345678", "0917abc4567", "09171234567;09171234568").forEach {
            assertNull(PhilippineMobile.normalize(it))
        }
    }
}
