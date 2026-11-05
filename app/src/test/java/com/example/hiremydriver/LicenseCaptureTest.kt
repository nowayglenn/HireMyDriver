package com.example.hiremydriver

import org.junit.Assert.*
import org.junit.Test

class LicenseCaptureTest {
    @Test fun frontMovesToBackBeforePairIsComplete() {
        val session = LicenseCaptureSession()
        assertEquals("licenseFront", session.side)
        session.confirm("front.jpg")
        assertEquals("licenseBack", session.side)
        assertFalse(session.complete)
        session.confirm("back.jpg")
        assertTrue(session.complete)
        assertEquals(mapOf("licenseFront" to "front.jpg", "licenseBack" to "back.jpg"), session.photos)
    }

    @Test fun retakeKeepsOtherSideAndOldPhotoUntilConfirmation() {
        val session = LicenseCaptureSession()
        session.confirm("front.jpg"); session.confirm("back.jpg")
        session.retake("licenseFront")
        assertEquals("front.jpg", session.photos["licenseFront"])
        assertEquals("front.jpg", session.confirm("new-front.jpg"))
        assertEquals("back.jpg", session.photos["licenseBack"])
        assertTrue(session.complete)
    }

    @Test fun singleSideRetakeDoesNotAskForOtherSide() {
        val session = LicenseCaptureSession("licenseBack")
        session.confirm("back.jpg")
        assertTrue(session.complete)
        assertFalse(session.photos.containsKey("licenseFront"))
    }

    @Test fun restorationDuringBackCaptureRetainsConfirmedFront() {
        val session = LicenseCaptureSession()
        session.photos["licenseFront"] = "front.jpg"
        session.retake("licenseBack")
        session.confirm("back.jpg")
        assertTrue(session.complete)
    }

    @Test fun guideMaintainsCardAspectInPortraitAndLandscape() {
        listOf(320 to 480, 640 to 220).forEach { (w, h) ->
            val f = LicenseAlignment.frame(w, h)
            assertEquals(1.586f, f.width / f.height, .001f)
            assertTrue(f.left > 0 && f.top > 0 && f.right < w && f.bottom < h)
        }
    }

    @Test fun alignedCardHasFourEdgesButBlankAndPartialViewsDoNot() {
        assertTrue(LicenseAlignment.matches(card(), 320, 480))
        assertFalse(LicenseAlignment.matches(IntArray(320 * 480) { -1 }, 320, 480))
        val partial = card()
        for (y in 0 until 480) for (x in 0 until 45) partial[y * 320 + x] = -1
        assertFalse(LicenseAlignment.matches(partial, 320, 480))
    }

    @Test fun smallerAndOffCenterWholeCardsAreDetected() {
        assertTrue(LicenseAlignment.matches(card(scale = .65f), 320, 480))
        assertTrue(LicenseAlignment.matches(card(scale = .7f, offset = 35f), 320, 480))
    }

    @Test fun tinyPartialAndHeavilyRotatedCardsRemainRed() {
        assertFalse(LicenseAlignment.matches(card(scale = .35f), 320, 480))
        assertFalse(LicenseAlignment.matches(card(offset = 45f), 320, 480))
        assertFalse(LicenseAlignment.matches(card(angle = .3), 320, 480))
    }

    @Test fun smallerCardCropIncludesItsEdgesWithoutFixedGuideBackground() {
        val found = LicenseAlignment.detect(card(scale = .65f, offset = 30f), 320, 480)!!
        val width = LicenseAlignment.frame(320, 480).width * .65f
        assertTrue(found.width * 320 >= width)
        assertTrue(found.width * 320 < width + 8f)
        assertTrue(found.left * 320 <= 190f - width / 2 + 1f)
        assertTrue(found.right * 320 >= 190f + width / 2 - 1f)
    }

    @Test fun slightHandTiltCanBeDetectedAndCropKeepsAllCorners() {
        val angle = Math.toRadians(5.0)
        val found = LicenseAlignment.detect(card(scale = .7f, angle = angle), 320, 480)!!
        val f = LicenseAlignment.frame(320, 480)
        val halfWidth = .35f * (f.width * kotlin.math.cos(angle) + f.height * kotlin.math.sin(angle))
        val bounds = LicenseCropBounds.pixels(found, 320, 480)
        assertTrue(bounds.left <= 160 - halfWidth + 2)
        assertTrue(bounds.left + bounds.width >= 160 + halfWidth - 2)
    }

    @Test fun detectedCropTracksCardEdgesInsteadOfUsingFixedGuide() {
        val detected = LicenseAlignment.detect(card(offset = 8f), 320, 480)!!
        val guide = LicenseAlignment.frame(320, 480)
        assertEquals(guide.left + 8f, detected.left * 320, 5f)
        assertEquals(guide.right + 8f, detected.right * 320, 5f)
        assertEquals(guide.top, detected.top * 480, 5f)
        assertEquals(guide.bottom, detected.bottom * 480, 5f)
    }

    @Test fun textLikeStripesDoNotCountAsCardEdges() {
        val pixels = IntArray(320 * 480) { i -> if ((i / 320) % 14 < 3) -1 else 0xff202020.toInt() }
        assertFalse(LicenseAlignment.matches(pixels, 320, 480))
    }

    @Test fun alignmentMustStabilizeAndResetsForNextSide() {
        val state = LicenseAlignmentStability()
        assertFalse(state.update(true)); assertFalse(state.update(true)); assertTrue(state.update(true))
        assertTrue(state.update(false)); assertFalse(state.update(false))
        repeat(3) { state.update(true) }
        state.reset()
        assertFalse(state.aligned)
        assertFalse(state.update(true))
    }

    private fun card(scale: Float = 1f, offset: Float = 0f, angle: Double = 0.0): IntArray {
        val f = LicenseAlignment.frame(320, 480)
        return IntArray(320 * 480) { i ->
            val x = i % 320 - 160f - offset; val y = i / 320 - 240f
            val localX = x * kotlin.math.cos(angle) + y * kotlin.math.sin(angle)
            val localY = -x * kotlin.math.sin(angle) + y * kotlin.math.cos(angle)
            if (kotlin.math.abs(localX) <= f.width * scale / 2 && kotlin.math.abs(localY) <= f.height * scale / 2)
                0xffeeeeee.toInt() else 0xff202020.toInt()
        }
    }
}
