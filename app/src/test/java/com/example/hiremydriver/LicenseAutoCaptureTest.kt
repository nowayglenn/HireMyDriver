package com.example.hiremydriver

import org.junit.Assert.*
import org.junit.Test

class LicenseAutoCaptureTest {
    private val frame = LicenseAlignment.Frame(.06f, .3f, .94f, .7f)
    private fun green(): LicenseCaptureGate = LicenseCaptureGate().apply {
        val card = this@LicenseAutoCaptureTest.frame
        update(card, 0); update(card, 250)
        assertTrue(canCapture(250))
    }

    @Test fun shutterRequiresTwoStableFrames() {
        val gate = LicenseCaptureGate()
        assertNull(gate.consume(0))
        gate.update(frame, 0); assertFalse(gate.canCapture(0))
        gate.update(frame, 250); assertTrue(gate.canCapture(250))

        assertFalse(gate.autoReady(250))
    }

    @Test fun automaticCaptureWaitsForStableGreenAndFiresOnlyOnce() {
        val gate = green()
        gate.update(frame, 500); assertFalse(gate.autoReady(500))
        gate.update(frame, 750); assertTrue(gate.autoReady(750))
        assertEquals(frame, gate.consume(750))
        assertNull(gate.consume(751))
        gate.update(frame, 1000); assertFalse(gate.autoReady(1000))
    }

    @Test fun missingOrMovingCardImmediatelyLocksShutter() {
        val missing = green()
        missing.update(null, 500)
        assertNull(missing.consume(500))
        val moved = green()
        moved.update(frame.copy(left = .09f), 500)
        assertFalse(moved.canCapture(500))
    }

    @Test fun staleOrResetAlignmentCannotCapture() {
        val gate = green()
        assertNull(gate.consume(951))
        gate.update(frame, 1001)
        assertFalse(gate.canCapture(1001))
        val nextSide = green()
        nextSide.reset()
        assertNull(nextSide.consume(900))
        nextSide.update(frame, 500)
        assertFalse(nextSide.canCapture(500))
    }

    @Test fun cropUsesDetectedBoundsAndPreservesEdgeMargin() {
        val crop = LicenseCropBounds.pixels(frame, 2000, 3000)
        assertTrue(crop.left < 120 && crop.left >= 100)
        assertTrue(crop.top < 900 && crop.top >= 880)
        assertTrue(crop.left + crop.width > 1880)
        assertTrue(crop.top + crop.height > 2100)
        assertTrue(crop.width < 1800 && crop.height < 1240)
    }

    @Test fun cropStaysInsideImageInEitherOrientation() {
        for ((w, h) in listOf(3000 to 2000, 2000 to 3000)) {
            val crop = LicenseCropBounds.pixels(LicenseAlignment.Frame(0f, 0f, 1f, 1f), w, h)
            assertEquals(LicenseCropBounds.Bounds(0, 0, w, h), crop)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidCropBoundsAreRejected() {
        LicenseCropBounds.pixels(frame.copy(right = 1.1f), 2000, 3000)
    }
}
