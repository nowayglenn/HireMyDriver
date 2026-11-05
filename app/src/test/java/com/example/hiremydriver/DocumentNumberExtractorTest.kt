package com.example.hiremydriver

import org.junit.Assert.*
import org.junit.Test

class DocumentNumberExtractorTest {
    private val license = DocumentNumberExtractor.Kind.LICENSE
    private val police = DocumentNumberExtractor.Kind.POLICE_CLEARANCE

    @Test fun readsLicenseSameLineAndKeepsLeadingZeros() {
        assertEquals("A01-02-000003", DocumentNumberExtractor.extract("Name: Sample Driver\nLicense No.: A01-02-000003\nExpiration Date: 2030-01-01", license))
    }

    @Test fun readsNextLineAndNormalizesOnlyCaseAndHyphenSpacing() {
        assertEquals("A01-02-000003", DocumentNumberExtractor.extract("LICENSE NUMBER\na01 - 02 - 000003", license))
    }

    @Test fun readsPoliceClearanceNumberWithoutGuessingFromReceipt() {
        assertEquals("000123456789", DocumentNumberExtractor.extract("Receipt No: 999999999\nPolice Clearance No. 000123456789", police))
        assertNull(DocumentNumberExtractor.extract("Reference No: 999999999\nContact No: 09123456789", police))
    }

    @Test fun readsExplicitTransactionLabelButNeverUsesAppointmentReferenceOrUnlabelledNumber() {
        assertEquals("001234567890", DocumentNumberExtractor.extract("Transaction Number: 001234567890", police))
        assertEquals("001234567890", DocumentNumberExtractor.extract("Transaction No.\n001234567890", police))
        assertNull(DocumentNumberExtractor.extract("Reference No: 001234567890\nReceipt No: 009876543210", police))
        assertNull(DocumentNumberExtractor.extract("001234567890", police))
    }

    @Test fun rejectsDatesGenericUnlabelledNumbersAndWrongDocumentType() {
        assertNull(DocumentNumberExtractor.extract("License No.\n2030-01-01", license))
        assertNull(DocumentNumberExtractor.extract("000123456789", license))
        assertNull(DocumentNumberExtractor.extract("Clearance No. 000123456789", license))
        assertNull(DocumentNumberExtractor.extract("License No. A01-02-000003", police))
    }

    @Test fun rejectsConflictingReadingsButAllowsRepeatedSameNumber() {
        assertNull(DocumentNumberExtractor.extract("License No. A01-02-000003\nLicense No. A01-02-000004", license))
        assertEquals("A01-02-000003", DocumentNumberExtractor.extract("License No. A01-02-000003\nLicense No. A01-02-000003", license))
    }

    @Test fun leavesUnclearCharactersForManualReviewButReadsReorderedColumns() {
        assertNull(DocumentNumberExtractor.extract("License No. A0?-02-000003", license))
        assertEquals("A01-02-000003", DocumentNumberExtractor.extract("License No. Expiration Date\nA01-02-000003 2030-01-01", license))
        assertEquals("A01-02-000003", DocumentNumberExtractor.extract("License No.\nName Sample Driver\nA01-02-000003", license))
    }

    @Test fun readsStrictLicenseFormatWithMissingLabelOrSeparators() {
        listOf("A01-02-000003", "A01 02 000003", "A0102000003", "A01–02–000003").forEach {
            assertEquals("A01-02-000003", DocumentNumberExtractor.extract(it, license))
        }
        assertNull(DocumentNumberExtractor.extract("A01020000034", license))
        assertNull(DocumentNumberExtractor.extract("A0I-02-000003", license))
    }

    @Test fun excludesReferenceNumbersAndReportsConflicts() {
        assertNull(DocumentNumberExtractor.extract("Reference No: A01-02-000003", license))
        assertNull(DocumentNumberExtractor.extract("Receipt No:\nA01-02-000003", license))
        val result = DocumentNumberExtractor.analyze(listOf(
            DocumentNumberExtractor.Line("A01-02-000003"), DocumentNumberExtractor.Line("B02-03-000004")), license)
        assertNull(result.number)
        assertTrue(result.ambiguous)
    }

    @Test fun recognizesPoliceNumberInSeparateBlockByPosition() {
        val result = DocumentNumberExtractor.analyze(listOf(
            line("NPCS No.", 20, 50, 140, 70),
            line("999999999", 500, 80, 620, 100), // Receipt column, emitted first by OCR.
            line("000123456789", 20, 80, 180, 100)), police)
        assertEquals("000123456789", result.number)
    }

    @Test fun recognizesNumberToRightOfLabelRegardlessOfBlockOrder() {
        assertEquals("000123456789", DocumentNumberExtractor.analyze(listOf(
            line("000123456789", 170, 50, 310, 70),
            line("Police Clearance No.", 10, 50, 160, 70)), police).number)
    }

    @Test fun positionedCandidatesMustBeNearLabelAndRemainUnambiguous() {
        assertNull(DocumentNumberExtractor.analyze(listOf(
            line("Clearance No.", 20, 50, 140, 70),
            line("000123456789", 20, 300, 180, 320)), police).number)
        assertTrue(DocumentNumberExtractor.analyze(listOf(
            line("Clearance No.", 20, 50, 140, 70),
            line("000123456789", 20, 80, 180, 100),
            line("000987654321", 20, 105, 180, 125)), police).ambiguous)
    }

    @Test fun joinedColumnsDoNotAppendExpiryToNumber() {
        assertEquals("A01-02-000003", DocumentNumberExtractor.extract("License No. A01-02-000003 Expiration Date 2030/01/01", license))
        assertEquals("000123456789", DocumentNumberExtractor.extract("Clearance No. 000123456789 Date Issued 2026/01/01", police))
    }

    private fun line(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        DocumentNumberExtractor.Line(text, DocumentNumberExtractor.Box(left, top, right, bottom))

    @Test fun neverInventsDigitsFromLetters() {
        assertEquals("A01-O2-000003", DocumentNumberExtractor.extract("License No. A01-O2-000003", license))
    }

    @Test fun fillsOnlyUntouchedEmptyField() {
        val guard = DocumentScanGuard()
        val ticket = guard.start("front", "license")
        assertTrue(guard.canFill(ticket, ""))
        assertFalse(guard.canFill(ticket, "manually entered"))
        guard.edited("license")
        assertFalse(guard.canFill(ticket, "")) // User typed and then cleared it.
    }

    @Test fun replacementAndNavigationDiscardOldResults() {
        val guard = DocumentScanGuard()
        val old = guard.start("front", "license")
        val fresh = guard.start("front", "license")
        assertFalse(guard.isCurrent(old))
        assertTrue(guard.canFill(fresh, ""))
        guard.invalidate()
        assertFalse(guard.isCurrent(fresh))
    }

    @Test fun frontBackShareFieldProtectionButPoliceIsIndependent() {
        val guard = DocumentScanGuard()
        val front = guard.start("front", "license")
        val back = guard.start("back", "license")
        val clearance = guard.start("police", "clearance")
        assertTrue(guard.canFill(front, ""))
        guard.edited("license") // Front filled the field while back was scanning.
        assertFalse(guard.canFill(back, ""))
        assertTrue(guard.canFill(clearance, ""))
    }
}
