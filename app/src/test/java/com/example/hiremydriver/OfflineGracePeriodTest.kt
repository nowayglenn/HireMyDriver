package com.example.hiremydriver

import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineGracePeriodTest {
    @Test fun repeatedOfflineCallbacksDoNotPostponeDialog() {
        val grace = OfflineGracePeriod()
        assertEquals(8_000L, grace.remaining(false, 1_000L))
        assertEquals(5_000L, grace.remaining(false, 4_000L))
        assertEquals(0L, grace.remaining(false, 9_000L))
        assertEquals(0L, grace.remaining(false, 12_000L))
    }

    @Test fun recoveryStartsFreshGraceForNextOutage() {
        val grace = OfflineGracePeriod()
        grace.remaining(false, 1_000L)
        assertEquals(0L, grace.remaining(true, 3_000L))
        assertEquals(8_000L, grace.remaining(false, 4_000L))
    }
}
