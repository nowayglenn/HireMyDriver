package com.example.hiremydriver

import androidx.core.graphics.Insets
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenInsetsTest {
    @Test fun statusBarAndLandscapeCutoutAreReservedWithoutLosingOriginalPadding() {
        val actual = ScreenInsets.padding(Insets.of(8, 12, 8, 16), Insets.of(48, 32, 0, 24), 0)
        assertEquals(Insets.of(56, 44, 8, 40), actual)
    }

    @Test fun keyboardReplacesTheNavigationInsetInsteadOfAddingItTwice() {
        val original = Insets.of(0, 0, 0, 8)
        val bars = Insets.of(0, 36, 0, 24)
        assertEquals(Insets.of(0, 36, 0, 308), ScreenInsets.padding(original, bars, 300))
        assertEquals(Insets.of(0, 36, 0, 32), ScreenInsets.padding(original, bars, 0))
    }

    @Test fun repeatedInsetsAndRotationNeverAccumulateOldSpacing() {
        val original = Insets.of(4, 8, 4, 8)
        val portrait = Insets.of(0, 44, 0, 24)
        repeat(5) { assertEquals(Insets.of(4, 52, 4, 32), ScreenInsets.padding(original, portrait, 0)) }
        assertEquals(Insets.of(48, 8, 28, 8), ScreenInsets.padding(original, Insets.of(44, 0, 24, 0), 0))
        assertEquals(Insets.of(4, 8, 4, 8), original)
    }
}
