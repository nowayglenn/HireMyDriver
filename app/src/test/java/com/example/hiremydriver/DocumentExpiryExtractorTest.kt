package com.example.hiremydriver

import org.junit.Assert.*
import org.junit.Test

class DocumentExpiryExtractorTest {
    @Test fun fillsYearFirstInExistingDateFieldFormat() {
        assertEquals("4/3/2030", DocumentExpiryExtractor.extract("Expiration Date: 2030/04/03").date?.forField())
        assertEquals("4/3/2030", DocumentExpiryExtractor.extract("Expiry Date\n2030 - 04 - 03").date?.forField())
    }

    @Test fun acceptsNamedMonthsAndUnambiguousYearLastDates() {
        listOf("31 December 2030", "December 31, 2030", "31/12/2030", "12/31/2030").forEach {
            assertEquals("12/31/2030", DocumentExpiryExtractor.extract("Expiration Date: $it").date?.forField())
        }
    }

    @Test fun neverGuessesAmbiguousDayAndMonth() {
        val result = DocumentExpiryExtractor.extract("Expiration Date: 03/04/2030")
        assertNull(result.date)
        assertTrue(result.ambiguous)
        assertEquals("4/4/2030", DocumentExpiryExtractor.extract("Expiration Date: 04/04/2030").date?.forField())
    }

    @Test fun validatesCalendarDatesIncludingLeapYears() {
        assertNull(DocumentExpiryExtractor.extract("Expiration Date: 2030/02/29").date)
        assertNull(DocumentExpiryExtractor.extract("Expiration Date: 2030/04/31").date)
        assertNull(DocumentExpiryExtractor.extract("Expiration Date: 2030/00/12").date)
        assertEquals("2/29/2032", DocumentExpiryExtractor.extract("Expiration Date: 2032/02/29").date?.forField())
    }

    @Test fun doesNotPickBirthIssueOrUnlabelledDates() {
        assertNull(DocumentExpiryExtractor.extract("Date of Birth: 1990/04/03\nIssue Date: 2020/04/03\n2030/04/03").date)
        assertNull(DocumentExpiryExtractor.extract("Expiration Date\nDate of Birth: 1990/04/03").date)
    }

    @Test fun ignoresDatesAfterAnotherFieldLabel() {
        assertEquals("4/3/2030", DocumentExpiryExtractor.extract("Expiration Date: 2030/04/03 Date of Birth: 1990/04/03").date?.forField())
    }

    @Test fun spatialLookupUsesExpiryColumnEvenWithShuffledBlocks() {
        val lines = listOf(
            line("1990/04/03", 10, 80, 110, 100),
            line("Expiration Date", 250, 50, 370, 70),
            line("2030/04/03", 250, 80, 350, 100),
            line("Date of Birth", 10, 50, 110, 70))
        assertEquals("4/3/2030", DocumentExpiryExtractor.analyze(lines).date?.forField())
    }

    @Test fun supportsDateToRightOfLabel() {
        assertEquals("4/3/2030", DocumentExpiryExtractor.analyze(listOf(
            line("2030/04/03", 155, 50, 260, 70),
            line("Expiration Date", 10, 50, 145, 70))).date?.forField())
    }

    @Test fun separateWordSpansHandleMergedHeaderAndValueRows() {
        val lines = listOf(
            line("Date of Birth Expiration Date", 10, 50, 370, 70),
            line("1990/04/03 2030/04/03", 10, 80, 350, 100),
            line("Expiration Date", 250, 50, 370, 70),
            line("1990/04/03", 10, 80, 110, 100),
            line("2030/04/03", 250, 80, 350, 100))
        assertEquals("4/3/2030", DocumentExpiryExtractor.analyze(lines).date?.forField())
    }

    @Test fun conflictingDatesRequireManualSelectionAndDuplicatesDoNot() {
        val result = DocumentExpiryExtractor.extract("Expiration Date: 2030/04/03\nExpiry: 2031/04/03")
        assertTrue(result.ambiguous)
        assertNull(result.date)
        assertEquals("4/3/2030", DocumentExpiryExtractor.extract("Expiration Date: 2030/04/03\nExpiry: 2030/04/03").date?.forField())
    }

    @Test fun expiredDatesAreTranscribedWithoutChangingTheYear() {
        assertEquals("4/3/2020", DocumentExpiryExtractor.extract("Expiration Date: 2020/04/03").date?.forField())
    }

    @Test fun numberAndDateHaveIndependentEditProtectionAndSharedImageGeneration() {
        val guard = DocumentScanGuard()
        val number = guard.start("front", "licenseNumber")
        val expiry = guard.forField(number, "licenseExpiration")
        guard.edited("licenseNumber")
        assertTrue(guard.canFill(expiry, ""))
        assertFalse(guard.canFill(expiry, "4/3/2030"))
        guard.edited("licenseExpiration")
        assertFalse(guard.canFill(expiry, ""))
        val replacement = guard.start("front", "licenseNumber")
        assertFalse(guard.isCurrent(expiry))
        assertTrue(guard.canFill(guard.forField(replacement, "licenseExpiration"), ""))
    }

    private fun line(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        DocumentNumberExtractor.Line(text, DocumentNumberExtractor.Box(left, top, right, bottom))
}
