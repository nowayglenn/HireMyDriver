package com.example.hiremydriver

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test fun recognizesSupportedLanguagesAndFilipinoDeviceLocales() {
        mapOf("en-US" to "en", "tl" to "tl", "fil-PH" to "tl", "tl_PH" to "tl",
            "ilo" to "ilo", "ILO-PH" to "ilo").forEach { (input, expected) ->
            assertEquals(expected, AppLanguage.normalize(input))
        }
    }

    @Test fun unsupportedOrMissingLanguagesUseEnglish() {
        listOf(null, "", "ar", "ja").forEach { assertEquals("en", AppLanguage.normalize(it)) }
    }
}
