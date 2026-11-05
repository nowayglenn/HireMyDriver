package com.example.hiremydriver

import com.example.hiremydriver.trip.*
import org.junit.Assert.assertEquals
import org.junit.Test

class BookingSnapshotTest {
    private val trip = TripSession("booking", "Driver", "Customer", "Pickup", "Destination")

    @Test fun notificationServerResultSurvivesAnOlderListenerCache() {
        val completed = trip.copy(phase = TripPhase.COMPLETED)
        val result = bookingSnapshot(completed, trip.copy(phase = TripPhase.ACCEPTED), true)
        assertEquals(TripPhase.COMPLETED, result.phase)
        assertEquals(BookingDetailPage.DRIVER_BOOKING, result.detailPage(true))
    }

    @Test fun cancelledNotificationCannotReopenAnActiveBookingFromDisk() {
        val cancelled = trip.copy(phase = TripPhase.CANCELLED)
        val result = bookingSnapshot(cancelled, trip.copy(phase = TripPhase.IN_PROGRESS), true)
        assertEquals(TripPhase.CANCELLED, result.phase)
        assertEquals(BookingDetailPage.DRIVER_REQUEST, result.detailPage(true))
    }

    @Test fun authoritativeServerUpdatesStillReplaceLocalBookings() {
        for (phase in TripPhase.entries) {
            val incoming = trip.copy(phase = phase)
            assertEquals(incoming, bookingSnapshot(trip, incoming, false))
        }
    }

    @Test fun firstOfflineLoadUsesTheAvailableDiskSnapshot() {
        assertEquals(trip, bookingSnapshot(null, trip, true))
    }
}
